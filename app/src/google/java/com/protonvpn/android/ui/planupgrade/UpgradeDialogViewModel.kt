/*
 * Copyright (c) 2023. Proton AG
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

import android.content.res.Resources
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.ui.planupgrade.usecase.GiapPlanInfo
import com.protonvpn.android.ui.planupgrade.usecase.LoadDefaultGooglePlan
import com.protonvpn.android.ui.planupgrade.usecase.OneClickPaymentsEnabled
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.payment.presentation.entity.BillingInput
import me.proton.core.payment.presentation.entity.PlanShortDetails
import me.proton.core.plan.presentation.PlansOrchestrator
import me.proton.core.plan.presentation.entity.PlanCycle
import me.proton.core.plan.presentation.viewmodel.filterByCycle
import javax.inject.Inject

@HiltViewModel
class UpgradeDialogViewModel @Inject constructor(
    private val currentUser: CurrentUser,
    authOrchestrator: AuthOrchestrator,
    plansOrchestrator: PlansOrchestrator,
    isInAppUpgradeAllowed: IsInAppUpgradeAllowedUseCase,
    upgradeTelemetry: UpgradeTelemetry,
    private val loadDefaultGiapPlan: LoadDefaultGooglePlan,
    private val oneClickPaymentsEnabled: OneClickPaymentsEnabled
) : CommonUpgradeDialogViewModel(
    currentUser,
    authOrchestrator,
    plansOrchestrator,
    isInAppUpgradeAllowed,
    upgradeTelemetry
) {

    private lateinit var loadedPlan : GiapPlanInfo

    class GiapPlanModel(
        val giapPlanInfo: GiapPlanInfo,
    ) : PlanModel {
        override val name get() = giapPlanInfo.name
        override val id: String get() = giapPlanInfo.productId
        override val cycle: PlanCycle get() = giapPlanInfo.cycle
    }

    init {
        loadPlans()
    }

    fun loadPlans() {
        if (!isInAppUpgradeAllowed())
            state.value = State.UpgradeDisabled
        else viewModelScope.launch {
            if (!oneClickPaymentsEnabled()) {
                state.value = State.PlansFallback
            } else {
                state.value = State.LoadingPlans
                try {
                    val giapPlan = loadDefaultGiapPlan()
                    state.value = if (giapPlan != null) {
                        loadedPlan = giapPlan
                        State.PlanLoaded(GiapPlanModel(giapPlan))
                    } else {
                        State.PlansFallback
                    }
                } catch (e: Throwable) {
                    // loadDefaultGiapPlan throws errors.
                    state.value = State.LoadError(e)
                }
            }
        }
    }

    suspend fun getBillingInput(
        resources: Resources
    ): BillingInput? {
        val currentState = state.value
        if (currentState !is State.PlanLoaded)
            return null
        val plan = (currentState.plan as GiapPlanModel).giapPlanInfo
        val userId = currentUser.user()?.userId?.id ?: return null
        val selectedPlan = plan.getSelectedPlan(resources, plan.cycle.cycleDurationMonths)
        return BillingInput(
            userId,
            plan = PlanShortDetails(
                name = selectedPlan.planName,
                displayName = selectedPlan.planDisplayName,
                subscriptionCycle = plan.cycle.toSubscriptionCycle(),
                currency = selectedPlan.currency.toSubscriptionCurrency(),
                services = selectedPlan.services,
                type = selectedPlan.type,
                vendors = selectedPlan.vendorNames.filterByCycle(plan.cycle)
            ),
            paymentMethodId = null
        )
    }

    fun onPurchaseSuccess() {
        state.value = State.PurchaseSuccess(
            newPlanName = loadedPlan.name,
            newPlanDisplayName = loadedPlan.displayName
        )
    }

    fun onErrorInFragment() {
        state.update { if (it is State.PurchaseReady) it.copy(inProgress = false) else it }
    }

    fun onUserCancelled() {
        state.update { if (it is State.PurchaseReady) it.copy(inProgress = false) else it }
    }

    fun onPriceAvailable(formattedPriceAndCurrency: String) {
        state.value = State.PurchaseReady(GiapPlanModel(loadedPlan), formattedPriceAndCurrency)
    }
}
