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
import com.protonvpn.android.ui.planupgrade.usecase.CycleInfo
import com.protonvpn.android.ui.planupgrade.usecase.GiapPlanInfo
import com.protonvpn.android.ui.planupgrade.usecase.LoadDefaultGooglePlan
import com.protonvpn.android.ui.planupgrade.usecase.OneClickPaymentsEnabled
import com.protonvpn.android.utils.formatPrice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.domain.entity.UserId
import me.proton.core.payment.presentation.entity.BillingInput
import me.proton.core.payment.presentation.entity.PlanShortDetails
import me.proton.core.paymentiap.presentation.viewmodel.GoogleProductDetails
import me.proton.core.paymentiap.presentation.viewmodel.GoogleProductId
import me.proton.core.plan.presentation.PlansOrchestrator
import me.proton.core.plan.presentation.entity.PlanCycle
import me.proton.core.plan.presentation.viewmodel.filterByCycle
import me.proton.core.util.kotlin.filterNullValues
import org.jetbrains.annotations.VisibleForTesting
import javax.inject.Inject

@HiltViewModel
class UpgradeDialogViewModel(
    userId: Flow<UserId?>,
    authOrchestrator: AuthOrchestrator,
    plansOrchestrator: PlansOrchestrator,
    isInAppUpgradeAllowed: () -> Boolean,
    upgradeTelemetry: UpgradeTelemetry,
    private val loadDefaultGiapPlan: suspend () -> GiapPlanInfo?,
    private val oneClickPaymentsEnabled: suspend () -> Boolean,
    private val loadOnStart: Boolean
) : CommonUpgradeDialogViewModel(
    userId,
    authOrchestrator,
    plansOrchestrator,
    isInAppUpgradeAllowed::invoke,
    upgradeTelemetry
) {

    @Inject
    constructor(
        currentUser: CurrentUser,
        authOrchestrator: AuthOrchestrator,
        plansOrchestrator: PlansOrchestrator,
        isInAppUpgradeAllowed: IsInAppUpgradeAllowedUseCase,
        upgradeTelemetry: UpgradeTelemetry,
        loadDefaultGiapPlan: LoadDefaultGooglePlan,
        oneClickPaymentsEnabled: OneClickPaymentsEnabled
    ) : this(
        currentUser.userFlow.map { it?.userId },
        authOrchestrator,
        plansOrchestrator,
        isInAppUpgradeAllowed::invoke,
        upgradeTelemetry,
        loadDefaultGiapPlan::invoke,
        oneClickPaymentsEnabled::invoke,
        true
    )

    private lateinit var loadedPlan : GiapPlanInfo
    val selectedCycle = MutableStateFlow<PlanCycle?>(null)

    data class GiapPlanModel(
        val giapPlanInfo: GiapPlanInfo,
    ) : PlanModel {
        override val name get() = giapPlanInfo.name
        override val cycles get() = giapPlanInfo.cycles
    }

    init {
        if (loadOnStart)
            loadPlans()
    }

    fun loadPlans() {
        if (!isInAppUpgradeAllowed())
            state.value = State.UpgradeDisabled
        else viewModelScope.launch {
            if (!oneClickPaymentsEnabled()) {
                state.value = State.PlansFallback
            } else {
                loadGiapPlans()
            }
        }
    }

    private suspend fun loadGiapPlans() {
        state.value = State.LoadingPlans
        try {
            val giapPlan = loadDefaultGiapPlan()
            if (giapPlan != null) {
                loadedPlan = giapPlan
                state.value = State.PlanLoaded(GiapPlanModel(giapPlan))
                selectedCycle.value = giapPlan.preselectedCycle
            } else {
                state.value = State.PlansFallback
            }
        } catch (e: Throwable) {
            // loadDefaultGiapPlan throws errors.
            state.value = State.LoadError(e)
        }
    }

    suspend fun getBillingInput(
        resources: Resources
    ): BillingInput? {
        val currentState = state.value
        if (currentState !is State.PlanLoaded)
            return null
        val cycle = selectedCycle.value ?: return null
        val plan = (currentState.plan as GiapPlanModel).giapPlanInfo
        val userId = userId.first()?.id ?: return null
        val selectedPlan = plan.getSelectedPlan(resources, cycle.cycleDurationMonths)
        return BillingInput(
            userId,
            plan = PlanShortDetails(
                name = selectedPlan.planName,
                displayName = selectedPlan.planDisplayName,
                subscriptionCycle = cycle.toSubscriptionCycle(),
                currency = selectedPlan.currency.toSubscriptionCurrency(),
                services = selectedPlan.services,
                type = selectedPlan.type,
                vendors = selectedPlan.vendorNames.filterByCycle(cycle)
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
        removeProgressFromPurchaseReady()
    }

    fun onUserCancelled() {
        removeProgressFromPurchaseReady()
    }

    private fun removeProgressFromPurchaseReady() {
        state.update { if (it is State.PurchaseReady) it.copy(inProgress = false) else it }
    }

    fun onPricesAvailable(idToPrice: Map<GoogleProductId, GoogleProductDetails>) {
        val prices = calculatePriceInfos(loadedPlan.cycles, idToPrice)
        require(prices.isNotEmpty())
        state.value = State.PurchaseReady(GiapPlanModel(loadedPlan), prices)
    }

    companion object {

        @VisibleForTesting
        fun calculateSavingsPercentage(price: Double?, maxPerMonthPrice: Double?): Int? {
            if (price == null || maxPerMonthPrice == null)
                return null
            return (-100 * (1 - price / maxPerMonthPrice)).toInt().takeIf { it <= -5 }
        }

        @VisibleForTesting
        fun calculatePriceInfos(
            cycles: List<CycleInfo>,
            priceDetails: Map<GoogleProductId, GoogleProductDetails>
        ): Map<PlanCycle, PriceInfo> {
            val perMonthPrices = cycles.associate { cycleInfo ->
                val id = GoogleProductId(cycleInfo.productId)
                val months = cycleInfo.cycle.cycleDurationMonths
                val amount = priceDetails[id]?.priceAmount
                val perMonthPrice = if (months > 0 && amount != null && amount > 0.0)
                    amount / months else null
                cycleInfo.cycle to perMonthPrice
            }.filterNullValues()

            val maxPerMonthPrice = perMonthPrices.values.maxOrNull()

            return cycles.associate { cycleInfo ->
                val info = priceDetails[GoogleProductId(cycleInfo.productId)]?.let { details ->
                    val perMonthPrice = perMonthPrices[cycleInfo.cycle]
                    PriceInfo(
                        formattedPrice = formatPrice(details.priceAmount, details.currency),
                        savePercent = calculateSavingsPercentage(perMonthPrice, maxPerMonthPrice),
                        formattedPerMonthPrice =
                            if (perMonthPrice != null && cycleInfo.cycle.cycleDurationMonths != 1)
                                formatPrice(perMonthPrice, details.currency) else null
                    )
                }
                cycleInfo.cycle to info
            }.filterNullValues().toSortedMap(compareByDescending { it.cycleDurationMonths })
        }
    }
}
