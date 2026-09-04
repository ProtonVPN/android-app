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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.mmp.events.MmpEvent
import com.protonvpn.android.mmp.events.MmpEventType
import com.protonvpn.android.mmp.events.usecases.SaveMmpEvent
import com.protonvpn.android.promooffers.ui.NotificationIapParams
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.telemetry.UpgradeTrigger
import com.protonvpn.android.ui.planupgrade.UpgradeDialogViewModel.CycleViewInfo
import com.protonvpn.android.ui.planupgrade.UpgradeDialogViewModel.State.PurchaseSuccess
import com.protonvpn.android.ui.planupgrade.usecase.CycleInfo
import com.protonvpn.android.ui.planupgrade.usecase.GetUpgradeDialogPlansConfig
import com.protonvpn.android.ui.planupgrade.usecase.LoadPlansConfig
import com.protonvpn.android.ui.planupgrade.usecase.LoadSubscriptionPlans
import com.protonvpn.android.ui.planupgrade.usecase.SubscriptionPlanInfo
import com.protonvpn.android.ui.planupgrade.usecase.UpgradeDialogLoadPlansConfig
import com.protonvpn.android.ui.planupgrade.usecase.shouldReportToSentry
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.formatPrice
import com.protonvpn.android.utils.ifOrNull
import com.protonvpn.android.utils.runCatchingCheckedExceptions
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sentry.Sentry
import io.sentry.Sentry.captureException
import io.sentry.SentryEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.proton.android.payment.common.exception.PaymentException
import me.proton.android.payment.common.model.OfferToken
import me.proton.android.payment.common.model.ProductId
import me.proton.android.payment.purchase.extension.transacting
import me.proton.android.payment.purchase.model.PendingPurchase
import me.proton.android.payment.purchase.model.SessionState
import me.proton.android.payment.purchase.usecase.ObserveSessionState
import me.proton.android.payment.purchase.usecase.PurchaseProduct
import me.proton.core.util.kotlin.filterNotNullValues
import org.jetbrains.annotations.VisibleForTesting
import javax.inject.Inject
import kotlin.math.max

class PlanModel(
    val displayName: String,
    val planName: String,
    val currency: String,
    val cycles: List<CycleViewInfo>,
    val preselectedCycle: PaymentCycle,
)

@HiltViewModel
class UpgradeDialogViewModel(
    private val upgradeTelemetry: UpgradeTelemetry,
    private val getUpgradeDialogPlansConfig: GetUpgradeDialogPlansConfig,
    private val loadSubscriptionPlans: suspend (selection: LoadPlansConfig) -> List<SubscriptionPlanInfo>,
    private val purchaseProduct: PurchaseProduct,
    private val observePaymentSessionState: ObserveSessionState,
    private val userPlanManager: UserPlanManager,
    private val saveMmpEvent: SaveMmpEvent,
) : ViewModel() {

    @Inject
    constructor(
        upgradeTelemetry: UpgradeTelemetry,
        getUpgradeDialogPlansConfig: GetUpgradeDialogPlansConfig,
        loadSubscriptionPlans: LoadSubscriptionPlans,
        purchaseProduct: PurchaseProduct,
        observePaymentSessionState: ObserveSessionState,
        userPlanManager: UserPlanManager,
        saveMmpEvent: SaveMmpEvent,
    ) : this(
        upgradeTelemetry = upgradeTelemetry,
        getUpgradeDialogPlansConfig = getUpgradeDialogPlansConfig,
        loadSubscriptionPlans = loadSubscriptionPlans::invoke,
        purchaseProduct = purchaseProduct,
        observePaymentSessionState = observePaymentSessionState,
        userPlanManager = userPlanManager,
        saveMmpEvent = saveMmpEvent,
    )

    data class PriceInfo(
        val formattedPrice: String,
        val formattedRenewPrice: String = formattedPrice,
        val hasDiscountPrice: Boolean,
        val savePercent: Int? = null,
        val formattedPerMonthPrice: String? = null,
    )
    data class CycleViewInfo(
        val productId: ProductId,
        val offerToken: OfferToken,
        val cycle: PlanCycle,
        val priceInfo: PriceInfo,
    )
    sealed interface State {

        sealed interface LoadPurchase : State

        val inProgress: Boolean get() = false

        object Initializing : LoadPurchase
        object UpgradeDisabled : LoadPurchase
        data class LoadingPlans(
            val expectedCycleCount: Int,
            val buttonLabelOverride: String?,
        ) : LoadPurchase {
            override val inProgress: Boolean = true
        }
        object LoadError : LoadPurchase // Error messages are emitted via onError.
        data class PurchaseReady(
            val allPlans: List<PlanModel>,
            val selectedPlan: PlanModel,
            override val inProgress: Boolean,
            val buttonLabelOverride: String? = null,
        ) : LoadPurchase

        data class PurchaseSuccess(
            val orderId: String,
            val newPlanName: String,
            val paymentCycle: PaymentCycle?,
            val currency: String?,
        ) : State
    }

    data class Error(val messageRes: Int?, val throwable: Throwable?)

    private val errorMessage = Channel<Error>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val eventErrorMessage: ReceiveChannel<Error> = errorMessage

    private var plansForReload: UpgradeDialogLoadPlansConfig? = null

    private lateinit var loadedPlans: List<PlanModel>
    private val selectedCycle = MutableStateFlow<PaymentCycle?>(null)
    private val paymentSessionState = observePaymentSessionState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SessionState.Idle)
    private val loadPurchaseState = MutableStateFlow<State.LoadPurchase>(State.Initializing)

    val upgradeState: StateFlow<State> = combine(
        loadPurchaseState,
        paymentSessionState,
    ) { upgradeState, paymentState ->
        when {
            paymentState is SessionState.Reconciling.Terminal.Success -> {
                // Payment state's lifetime may be longer than lifetime of the activity/view model.
                val selectedPlan = (loadPurchaseState.value as? State.PurchaseReady)?.selectedPlan
                with(paymentState.purchase) {
                    PurchaseSuccess(
                        newPlanName = planId,
                        paymentCycle = selectedCycle.value,
                        orderId = this.orderId,
                        currency = selectedPlan?.currency,
                    )
                }
            }

            upgradeState is State.PurchaseReady -> {
                upgradeState.copy(inProgress = paymentState.transacting())
            }

            else -> upgradeState
        }
    }
        // Keep observing forever, payment states are temporary (e.g. Success) and quickly change
        // back to Idle. Using WhileSubscribed may lead to loss of state that is otherwise preserved
        // with the "else" branch.
        .stateIn(viewModelScope, SharingStarted.Lazily, State.Initializing)

    val fullPanelState: StateFlow<PaymentPanelState> = combine(
        upgradeState,
        selectedCycle,
    ) { currentState, currentSelectedCycle ->
        buildFullState(currentState, currentSelectedCycle)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        buildFullState(State.Initializing, null)
    )

    init {
        upgradeState
            .filterIsInstance<PurchaseSuccess>()
            .onEach {
                onPaymentFinished(
                    it.orderId, it.newPlanName, it.currency, it.paymentCycle,
                    UpgradeFlowType.ONE_CLICK
                )
            }
            .launchIn(viewModelScope)

        paymentSessionState
            .onEach { state ->
                when (state) {
                    is SessionState.Purchasing.Terminal.Failure ->
                        onError(error = state.exception, paymentsCode = state.exception.code)
                    is SessionState.Reconciling.Terminal.Failure ->
                        onError(error = state.exception, paymentsCode = state.exception.code)
                    else -> Unit
                }
            }.launchIn(viewModelScope)
    }

    fun reloadPlans() {
        plansForReload?.let {
            viewModelScope.launch {
                loadPlans(it.planSelection, null, it.preselectedCycle, it.buttonLabelOverride, it.showDiscountBadge)
            }
        }
    }

    fun loadBuiltinUpsellPlans(
        notificationType: Int,
        supportedPlanNames: List<String>,
        shouldReportTelemetry: Boolean,
        upgradeSource: UpgradeSource,
        upgradeTrigger: UpgradeTrigger,
        countryId: CountryId? = null,
    ) {
        loadPurchaseState.value = State.LoadingPlans(2, null)
        viewModelScope.launch {
            val config = getUpgradeDialogPlansConfig.forBuiltinUpsell(notificationType, supportedPlanNames)
            if (shouldReportTelemetry) {
                upgradeTelemetry.onUpgradeFlowStarted(
                    upgradeSource,
                    upgradeTrigger,
                    countryId,
                    config?.notificationReference
                )
            }
            loadPlans(config)
        }
    }

    fun loadPlansForNotification(
        iapParams: NotificationIapParams,
        buttonLabelOverride: String?,
        shouldReportTelemetry: Boolean,
        upgradeSource: UpgradeSource,
        upgradeTrigger: UpgradeTrigger,
        notificationReference: String?,
    ) {
        loadPurchaseState.value = State.LoadingPlans(1, null)
        viewModelScope.launch {
            val config = getUpgradeDialogPlansConfig.forNotification(iapParams, buttonLabelOverride, notificationReference)
            if (shouldReportTelemetry) {
                upgradeTelemetry.onUpgradeFlowStarted(
                    upgradeSource,
                    upgradeTrigger,
                    null,
                    notificationReference
                )
            }
            loadPlans(config)
        }
    }

    @VisibleForTesting
    suspend fun loadPlansForTests(config: UpgradeDialogLoadPlansConfig?) = loadPlans(config)

    private suspend fun loadPlans(config: UpgradeDialogLoadPlansConfig?) {
        if (config == null) {
            loadPurchaseState.value = State.UpgradeDisabled
        } else {
            plansForReload = config
            with(config) {
                loadPlans(
                    planSelection,
                    null,
                    preselectedCycle,
                    buttonLabelOverride,
                    showDiscountBadge
                )
            }
        }
    }

    private suspend fun loadPlans(
        selection: LoadPlansConfig,
        preselectedPlan: String?,
        preselectedCycle: PaymentCycle?,
        buttonLabelOverride: String?,
        showDiscountBadge: Boolean,
    ) {
        loadPurchaseState.value = State.LoadingPlans(2, buttonLabelOverride)
        suspend {
            val unorderedPlans = loadSubscriptionPlans(selection)
                .map { planInfo ->
                    val cyclesDescending = calculatePriceInfos(
                        planInfo.name,
                        planInfo.currency,
                        planInfo.cycles,
                        showDiscountBadge
                    )
                    val preselectedCycle =
                        if (preselectedCycle != null && cyclesDescending.any { it.cycle.paymentCycle == preselectedCycle }) {
                            preselectedCycle
                        } else {
                            cyclesDescending.first().cycle.paymentCycle
                        }
                    PlanModel(
                        displayName = planInfo.displayName,
                        planName = planInfo.name,
                        currency = planInfo.currency,
                        cycles = cyclesDescending,
                        preselectedCycle = preselectedCycle,
                    )
                }
            loadedPlans = unorderedPlans.orderForConfig(selection)
            val preselectedPlan = loadedPlans.find { it.planName == preselectedPlan } ?: loadedPlans.firstOrNull()
            if (loadedPlans.isEmpty()
                // Note: plans with no Google prices should already be filtered out by LoadSubscriptionPlans.
                || loadedPlans.any { it.cycles.isEmpty() }
                || preselectedPlan == null
            ) {
                val errorInfo = loadedPlans.joinToString("\n") { "Plan: $it" }
                loadPurchaseState.value = State.LoadError
                onError(
                    messageRes = R.string.error_fetching_prices,
                    error = IllegalArgumentException("Missing prices: $errorInfo")
                )
            } else {
                reportPricesLoaded(loadedPlans.hasDiscountPrice())
                selectPlan(preselectedPlan, buttonLabelOverride)
            }
        }.runCatchingCheckedExceptions { e ->
            // loadGoogleSubscriptionPlans throws errors.
            loadPurchaseState.value = State.LoadError
            onError(error = e, paymentsCode = if (e is PaymentException) e.code else null)
        }
    }

    fun selectPlan(plan: PlanModel) {
        selectPlan(plan, plansForReload?.buttonLabelOverride)
    }

    private fun selectPlan(plan: PlanModel, buttonLabelOverride: String?) {
        loadPurchaseState.value = State.PurchaseReady(
            allPlans = loadedPlans,
            selectedPlan = plan,
            inProgress = false,
            buttonLabelOverride = buttonLabelOverride
        )
        if (plan.cycles.none { it.cycle.paymentCycle == selectedCycle.value }) {
            selectedCycle.value = plan.preselectedCycle
        }
    }

    private fun pay() = viewModelScope.launch {
        val currentState = loadPurchaseState.value
        require(currentState is State.PurchaseReady)
        val cycle = requireNotNull(selectedCycle.value) { "Missing plan cycle." }
        val plan = currentState.selectedPlan
        val planCycle = requireNotNull(plan.cycles.find { it.cycle.paymentCycle == cycle }) {
            "Missing cycle $cycle for ${plan.planName}"
        }

        val purchase = PendingPurchase(planCycle.productId, planCycle.offerToken)
        purchaseProduct(purchase) // Ignore result, the payment session state will report everything.
    }

    fun reportPricesLoaded(hasIntroPrices: Boolean) {
        upgradeTelemetry.onPricesLoaded(hasIntroPrices)
    }

    private suspend fun onPaymentFinished(
        orderId: String,
        newPlanName: String,
        currency: String?,
        paymentCycle: PaymentCycle?,
        upgradeFlowType: UpgradeFlowType,
    ) {
        upgradeTelemetry.onUpgradeSuccess(newPlanName, upgradeFlowType, paymentCycle)
        val mmpSubscriptionCycle = when (paymentCycle) {
            is PaymentCycle.Month -> paymentCycle.count
            is PaymentCycle.Year -> paymentCycle.count * 12
            else -> 0
        }
        val subscriptionDetails = MmpEvent.SubscriptionDetails(
            price = 0L,
            currency = currency ?: "",
            planName = newPlanName,
            cycle = mmpSubscriptionCycle,
            transactionId = orderId,
            couponCode = null,
            isFirstPurchase = null,
            isFreeToPaid = null,
        )
        saveMmpEvent(MmpEventType.Subscription(subscriptionDetails))
        userPlanManager.refreshVpnInfo()
    }

    private fun List<PlanModel>.orderForConfig(config: LoadPlansConfig): List<PlanModel> {
        fun List<PlanModel>.matchPlanNames(planNames: List<String>) =
            planNames.mapNotNull { planName -> find { it.planName == planName } }

        return when (config) {
            is LoadPlansConfig.WithOfferTag -> this
            is LoadPlansConfig.WithOfferTagAndFilter -> matchPlanNames(config.planNames)
            is LoadPlansConfig.WithOptionalDiscount -> matchPlanNames(config.planNames)
        }
    }

    private fun onError(messageRes: Int? = null, error: Throwable? = null, paymentsCode: Int? = null) {
        if (shouldReportToSentry(error))
            logToSentry(error?.message, error, paymentsCode) // Remove this once we know payments are in a good shape.
        if (paymentsCode != null || error != null)
            ProtonLogger.logCustom(LogCategory.IN_APP_PURCHASE, "Code: $paymentsCode; ${error?.message}")
        errorMessage.trySend(Error(messageRes, error))
    }

    private fun List<PlanModel>.hasDiscountPrice(): Boolean =
        any { plan -> plan.cycles.any { it.priceInfo.hasDiscountPrice }}

    private fun buildFullState(
        currentState: State,
        currentSelectedCycle: PaymentCycle?,
    ) = PaymentPanelState(
        upgradeState = currentState,
        selectedCycle = currentSelectedCycle,
        onPayClicked = { activity ->
            if (currentState is State.PurchaseReady) {
                val flowType = UpgradeFlowType.ONE_CLICK
                val planId = currentState.selectedPlan.planName
                upgradeTelemetry.onUpgradeAttempt(flowType, planId, currentSelectedCycle)
                pay()
            }
        },
        onErrorButtonClicked = ::reloadPlans,
        onCycleSelected = { selectedCycle.value = it },
    )

    private fun logToSentry(errorMessage: String?, throwable: Throwable?, paymentsCode: Int?) {
        val sentryMessage = buildList {
            if (paymentsCode != null) add("Payments code: $paymentsCode")
            if (errorMessage != null) add("Error message: $errorMessage")
        }.joinToString("; ")
        Sentry.captureEvent(SentryEvent(OneClickPaymentError(sentryMessage, throwable)))
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
            planName: String,
            currency: String,
            cycles: List<CycleInfo>,
            withSavePercent: Boolean,
        ): List<CycleViewInfo> {
            fun perMonthPrice(cycleInfo: CycleInfo, price: (CycleInfo) -> Int): Double? {
                val amount = price(cycleInfo).centsToUnits()
                val months = when (val paymentCycle = cycleInfo.cycle.paymentCycle) {
                    // We don't support weekly yet.
                    is PaymentCycle.Day -> 0
                    is PaymentCycle.Week -> 0
                    is PaymentCycle.Month -> paymentCycle.count
                    is PaymentCycle.Year -> 12 * paymentCycle.count
                }
                return if (months > 0 && amount > 0.0) {
                    amount / months
                } else {
                    null
                }
            }

            val perMonthCurrentPrices = cycles.associate { cycleInfo ->
                cycleInfo.cycle to perMonthPrice(cycleInfo) { it.currentPriceCents }
            }.filterNotNullValues()

            val maxPerMonthPrice = cycles
                .mapNotNull { cycleInfo ->
                    perMonthPrice(cycleInfo) { max(it.currentPriceCents, it.defaultPriceCents) }
                }
                .maxOrNull()

            val cyclesWithPrices = cycles.map { cycleInfo ->
                val cycle = cycleInfo.cycle
                val perMonthPrice = perMonthCurrentPrices[cycle]
                val priceAmount = cycleInfo.currentPriceCents.centsToUnits()
                val renewPriceAmount = cycleInfo.defaultPriceCents.centsToUnits()
                val showPerMonthPrice = perMonthPrice != null && with(cycleInfo.cycle) {
                    paymentCycle != PaymentCycle.Month(1) && paymentCycle.unitOrder() >= PaymentCycle.Month(1).unitOrder()
                }
                val priceInfo = PriceInfo(
                    formattedPrice = formatPrice(priceAmount, currency),
                    formattedRenewPrice = formatPrice(renewPriceAmount, currency),
                    savePercent = ifOrNull(withSavePercent) {
                        calculateSavingsPercentage(perMonthPrice, maxPerMonthPrice)
                    },
                    formattedPerMonthPrice = if (showPerMonthPrice) { formatPrice(perMonthPrice, currency) } else null,
                    hasDiscountPrice = priceAmount != renewPriceAmount,
                )
                CycleViewInfo(
                    productId = cycleInfo.productId,
                    offerToken = cycleInfo.offerToken,
                    cycle = cycle,
                    priceInfo = priceInfo
                )
            }.sortedWith(
                compareBy<CycleViewInfo> { it.cycle.paymentCycle.unitOrder() }
                    .thenBy { it.cycle.paymentCycle.count }
                    .reversed()
            )

            reportDuplicatesToSentry(planName, cyclesWithPrices)
            return cyclesWithPrices
        }
    }
}

class OneClickPaymentError(message: String?, cause: Throwable?) : Throwable(message, cause)

private fun reportDuplicatesToSentry(
    planName: String,
    cyclesWithPrices: List<CycleViewInfo>
) {
    val byCycle = cyclesWithPrices.groupBy { it.cycle }
    byCycle.filter { (_, prices) -> prices.size > 1 }
        .onEach { (cycle, prices) ->
            val pricesString = prices.joinToString { it.priceInfo.formattedPrice }
            val message = "Multiple prices for plan '$planName' $cycle: $pricesString"
            ProtonLogger.logCustom(LogCategory.IN_APP_PURCHASE, message)
            captureException(DuplicatePricesError(message))
        }
}

private class DuplicatePricesError(message: String) : Throwable(message)

@Suppress("MagicNumber")
private fun Int.centsToUnits(): Double = this / 100.0
