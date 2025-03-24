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
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.ui.planupgrade.usecase.CycleInfo
import com.protonvpn.android.ui.planupgrade.usecase.WaitForSubscription
import com.protonvpn.android.utils.UserPlanManager
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
import me.proton.core.payment.domain.repository.BillingClientError
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
    private val waitForSubscription: WaitForSubscription
) : ViewModel() {

    data class PriceInfo(
        val formattedPrice: String,
        val formattedRenewPrice: String? = null,
        val savePercent: Int? = null,
        val formattedPerMonthPrice: String? = null,
    )
    sealed class State {
        object Initializing : State()
        object UpgradeDisabled : State()
        object LoadingPlans : State()
        class LoadError(
            @StringRes val messageRes: Int? = null,
            val error: Throwable? = null
        ) : State()
        data class PurchaseReady(
            val allPlans: List<PlanModel>,
            val selectedPlan: PlanModel,
            val selectedPlanPriceInfo: Map<PlanCycle, PriceInfo>,
            val showRenewPrice: Boolean,
            val inProgress: Boolean = false,
        ) : State()
        object PlansFallback : State() // Conditions for short flow were not met, start normal account flow
        data class PurchaseSuccess(
            val newPlanName: String,
            val upgradeFlowType: UpgradeFlowType
        ) : State()
    }

    data class PurchaseError(val billingClientError: BillingClientError?)

    protected val purchaseError = Channel<PurchaseError>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val eventPurchaseError: ReceiveChannel<PurchaseError> = purchaseError
    val state = MutableStateFlow<State>(State.Initializing)

    fun reportUpgradeFlowStart(upgradeSource: UpgradeSource) {
        upgradeTelemetry.onUpgradeFlowStarted(upgradeSource)
    }

    fun setupOrchestrators(activity: ComponentActivity) {
        authOrchestrator.register(activity)
        plansOrchestrator.register(activity)

        plansOrchestrator.onUpgradeResult { result ->
            viewModelScope.launch {
                state.update { current ->
                    if (result != null && result.billingResult.paySuccess) {
                        onPaymentFinished(result.planId, UpgradeFlowType.REGULAR)
                        State.PurchaseSuccess(
                            newPlanName = result.planId,
                            upgradeFlowType = UpgradeFlowType.REGULAR
                        )
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

    suspend fun onPaymentFinished(newPlanName: String, upgradeFlowType: UpgradeFlowType) {
        upgradeTelemetry.onUpgradeSuccess(newPlanName, upgradeFlowType)
        waitForSubscription(newPlanName, userId.first())
        userPlanManager.refreshVpnInfo()
    }

    fun onStartFallbackUpgrade() = viewModelScope.launch {
        userId.first()?.let { userId ->
            onPaymentStarted(UpgradeFlowType.REGULAR)
            plansOrchestrator.startUpgradeWorkflow(userId)
        }
    }
}
