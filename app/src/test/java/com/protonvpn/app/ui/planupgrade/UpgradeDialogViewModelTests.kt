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

package com.protonvpn.app.ui.planupgrade

import android.app.Activity
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.mmp.events.MmpEvent
import com.protonvpn.android.mmp.events.MmpEventType
import com.protonvpn.android.mmp.events.usecases.SaveMmpEvent
import com.protonvpn.android.promooffers.usecase.FakeIsIapClientSidePromo12mEnabled
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.telemetry.UpgradeTrigger
import com.protonvpn.android.ui.planupgrade.CommonUpgradeDialogViewModel
import com.protonvpn.android.ui.planupgrade.CommonUpgradeDialogViewModel.State
import com.protonvpn.android.ui.planupgrade.UpgradeDialogViewModel
import com.protonvpn.android.ui.planupgrade.UpgradeFlowType
import com.protonvpn.android.ui.planupgrade.comparison_table.FakeIsUpsellComparisonTableEnabled
import com.protonvpn.android.ui.planupgrade.usecase.CycleInfo
import com.protonvpn.android.ui.planupgrade.usecase.LoadGoogleSubscriptionPlans
import com.protonvpn.android.ui.planupgrade.usecase.WaitForSubscription
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.formatPrice
import com.protonvpn.mocks.FakeCommonDimensions
import com.protonvpn.mocks.TestTelemetryReporter
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestLoadGoogleOffers
import com.protonvpn.test.shared.TestVpnUser
import com.protonvpn.test.shared.createDynamicPlan
import com.protonvpn.test.shared.createGiapOffer
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.session.SessionId
import me.proton.core.payment.domain.entity.Currency
import me.proton.core.payment.domain.entity.Purchase
import me.proton.core.payment.domain.entity.PurchaseState
import me.proton.core.payment.domain.usecase.PaymentProvider
import me.proton.core.plan.domain.entity.DynamicPlan
import me.proton.core.plan.domain.entity.DynamicPlanPrice
import me.proton.core.plan.domain.usecase.PerformGiapPurchase
import me.proton.core.plan.presentation.entity.PlanCycle
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Optional
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class UpgradeDialogViewModelTests {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private val userIdFlow = MutableStateFlow<UserId?>(UserId("test_user_id"))

    @MockK
    private lateinit var mockPerformGiapPurchase: PerformGiapPurchase<Activity>

    @RelaxedMockK
    private lateinit var mockWaitForSubscription: WaitForSubscription

    @MockK
    private lateinit var mockSaveMmpEvent: SaveMmpEvent

    private lateinit var testScope: TestScope
    private lateinit var viewModel: UpgradeDialogViewModel
    private lateinit var loadGoogleSubscriptionPlans: LoadGoogleSubscriptionPlans
    private lateinit var rawDynamicPlans: List<DynamicPlan>
    private lateinit var testLoadGoogleOffers: TestLoadGoogleOffers
    private lateinit var testTelemetry: TestTelemetryReporter

    private var isInAppAllowed = true
    private val availablePaymentProviders = setOf(PaymentProvider.GoogleInAppPurchase)

    private val dummyPrices = mapOf(
        PlanCycle.MONTHLY to mapOf("USD" to DynamicPlanPrice("", currency = "USD", current = 10_00)),
        PlanCycle.YEARLY to mapOf("USD" to DynamicPlanPrice("", currency = "USD", current = 100_00))
    )
    private val testPlanName = "myplan"

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)

        testLoadGoogleOffers = TestLoadGoogleOffers()
        isInAppAllowed = true
        rawDynamicPlans = listOf(createDynamicPlan(testPlanName, dummyPrices))
        testLoadGoogleOffers.offers = dummyGiapOffers(testPlanName)
        val currentUser = CurrentUser(TestCurrentUserProvider(TestVpnUser.create()))
        loadGoogleSubscriptionPlans = LoadGoogleSubscriptionPlans(
            vpnUserFlow = currentUser.vpnUserFlow,
            rawDynamicPlans = { rawDynamicPlans },
            loadGoogleOffers = testLoadGoogleOffers::invoke,
            availablePaymentProviders = { availablePaymentProviders },
            defaultCycles = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY),
            defaultPreselectedCycle = PlanCycle.MONTHLY,
        )
        testTelemetry = TestTelemetryReporter()
        val telemetryFlowHelper = TelemetryFlowHelper(testScope.backgroundScope, testTelemetry)
        val upgradeTelemetry = UpgradeTelemetry(
            commonDimensions = FakeCommonDimensions(emptyMap()),
            currentUser = currentUser,
            clock = { testScope.currentTime },
            telemetryHelperLazy = { telemetryFlowHelper },
            isUpsellComparisonTableEnabled = FakeIsUpsellComparisonTableEnabled(true),
            isIapClientSidePromo12mEnabled = FakeIsIapClientSidePromo12mEnabled(true),
        )

        viewModel = UpgradeDialogViewModel(
            userId = userIdFlow,
            authOrchestrator = mockk(relaxed = true),
            plansOrchestrator = mockk(relaxed = true),
            isInAppUpgradeAllowed = { isInAppAllowed },
            upgradeTelemetry = upgradeTelemetry,
            loadGoogleSubscriptionPlans = loadGoogleSubscriptionPlans::invoke,
            performGiapPurchase = mockPerformGiapPurchase,
            userPlanManager = mockk(relaxed = true),
            waitForSubscription = mockWaitForSubscription,
            convertToObservabilityGiapStatus = Optional.empty(),
            observabilityManager = mockk(relaxed = true),
            saveMmpEvent = mockSaveMmpEvent,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load default plan and purchase`() = testScope.runTest {
        val purchaseResult = MutableSharedFlow<PerformGiapPurchase.Result>()
        coEvery { mockPerformGiapPurchase.invoke(any(), any(), any(), any(), any()) } coAnswers {
            purchaseResult.first()
        }
        coEvery { mockSaveMmpEvent(any()) } returns Unit

        viewModel.loadPlans(listOf(testPlanName), null, null, true)

        viewModel.fullPanelState.test {
            val loadedState = awaitItem()
            assertIs<State.PurchaseReady>(loadedState.upgradeState)
            val loadedPlan = loadedState.upgradeState.selectedPlan
            assertEquals("myplan", loadedPlan.planName)
            assertFalse(loadedState.upgradeState.inProgress)
            assertEquals(PlanCycle.MONTHLY, loadedState.selectedCycle)

            loadedState.onPayClicked(mockk())
            assertTrue(assertIs<State.PurchaseReady>(awaitItem().upgradeState).inProgress)

            // Fail before succeeding
            purchaseResult.emit(PerformGiapPurchase.Result.Error.PurchaseNotFound)
            assertFalse(assertIs<State.PurchaseReady>(awaitItem().upgradeState).inProgress)

            // Try again and succeed
            loadedState.onPayClicked(mockk())
            assertTrue(assertIs<State.PurchaseReady>(awaitItem().upgradeState).inProgress)

            purchaseResult.emit(mockk<PerformGiapPurchase.Result.GiapSuccess>())
            assertEquals(
                State.PurchaseSuccess("myplan", UpgradeFlowType.ONE_CLICK, PlanCycle.MONTHLY.value),
                awaitItem().upgradeState
            )
        }
    }

    @Test
    fun `in-app payments disabled`() = testScope.runTest {
        isInAppAllowed = false
        viewModel.loadPlans(listOf(testPlanName), null, null, true)
        Assert.assertTrue(viewModel.state.value is State.UpgradeDisabled)
    }

    @Test
    fun `show error on plan load fail`() = testScope.runTest {
        rawDynamicPlans = emptyList()
        viewModel.loadPlans(listOf(testPlanName), null, null, true)
        val state = viewModel.state.first()
        assertIs<State.LoadError>(state)
        val error = viewModel.eventErrorMessage.receiveCatching().getOrNull()
        assertEquals(R.string.error_fetching_prices, error?.messageRes)
    }

    @Test
    fun `show error when first plan is missing`() = testScope.runTest {
        viewModel.loadPlans(listOf("missing plan", testPlanName), null, null, true)
        val state = viewModel.state.first()
        assertIs<State.LoadError>(state)
        val error = viewModel.eventErrorMessage.receiveCatching().getOrNull()
        assertEquals(R.string.error_fetching_prices, error?.messageRes)
    }

    @Test
    fun `ignore subsequent plans if missing`() = testScope.runTest {
        viewModel.loadPlans(listOf(testPlanName, "missing plan"), null, null, true)
        val state = viewModel.state.first()
        assertIs<State.PurchaseReady>(state)
        assertEquals(listOf(testPlanName), state.allPlans.map { it.planName })
    }

    @Test
    fun `calculate price info with and without savings`() = testScope.runTest {
        val priceInfo = UpgradeDialogViewModel.calculatePriceInfos(
            "vpn2022",
            "USD",
            listOf(
                CycleInfo(PlanCycle.MONTHLY, "m", "$testPlanName-m", 10_00, 15_00),
                CycleInfo(PlanCycle.YEARLY, "y", "$testPlanName-y", 100_00, 100_00),
            ),
            withSavePercent = true,
        )
        assertEquals(
            // Checks also the descending order by the cycle length in the list.
            listOf(
                CommonUpgradeDialogViewModel.CycleViewInfo(
                    cycle = PlanCycle.YEARLY,
                    perCycleResId = R.string.payment_price_per_year,
                    cycleLabelResId = R.string.payment_price_cycle_year_label,
                    priceInfo = CommonUpgradeDialogViewModel.PriceInfo(
                        formattedPrice = formatPrice(100.0, "USD"),
                        savePercent = -44,
                        formattedPerMonthPrice = formatPrice(8.33, "USD"),
                        formattedRenewPrice = formatPrice(100.0, "USD"),
                        hasIntroPrice = false,
                    )
                ),
                CommonUpgradeDialogViewModel.CycleViewInfo(
                    cycle = PlanCycle.MONTHLY,
                    perCycleResId = null,
                    cycleLabelResId = R.string.payment_price_cycle_month_label,
                    priceInfo = CommonUpgradeDialogViewModel.PriceInfo(
                        formattedPrice = formatPrice(10.0, "USD"),
                        savePercent = -33,
                        formattedRenewPrice = formatPrice(15.0, "USD"),
                        hasIntroPrice = true,
                    )
                ),
            ),
            priceInfo.toList()
        )
    }

    @Test
    fun `show error when prices are missing for any of the plans`() = testScope.runTest {
        val planName = Constants.CURRENT_PLUS_PLAN
        rawDynamicPlans = listOf(
            createDynamicPlan(
                planName,
                mapOf(
                    PlanCycle.MONTHLY to mapOf("USD" to DynamicPlanPrice("1", "USD", 1_00)),
                    PlanCycle.YEARLY to mapOf("USD" to DynamicPlanPrice("1", "USD", 2_00)),
                )
            )
        )
        testLoadGoogleOffers.offers = listOf(
            createGiapOffer(planName, PlanCycle.MONTHLY, listOf(1_00))
            // Yearly plan missing.
        )

        viewModel.loadPlans(listOf(planName), null, null, true)
        runCurrent()
        val state = viewModel.state.first()
        assertIs<State.LoadError>(state)
        val error = viewModel.eventErrorMessage.receiveCatching().getOrNull()
        assertEquals(R.string.error_fetching_prices, error?.messageRes)
        assertIs<LoadGoogleSubscriptionPlans.PartialPrices>(error?.throwable)
    }

    @Test
    fun `plan order matches the order of plan names to loadPlans`() = testScope.runTest {
        val planNames = arrayOf("plan1", "plan2")
        rawDynamicPlans = createDummyPlans(*planNames)
        testLoadGoogleOffers.offers = planNames.flatMap { dummyGiapOffers(it) }

        viewModel.loadPlans(listOf("plan2", "plan1"), null, null, true)
        assertPlanNames(listOf("plan2", "plan1"), viewModel.state.first())
    }

    @Test
    fun `when allowMultiplePlans is true then Plus and Unlimited plans are used`() = testScope.runTest {
        val planNames = arrayOf(Constants.CURRENT_PLUS_PLAN, Constants.CURRENT_BUNDLE_PLAN)
        rawDynamicPlans = createDummyPlans(*planNames)
        testLoadGoogleOffers.offers = planNames.flatMap { dummyGiapOffers(it) }
        viewModel.loadPlans(allowMultiplePlans = true)

        assertPlanNames(listOf(Constants.CURRENT_PLUS_PLAN, Constants.CURRENT_BUNDLE_PLAN), viewModel.state.first())
    }

    @Test
    fun `when allowMultiplePlans is false then only the first plan is used`() = testScope.runTest {
        val planNames = arrayOf(Constants.CURRENT_PLUS_PLAN, Constants.CURRENT_BUNDLE_PLAN)
        rawDynamicPlans = createDummyPlans(*planNames)
        testLoadGoogleOffers.offers = planNames.flatMap { dummyGiapOffers(it) }
        viewModel.loadPlans(allowMultiplePlans = false)

        assertPlanNames(listOf(Constants.CURRENT_PLUS_PLAN), viewModel.state.first())
    }

    @Test
    fun `WHEN payment is finished THEN Subscription mmp event is saved`() = testScope.runTest {
        val mmpEventTypeSlot = slot<MmpEventType>()
        val planName = Constants.CURRENT_PLUS_PLAN
        val planCycle = PlanCycle.MONTHLY
        val purchase = Purchase(
            sessionId = SessionId(id = "test_session_id"),
            planName = planName,
            planCycle = planCycle.value,
            purchaseState = PurchaseState.Purchased,
            purchaseFailure = null,
            paymentProvider = PaymentProvider.GoogleInAppPurchase,
            paymentOrderId = null,
            paymentToken = null,
            paymentCurrency = Currency.CHF,
            paymentAmount = 9900000,
        )
        val expectedMmpEventType = MmpEventType.Subscription(
            subscriptionDetails = MmpEvent.SubscriptionDetails(
                price = 9900000,
                currency = "CHF",
                cycle = 1,
                planName = planName,
                couponCode = null,
                transactionId = null,
                isFirstPurchase = null,
                isFreeToPaid = null,
            ) 
        )
        coEvery { mockSaveMmpEvent(eventType = capture(mmpEventTypeSlot)) } returns Unit
        coEvery { mockWaitForSubscription(planName = planName, userId = userIdFlow.first()) } returns purchase

        viewModel.onPaymentFinished(
            purchaseSuccessState = State.PurchaseSuccess(
                newPlanName = planName,
                upgradeFlowType = UpgradeFlowType.ONE_CLICK,
                billingCycle = planCycle.value,
            )
        )

        assertEquals(expectedMmpEventType, mmpEventTypeSlot.captured)
    }

    @Test
    fun `WHEN prices are loaded THEN upsell_price_display is reported`() = testScope.runTest {
        val planNames = arrayOf(Constants.CURRENT_PLUS_PLAN, Constants.CURRENT_BUNDLE_PLAN)
        rawDynamicPlans = createDummyPlans(*planNames)
        testLoadGoogleOffers.offers = planNames.flatMap { dummyGiapOffers(it) }
        viewModel.reportUpgradeFlowStart(UpgradeSource.COUNTRIES, UpgradeTrigger.COUNTRY_SELECTION)
        viewModel.loadPlans(true)
        runCurrent()

        val event = testTelemetry.collectedEvents.lastOrNull()
        assertEquals("upsell_price_display", event?.eventName)
        assertEquals("false", event?.dimensions["has_intro_price"])
    }

    private fun assertPlanNames(expected: List<String>, state: State) {
        assertIs<State.PurchaseReady>(state)
        assertEquals(expected, state.allPlans.map { it.planName })
    }

    private fun createDummyPlans(vararg planNames: String): List<DynamicPlan> =
        planNames.map { createDynamicPlan(it, dummyPrices) }

    private fun dummyGiapOffers(planName: String) = listOf(
        createGiapOffer(planName, PlanCycle.MONTHLY, listOf(10_00)),
        createGiapOffer(planName, PlanCycle.YEARLY, listOf(100_00)),
    )
}
