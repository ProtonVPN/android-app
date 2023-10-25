/*
 * Copyright (c) 2021 Proton Technologies AG
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.ui.planupgrade.usecase.CycleInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.domain.entity.UserId
import me.proton.core.plan.presentation.PlansOrchestrator
import me.proton.core.plan.presentation.entity.PlanCycle
import me.proton.core.plan.presentation.onUpgradeResult

interface PlanModel {
    val name: String
    val cycles: List<CycleInfo>
}

abstract class CommonUpgradeDialogViewModel(
    protected val userId: Flow<UserId?>,
    private val authOrchestrator: AuthOrchestrator,
    private val plansOrchestrator: PlansOrchestrator,
    protected val isInAppUpgradeAllowed: () -> Boolean,
    private val upgradeTelemetry: UpgradeTelemetry
) : ViewModel() {

    data class PriceInfo(
        val formattedPrice: String,
        val savePercent: Int? = null,
        val formattedPerMonthPrice: String? = null,
    )
    sealed class State {
        object Initializing : State()
        object UpgradeDisabled : State()
        object LoadingPlans : State()
        class LoadError(val error: Throwable) : State()
        open class PlanLoaded(open val plan: PlanModel) : State()
        data class PurchaseReady(
            override val plan: PlanModel,
            val priceInfo: Map<PlanCycle, PriceInfo>,
            val inProgress: Boolean = false,
        ) : PlanLoaded(plan)
        object PlansFallback : State() // Conditions for short flow were not met, start normal account flow
        data class PurchaseSuccess(
            val newPlanName: String,
            val newPlanDisplayName: String
        ) : State()
    }
    val state = MutableStateFlow<State>(State.Initializing)

    fun reportUpgradeFlowStart(upgradeSource: UpgradeSource) {
        upgradeTelemetry.onUpgradeFlowStarted(upgradeSource)
    }

    fun setupOrchestrators(activity: ComponentActivity) {
        authOrchestrator.register(activity)
        plansOrchestrator.register(activity)

        plansOrchestrator.onUpgradeResult { result ->
            state.update { current ->
                if (result != null && result.billingResult.subscriptionCreated) {
                    State.PurchaseSuccess(newPlanName = result.planId, newPlanDisplayName = result.planDisplayName)
                } else if (current is State.PurchaseReady) {
                    current.copy(inProgress = false)
                } else {
                    current // This should always be PlansFallback
                }
            }
        }
    }

    fun onPaymentStarted() {
        state.update { if (it is State.PurchaseReady) it.copy(inProgress = true) else it }
        upgradeTelemetry.onUpgradeAttempt()
    }

    fun onStartFallbackUpgrade() = viewModelScope.launch {
        userId.first()?.let { userId ->
            onPaymentStarted()
            plansOrchestrator.startUpgradeWorkflow(userId)
        }
    }
}
