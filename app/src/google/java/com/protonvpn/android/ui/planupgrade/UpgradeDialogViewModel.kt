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
import com.protonvpn.android.ui.planupgrade.usecase.OneClickPaymentsEnabled
import com.protonvpn.android.ui.planupgrade.usecase.PaymentDisplayRenewPriceKillSwitch
import com.protonvpn.android.ui.planupgrade.usecase.WaitForSubscription
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.formatPrice
import com.protonvpn.android.utils.runCatchingCheckedExceptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.domain.entity.UserId
import me.proton.core.payment.domain.repository.BillingClientError
import me.proton.core.plan.domain.entity.DynamicPlan
import me.proton.core.plan.domain.usecase.PerformGiapPurchase
import me.proton.core.plan.presentation.PlansOrchestrator
import me.proton.core.plan.presentation.entity.PlanCycle
import me.proton.core.util.kotlin.filterNotNullValues
import org.jetbrains.annotations.VisibleForTesting
import javax.inject.Inject

@HiltViewModel
class UpgradeDialogViewModel(
    userId: Flow<UserId?>,
    authOrchestrator: AuthOrchestrator,
    plansOrchestrator: PlansOrchestrator,
    isInAppUpgradeAllowed: suspend () -> Boolean,
    upgradeTelemetry: UpgradeTelemetry,
    private val loadGoogleSubscriptionPlans: suspend (planNames: List<String>) -> List<GiapPlanInfo>,
    private val oneClickPaymentsEnabled: suspend () -> Boolean,
    private val oneClickUnlimitedEnabled: suspend () -> Boolean,
    private val performGiapPurchase: PerformGiapPurchase<Activity>,
    userPlanManager: UserPlanManager,
    waitForSubscription: WaitForSubscription,
    private val paymentDisplayRenewPriceKillSwitch: suspend () -> Boolean,
) : CommonUpgradeDialogViewModel(
    userId,
    authOrchestrator,
    plansOrchestrator,
    isInAppUpgradeAllowed::invoke,
    upgradeTelemetry,
    userPlanManager,
    waitForSubscription
) {

    @Inject
    constructor(
        currentUser: CurrentUser,
        authOrchestrator: AuthOrchestrator,
        plansOrchestrator: PlansOrchestrator,
        isInAppUpgradeAllowed: IsInAppUpgradeAllowedUseCase,
        upgradeTelemetry: UpgradeTelemetry,
        loadGoogleSubscriptionPlans: LoadGoogleSubscriptionPlans,
        oneClickPaymentsEnabled: OneClickPaymentsEnabled,
        oneClickUnlimitedEnabled: OneClickUnlimitedPlanEnabled,
        performGiapPurchase: PerformGiapPurchase<Activity>,
        userPlanManager: UserPlanManager,
        waitForSubscription: WaitForSubscription,
        paymentDisplayRenewPriceKillSwitch: PaymentDisplayRenewPriceKillSwitch,
    ) : this(
        currentUser.userFlow.map { it?.userId },
        authOrchestrator,
        plansOrchestrator,
        isInAppUpgradeAllowed::invoke,
        upgradeTelemetry,
        loadGoogleSubscriptionPlans::invoke,
        oneClickPaymentsEnabled::invoke,
        oneClickUnlimitedEnabled::invoke,
        performGiapPurchase,
        userPlanManager,
        waitForSubscription,
        paymentDisplayRenewPriceKillSwitch::invoke,
    )

    private var loadPlanNames: List<String>? = null

    private lateinit var loadedPlans: List<GiapPlanModel>
    private var showRenewPrice = true // Initialized in loadPlans()
    val selectedCycle = MutableStateFlow<PlanCycle?>(null)

    data class GiapPlanModel(
        val giapPlanInfo: GiapPlanInfo,
        val prices: Map<PlanCycle, PriceInfo>
    ) : PlanModel(displayName = giapPlanInfo.displayName, planName = giapPlanInfo.name, cycles = giapPlanInfo.cycles)

    fun reloadPlans() {
        loadPlanNames?.let { loadPlans(it) }
    }

    fun loadPlans(allowMultiplePlans: Boolean) {
        viewModelScope.launch {
            val unlimitedPlanEnabled = oneClickUnlimitedEnabled()
            val plans = when {
                allowMultiplePlans && unlimitedPlanEnabled ->
                    listOf(Constants.CURRENT_PLUS_PLAN, Constants.CURRENT_BUNDLE_PLAN)

                else ->
                    listOf(Constants.CURRENT_PLUS_PLAN)
            }
            loadPlans(plans)
        }
    }

    @VisibleForTesting
    fun loadPlans(planNames: List<String>) {
        loadPlanNames = planNames
        viewModelScope.launch {
            if (!isInAppUpgradeAllowed())
                state.value = State.UpgradeDisabled
            else {
                if (!oneClickPaymentsEnabled()) {
                    state.value = State.PlansFallback
                } else {
                    showRenewPrice = !paymentDisplayRenewPriceKillSwitch()
                    loadGiapPlans(planNames)
                }
            }
        }
    }

    // The plan first on the list is mandatory and will be preselected.
    private suspend fun loadGiapPlans(planNames: List<String>) {
        state.value = State.LoadingPlans
        suspend {
            val unorderedPlans = loadGoogleSubscriptionPlans(planNames).map { planInfo ->
                GiapPlanModel(planInfo, calculatePriceInfos(planInfo.cycles, planInfo.dynamicPlan))
            }
            // Plans order should match order of planNames.
            loadedPlans = planNames.mapNotNull { planName -> unorderedPlans.find { it.planName == planName } }
            val preselectedPlan = loadedPlans.find { it.planName == planNames.first() }
            if (loadedPlans.isNotEmpty() && preselectedPlan != null) {
                if (loadedPlans.any { it.prices.isEmpty() }) {
                    state.value = State.LoadError(R.string.error_fetching_prices)
                } else {
                    selectPlan(preselectedPlan)
                }
            } else {
                state.value = State.PlansFallback
            }
        }.runCatchingCheckedExceptions { e ->
            // loadGoogleSubscriptionPlans throws errors.
            state.value = State.LoadError(error = e)
        }
    }

    private fun removeProgressFromPurchaseReady() {
        state.update { if (it is State.PurchaseReady) it.copy(inProgress = false) else it }
    }

    fun selectPlan(plan: PlanModel) {
        val giapPlan = plan as GiapPlanModel
        state.value = State.PurchaseReady(
            allPlans = loadedPlans,
            selectedPlan = plan,
            selectedPlanPriceInfo = giapPlan.prices,
            showRenewPrice = showRenewPrice,
            inProgress = false
        )
        if (plan.cycles.none { it.cycle == selectedCycle.value }) {
            selectedCycle.value = giapPlan.giapPlanInfo.preselectedCycle
        }
    }

    fun pay(activity: Activity, flowType: UpgradeFlowType) = flow {
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
    }.onEach {
        state.value = it
    }.launchIn(viewModelScope)

    private fun emitError(billingClientError: BillingClientError?) {
        removeProgressFromPurchaseReady()
        purchaseError.trySend(PurchaseError(billingClientError))
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
        ): Map<PlanCycle, PriceInfo> {
            val currencies = dynamicPlan.instances
                .flatMap { (_, instance) -> instance.price.map { it.value.currency } }
                .toSet()

            // Temporary workaround for issue in core returning wrong prices if there was an issue
            // fetching prices from google billing library.
            if (currencies.isEmpty() || currencies.size > 1)
                return emptyMap()

            // The prices coming from Google Play will have a single currency:
            val currency = currencies.first()

            val perMonthPrices = cycles.associate { cycleInfo ->
                val months = cycleInfo.cycle.cycleDurationMonths
                val amount = dynamicPlan.instances[months]?.price?.get(currency)?.current?.centsToUnits()
                val perMonthPrice = if (months > 0 && amount != null && amount > 0.0)
                    amount / months else null
                cycleInfo.cycle to perMonthPrice
            }.filterNotNullValues()

            val maxPerMonthPrice = perMonthPrices.values.maxOrNull()

            return cycles.associate { cycleInfo ->
                val info = dynamicPlan.instances[cycleInfo.cycle.cycleDurationMonths]?.let { planInstance ->
                    val perMonthPrice = perMonthPrices[cycleInfo.cycle]
                    val priceAmount = planInstance.price.getValue(currency).current.centsToUnits()
                    val renewPriceAmount = planInstance.price.getValue(currency).default?.centsToUnits()
                    PriceInfo(
                        formattedPrice = formatPrice(priceAmount, currency),
                        formattedRenewPrice = renewPriceAmount?.let { formatPrice(it, currency) },
                        savePercent = calculateSavingsPercentage(perMonthPrice, maxPerMonthPrice),
                        formattedPerMonthPrice =
                        if (perMonthPrice != null && cycleInfo.cycle.cycleDurationMonths != 1)
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
