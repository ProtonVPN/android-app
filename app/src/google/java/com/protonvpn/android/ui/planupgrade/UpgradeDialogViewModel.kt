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

import android.app.Activity
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.ui.planupgrade.usecase.CycleInfo
import com.protonvpn.android.ui.planupgrade.usecase.GiapPlanInfo
import com.protonvpn.android.ui.planupgrade.usecase.LoadGoogleSubscriptionPlans
import com.protonvpn.android.ui.planupgrade.usecase.WaitForSubscription
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.formatPrice
import com.protonvpn.android.utils.ifOrNull
import com.protonvpn.android.utils.runCatchingCheckedExceptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.domain.entity.UserId
import me.proton.core.observability.domain.ObservabilityContext
import me.proton.core.observability.domain.ObservabilityManager
import me.proton.core.observability.domain.metrics.CheckoutGiapBillingLaunchBillingTotal
import me.proton.core.observability.domain.metrics.CheckoutGiapBillingProductQueryTotal
import me.proton.core.observability.domain.metrics.CheckoutGiapBillingQuerySubscriptionsTotal
import me.proton.core.payment.domain.extension.getCreatePaymentTokenObservabilityData
import me.proton.core.payment.domain.extension.getValidatePlanObservabilityData
import me.proton.core.payment.domain.repository.BillingClientError
import me.proton.core.payment.domain.usecase.ConvertToObservabilityGiapStatus
import me.proton.core.payment.domain.usecase.PaymentProvider
import me.proton.core.plan.domain.entity.DynamicPlan
import me.proton.core.plan.domain.entity.DynamicPlanPrice
import me.proton.core.plan.domain.usecase.PerformGiapPurchase
import me.proton.core.plan.presentation.PlansOrchestrator
import me.proton.core.plan.presentation.entity.PlanCycle
import me.proton.core.util.kotlin.coroutine.ResultCollector
import me.proton.core.util.kotlin.coroutine.launchWithResultContext
import me.proton.core.util.kotlin.filterNotNullValues
import org.jetbrains.annotations.VisibleForTesting
import java.util.Optional
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

@HiltViewModel
class UpgradeDialogViewModel(
    userId: Flow<UserId?>,
    authOrchestrator: AuthOrchestrator,
    plansOrchestrator: PlansOrchestrator,
    isInAppUpgradeAllowed: suspend () -> Boolean,
    upgradeTelemetry: UpgradeTelemetry,
    private val loadGoogleSubscriptionPlans: suspend (planNames: List<String>) -> List<GiapPlanInfo>,
    private val performGiapPurchase: PerformGiapPurchase<Activity>,
    userPlanManager: UserPlanManager,
    waitForSubscription: WaitForSubscription,
    private val convertToObservabilityGiapStatus: Optional<ConvertToObservabilityGiapStatus>,
    override val observabilityManager: ObservabilityManager,
) : CommonUpgradeDialogViewModel(
    userId,
    authOrchestrator,
    plansOrchestrator,
    isInAppUpgradeAllowed::invoke,
    upgradeTelemetry,
    userPlanManager,
    waitForSubscription
), ObservabilityContext {

    @Inject
    constructor(
        currentUser: CurrentUser,
        authOrchestrator: AuthOrchestrator,
        plansOrchestrator: PlansOrchestrator,
        isInAppUpgradeAllowed: IsInAppUpgradeAllowedUseCase,
        upgradeTelemetry: UpgradeTelemetry,
        loadGoogleSubscriptionPlans: LoadGoogleSubscriptionPlans,
        performGiapPurchase: PerformGiapPurchase<Activity>,
        userPlanManager: UserPlanManager,
        waitForSubscription: WaitForSubscription,
        convertToObservabilityGiapStatus: Optional<ConvertToObservabilityGiapStatus>,
        observabilityManager: ObservabilityManager,
    ) : this(
        currentUser.userFlow.map { it?.userId },
        authOrchestrator,
        plansOrchestrator,
        isInAppUpgradeAllowed::invoke,
        upgradeTelemetry,
        loadGoogleSubscriptionPlans::invoke,
        performGiapPurchase,
        userPlanManager,
        waitForSubscription,
        convertToObservabilityGiapStatus,
        observabilityManager,
    )

    private class ReloadState(
        val plans: List<String>,
        val cycles: List<PlanCycle>?,
        val buttonLabelOverride: String? = null,
        val showDiscountBadge: Boolean = true,
    )
    private var plansForReload: ReloadState? = null

    private lateinit var loadedPlans: List<GiapPlanModel>
    val selectedCycle = MutableStateFlow<PlanCycle?>(null)

    data class GiapPlanModel(
        val giapPlanInfo: GiapPlanInfo,
        val prices: Map<PlanCycle, PriceInfo>
    ) : PlanModel(displayName = giapPlanInfo.displayName, planName = giapPlanInfo.name, cycles = giapPlanInfo.cycles)

    fun reloadPlans() {
        plansForReload?.let { loadPlans(it.plans, it.cycles, it.buttonLabelOverride, it.showDiscountBadge) }
    }

    fun loadPlans(allowMultiplePlans: Boolean) {
        viewModelScope.launch {
            val plans = when {
                allowMultiplePlans ->
                    listOf(Constants.CURRENT_PLUS_PLAN, Constants.CURRENT_BUNDLE_PLAN)

                else ->
                    listOf(Constants.CURRENT_PLUS_PLAN)
            }
            loadPlans(plans, cycles = null, buttonLabelOverride = null, showDiscountBadge = true)
        }
    }

    fun loadPlans(
        planNames: List<String>,
        cycles: List<PlanCycle>?,
        buttonLabelOverride: String?,
        showDiscountBadge: Boolean
    ) {
        plansForReload = ReloadState(planNames, cycles, null, showDiscountBadge)
        viewModelScope.launch {
            if (!isInAppUpgradeAllowed()) {
                state.value = State.UpgradeDisabled
            } else {
                loadGiapPlans(planNames, cycles, buttonLabelOverride, showDiscountBadge)
            }
        }
    }

    // The plan first on the list is mandatory and will be preselected.
    private suspend fun loadGiapPlans(
        planNames: List<String>,
        cycleFilter: List<PlanCycle>?,
        buttonLabelOverride: String?,
        showDiscountBadge: Boolean,
    ) {
        state.value = State.LoadingPlans(cycleFilter?.size ?: 2, buttonLabelOverride)
        suspend {
            val unorderedPlans = loadGoogleSubscriptionPlans(planNames).map { inputPlanInfo ->
                val planInfo = if (cycleFilter != null) {
                    val filteredCycles = inputPlanInfo.cycles.filter { cycleFilter.contains(it.cycle) }
                    val selectedCycle = (filteredCycles.find { it.cycle == inputPlanInfo.preselectedCycle } ?: filteredCycles.first()).cycle
                    inputPlanInfo.copy(cycles = filteredCycles, preselectedCycle = selectedCycle)
                } else {
                    inputPlanInfo
                }
                GiapPlanModel(
                    planInfo,
                    calculatePriceInfos(planInfo.cycles, planInfo.dynamicPlan, showDiscountBadge)
                )
            }
            // Plans order should match order of planNames.
            loadedPlans = planNames.mapNotNull { planName -> unorderedPlans.find { it.planName == planName } }
            val preselectedPlan = loadedPlans.find { it.planName == planNames.first() }
            if (loadedPlans.isEmpty()
                // Note: plans with no Google prices should already be filtered out by GetDynamicPlansAdjustedPrices.
                || loadedPlans.any { it.prices.isEmpty() }
                || preselectedPlan == null
            ) {
                val errorInfo = plansDebugInfo(loadedPlans)
                state.value = State.LoadError(
                    messageRes = R.string.error_fetching_prices,
                    error = IllegalArgumentException("Missing prices: $errorInfo")
                )
            } else {
                selectPlan(preselectedPlan, buttonLabelOverride)
            }
        }.runCatchingCheckedExceptions { e ->
            // loadGoogleSubscriptionPlans throws errors.
            val errorMessage =
                if (e is LoadGoogleSubscriptionPlans.PartialPrices) R.string.error_fetching_prices else null
            state.value = State.LoadError(messageRes = errorMessage, error = e)
        }
    }

    private fun plansDebugInfo(plans: List<GiapPlanModel>): String =
        plans.joinToString("\n") { plan ->
            val info = plan.giapPlanInfo.dynamicPlan.instances.values.joinToString("; ") { dynPlan ->
                "Cycle: ${dynPlan.cycle}, Currencies: ${dynPlan.price.values.joinToString { it.currency }}"
            }
            "Plan: ${plan.planName}; DynamicPlan: $info"
        }

    private fun removeProgressFromPurchaseReady() {
        state.update { if (it is State.PurchaseReady) it.copy(inProgress = false) else it }
    }

    fun selectPlan(plan: PlanModel) {
        selectPlan(plan, plansForReload?.buttonLabelOverride)
    }

    private fun selectPlan(plan: PlanModel, buttonLabelOverride: String?) {
        val giapPlan = plan as GiapPlanModel
        state.value = State.PurchaseReady(
            allPlans = loadedPlans,
            selectedPlan = plan,
            selectedPlanPriceInfo = giapPlan.prices,
            inProgress = false,
            buttonLabelOverride = buttonLabelOverride
        )
        if (plan.cycles.none { it.cycle == selectedCycle.value }) {
            selectedCycle.value = giapPlan.giapPlanInfo.preselectedCycle
        }
    }

    fun pay(activity: Activity, flowType: UpgradeFlowType) = viewModelScope.launchWithResultContext {
        onResultEnqueueObservabilityEvents(PaymentProvider.GoogleInAppPurchase)
        flow {
            val currentState = state.value
            require(currentState is State.PurchaseReady)
            val cycle = requireNotNull(selectedCycle.value) { "Missing plan cycle." }
            val userId = requireNotNull(userId.first()) { "Missing user ID."}
            val plan = (currentState.selectedPlan as GiapPlanModel).giapPlanInfo

            val purchaseResult = performGiapPurchase(
                activity,
                cycle.cycleDurationMonths,
                plan.dynamicPlan,
                userId
            )
            val resultLog = when (purchaseResult) {
                is PerformGiapPurchase.Result.GiapSuccess -> "Success" // Don't log any details, like tokens.
                is PerformGiapPurchase.Result.Error.GiapUnredeemed -> "GiapUnredeemed"  // Don't log any details.
                is PerformGiapPurchase.Result.Error -> purchaseResult.toString()
            }
            ProtonLogger.logCustom(LogCategory.APP, "GIAP purchase result: $resultLog")
            when (purchaseResult) {
                is PerformGiapPurchase.Result.Error.GiapUnredeemed -> {
                    emit(State.PlansFallback)
                    onStartFallbackUpgrade()
                }
                is PerformGiapPurchase.Result.Error.UserCancelled -> emit(currentState.copy(inProgress = false))
                is PerformGiapPurchase.Result.Error.RecoverableBillingError ->
                    emitError(purchaseResult.error)
                is PerformGiapPurchase.Result.Error.UnrecoverableBillingError ->
                    emitError(purchaseResult.error)
                is PerformGiapPurchase.Result.Error ->
                    emitError(billingClientError = null)
                is PerformGiapPurchase.Result.GiapSuccess -> {
                    onPaymentFinished(plan.name, flowType)
                    emit(State.PurchaseSuccess(plan.name, flowType))
                }
            }
        }.catch {
            state.value = State.LoadError(error = it)
        }.collect {
            state.value = it
        }
    }

    private fun emitError(billingClientError: BillingClientError?) {
        removeProgressFromPurchaseReady()
        purchaseError.trySend(PurchaseError(billingClientError))
    }

    // See ProtonPaymentButtonViewModel.onResultEnqueueObservabilityEvents.
    private suspend fun ResultCollector<*>.onResultEnqueueObservabilityEvents(paymentProvider: PaymentProvider?) {
        convertToObservabilityGiapStatus.getOrNull()?.let { converter ->
            onResultEnqueueObservability("getProductsDetails") {
                CheckoutGiapBillingProductQueryTotal(converter(this))
            }
            onResultEnqueueObservability("querySubscriptionPurchases") {
                CheckoutGiapBillingQuerySubscriptionsTotal(converter(this))
            }
            onResultEnqueueObservability("launchBillingFlow") {
                CheckoutGiapBillingLaunchBillingTotal(converter(this))
            }
        }

        onResultEnqueueObservability("validateSubscription") {
            getValidatePlanObservabilityData(paymentProvider)
        }
        onResultEnqueueObservability("createPaymentToken") {
            getCreatePaymentTokenObservabilityData(paymentProvider)
        }
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
            dynamicPlan: DynamicPlan,
            withSavePercent: Boolean,
        ): Map<PlanCycle, PriceInfo> {
            val currency = dynamicPlan.getSingleCurrency() ?: return emptyMap()

            fun perMonthPrice(cycleInfo: CycleInfo, price: (DynamicPlanPrice) -> Int): Double? {
                val months = cycleInfo.cycle.cycleDurationMonths
                val amount = dynamicPlan.instances[months]?.price?.get(currency)?.let { price(it) }?.centsToUnits()
                return if (months > 0 && amount != null && amount > 0.0) {
                    amount / months
                } else {
                    null
                }
            }

            val perMonthCurrentPrices = cycles.associate { cycleInfo ->
                cycleInfo.cycle to perMonthPrice(cycleInfo) { it.current }
            }.filterNotNullValues()

            val maxPerMonthPrice = cycles
                .mapNotNull { cycleInfo -> perMonthPrice(cycleInfo) { it.default ?: it.current } }
                .max()

            return cycles.associate { cycleInfo ->
                val info = dynamicPlan.instances[cycleInfo.cycle.cycleDurationMonths]?.let { planInstance ->
                    val perMonthPrice = perMonthCurrentPrices[cycleInfo.cycle]
                    val priceAmount = planInstance.price.getValue(currency).current.centsToUnits()
                    val renewPriceAmount = planInstance.price.getValue(currency).default?.centsToUnits()
                    val showPerMonthPrice =
                        perMonthPrice != null && cycleInfo.cycle.cycleDurationMonths != 1 && renewPriceAmount == null
                    PriceInfo(
                        formattedPrice = formatPrice(priceAmount, currency),
                        formattedRenewPrice = renewPriceAmount?.let { formatPrice(it, currency) },
                        savePercent = ifOrNull(withSavePercent) { calculateSavingsPercentage(perMonthPrice, maxPerMonthPrice) },
                        formattedPerMonthPrice = if (showPerMonthPrice)
                            formatPrice(perMonthPrice, currency) else null
                    )
                }
                cycleInfo.cycle to info
            }.filterNotNullValues().toSortedMap(compareByDescending { it.cycleDurationMonths })
        }
    }
}

@Suppress("MagicNumber")
private fun Int.centsToUnits(): Double = this / 100.0
