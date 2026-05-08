/*
 * Copyright (c) 2021 Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.ui.planupgrade

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.mmp.events.MmpEventType
import com.protonvpn.android.mmp.events.toMmpSubscriptionDetails
import com.protonvpn.android.mmp.events.usecases.SaveMmpEvent
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.telemetry.UpgradeAbTest
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.telemetry.UpgradeTrigger
import com.protonvpn.android.ui.planupgrade.usecase.CycleInfo
import com.protonvpn.android.ui.planupgrade.usecase.WaitForSubscription
import com.protonvpn.android.utils.UserPlanManager
import io.sentry.Sentry
import io.sentry.SentryEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.ApiException
import me.proton.core.network.domain.ApiResult
import me.proton.core.plan.presentation.PlansOrchestrator
import me.proton.core.plan.presentation.entity.PlanCycle
import me.proton.core.plan.presentation.onUpgradeResult

open class PlanModel(
    val displayName: String,
    val planName: String,
    val cycles: List<CycleInfo>
)

abstract class CommonUpgradeDialogViewModel(
    protected val userId: Flow<UserId?>,
    private val authOrchestrator: AuthOrchestrator,
    private val plansOrchestrator: PlansOrchestrator,
    protected val isInAppUpgradeAllowed: suspend () -> Boolean,
    private val upgradeTelemetry: UpgradeTelemetry,
    private val userPlanManager: UserPlanManager,
    private val waitForSubscription: WaitForSubscription,
    private val saveMmpEvent: SaveMmpEvent,
) : ViewModel() {

    data class PriceInfo(
        val formattedPrice: String,
        val formattedRenewPrice: String = formattedPrice,
        val hasIntroPrice: Boolean,
        val savePercent: Int? = null,
        val formattedPerMonthPrice: String? = null,
    )
    data class CycleViewInfo(
        val cycle: PlanCycle,
        @StringRes val perCycleResId: Int?,
        @StringRes val cycleLabelResId: Int,
        val priceInfo: PriceInfo,
    )
    sealed class State {
        object Initializing : State()
        object UpgradeDisabled : State()
        data class LoadingPlans(
            val expectedCycleCount: Int,
            val buttonLabelOverride: String?,
        ) : State()
        object LoadError : State() // Error messages are emitted via onError.
        data class PurchaseReady(
            val allPlans: List<PlanModel>,
            val selectedPlan: PlanModel,
            val selectedPlanCycles: List<CycleViewInfo>,
            val inProgress: Boolean = false,
            val buttonLabelOverride: String? = null,
        ) : State()
        object PlansFallback : State() // Conditions for short flow were not met, start normal account flow
        data class PurchaseSuccess(
            val newPlanName: String,
            val upgradeFlowType: UpgradeFlowType,
            val billingCycle: Int,
        ) : State()
    }

    data class Error(val messageRes: Int?, val message: String?, val throwable: Throwable?)

    private val errorMessage = Channel<Error>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val eventErrorMessage: ReceiveChannel<Error> = errorMessage
    val state = MutableStateFlow<State>(State.Initializing)

    fun reportUpgradeFlowStart(
        upgradeSource: UpgradeSource,
        upgradeTrigger: UpgradeTrigger,
        abTestGroup: UpgradeAbTest?,
        countryId: CountryId? = null,
        reference: String? = null,
    ) {
        upgradeTelemetry.onUpgradeFlowStarted(upgradeSource, upgradeTrigger, abTestGroup, countryId, reference)
    }

    fun setupOrchestrators(activity: ComponentActivity) {
        authOrchestrator.register(activity)
        plansOrchestrator.register(activity)

        plansOrchestrator.onUpgradeResult { result ->
            viewModelScope.launch {
                state.update { current ->
                    if (result != null && result.billingResult.paySuccess) {
                        State.PurchaseSuccess(
                            newPlanName = result.planId,
                            upgradeFlowType = UpgradeFlowType.REGULAR,
                            billingCycle = result.billingResult.cycle.value,
                        ).also { purchaseSuccessState ->
                            onPaymentFinished(purchaseSuccessState = purchaseSuccessState)
                        }
                    } else if (current is State.PurchaseReady) {
                        current.copy(inProgress = false)
                    } else {
                        current // This should always be PlansFallback
                    }
                }
            }
        }
    }

    fun onPaymentStarted(upgradeFlowType: UpgradeFlowType) {
        state.update { if (it is State.PurchaseReady) it.copy(inProgress = true) else it }
        upgradeTelemetry.onUpgradeAttempt(upgradeFlowType)
    }

    suspend fun onPaymentFinished(purchaseSuccessState: State.PurchaseSuccess) {
        with(purchaseSuccessState) {
            upgradeTelemetry.onUpgradeSuccess(newPlanName, upgradeFlowType, billingCycle)
            waitForSubscription(newPlanName, userId.first())?.also { purchase ->
                saveMmpEvent(eventType = MmpEventType.Subscription(subscriptionDetails = purchase.toMmpSubscriptionDetails()))
            }
            userPlanManager.refreshVpnInfo()
        }
    }

    fun onStartFallbackUpgrade() = viewModelScope.launch {
        userId.first()?.let { userId ->
            onPaymentStarted(UpgradeFlowType.REGULAR)
            plansOrchestrator.startUpgradeWorkflow(userId)
        }
    }

    fun onError(messageRes: Int? = null, message: String? = null, error: Throwable? = null) {
        if (shouldReportToSentry(error))
            logToSentry(message ?: error?.message, error) // Remove this once we know payments are in a good shape.
        errorMessage.trySend(Error(messageRes, message, error))
    }

    private fun shouldReportToSentry(throwable: Throwable?): Boolean =
        throwable == null || (throwable as? ApiException)?.error !is ApiResult.Error.Connection

    private fun logToSentry(errorMessage: String?, throwable: Throwable?) {
        Sentry.captureEvent(SentryEvent(OneClickPaymentError(errorMessage, throwable)))
    }
}

private class OneClickPaymentError(message: String?, cause: Throwable?) : Throwable(message, cause)
