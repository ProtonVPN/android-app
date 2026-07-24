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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.mmp.events.MmpEvent
import com.protonvpn.android.mmp.events.MmpEventType
import com.protonvpn.android.mmp.events.usecases.SaveMmpEvent
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.telemetry.UpgradeTrigger
import com.protonvpn.android.ui.planupgrade.PlanCycle
import com.protonvpn.android.ui.planupgrade.UpgradeDialogViewModel
import com.protonvpn.android.ui.planupgrade.UpgradeDialogViewModel.State
import com.protonvpn.android.ui.planupgrade.comparison_table.FakeIsUpsellComparisonTableExperimentEnabled
import com.protonvpn.android.ui.planupgrade.usecase.CycleInfo
import com.protonvpn.android.ui.planupgrade.usecase.LoadSubscriptionPlans
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.formatPrice
import com.protonvpn.mocks.FakeCommonDimensions
import com.protonvpn.mocks.TestTelemetryReporter
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestVpnUser
import com.protonvpn.test.shared.createOffer
import com.protonvpn.test.shared.createProduct
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.proton.android.payment.common.exception.PaymentException
import me.proton.android.payment.product.fake.FakeGetProducts
import me.proton.android.payment.purchase.fake.FakeObserveSessionState
import me.proton.android.payment.purchase.fake.FakePurchaseProduct
import me.proton.android.payment.purchase.model.SessionState
import me.proton.android.payment.purchase.sampledata.ReconciledPurchaseSampleData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class UpgradeDialogViewModelTests {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockSaveMmpEvent: SaveMmpEvent

    private lateinit var testScope: TestScope
    private lateinit var viewModel: UpgradeDialogViewModel
    private lateinit var loadSubscriptionPlans: LoadSubscriptionPlans
    private lateinit var testGetProducts: FakeGetProducts
    private lateinit var testPurchaseProduct: FakePurchaseProduct
    private lateinit var testObservePurchaseState: FakeObserveSessionState
    private lateinit var testTelemetry: TestTelemetryReporter

    private var isInAppAllowed = true
    private val testPlanName = "myplan"

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)

        testGetProducts = FakeGetProducts()
        testPurchaseProduct = FakePurchaseProduct()
        testObservePurchaseState = FakeObserveSessionState()
        testObservePurchaseState.emit(SessionState.Idle)
        isInAppAllowed = true
        testGetProducts.setProductsToReturn(listOf(createProduct(testPlanName, testPlanName)))
        val currentUser = CurrentUser(TestCurrentUserProvider(TestVpnUser.create()))
        loadSubscriptionPlans = LoadSubscriptionPlans(
            getProductsLazy = { testGetProducts },
        )
        testTelemetry = TestTelemetryReporter()
        val telemetryFlowHelper = TelemetryFlowHelper(testScope.backgroundScope, testTelemetry)
        val upgradeTelemetry = UpgradeTelemetry(
            mainScope = testScope.backgroundScope,
            commonDimensions = FakeCommonDimensions(emptyMap()),
            currentUser = currentUser,
            clock = { testScope.currentTime },
            telemetryHelperLazy = { telemetryFlowHelper },
            isUpsellComparisonTableExperimentEnabled = FakeIsUpsellComparisonTableExperimentEnabled(true),
        )
        coEvery { mockSaveMmpEvent(eventType = any()) } returns Unit

        viewModel = UpgradeDialogViewModel(
            isInAppUpgradeAllowed = { isInAppAllowed },
            upgradeTelemetry = upgradeTelemetry,
            loadSubscriptionPlans = loadSubscriptionPlans::invoke,
            purchaseProduct = testPurchaseProduct,
            observePaymentSessionState = testObservePurchaseState,
            userPlanManager = mockk(relaxed = true),
            saveMmpEvent = mockSaveMmpEvent,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load default plan and purchase`() = testScope.runTest {
        coEvery { mockSaveMmpEvent(any()) } returns Unit

        viewModel.loadBuiltinUpsellPlans(listOf(testPlanName))

        viewModel.fullPanelState.test {
            val loadedState = awaitItem()
            assertIs<State.PurchaseReady>(loadedState.upgradeState)
            val loadedPlan = loadedState.upgradeState.selectedPlan
            assertEquals("myplan", loadedPlan.planName)
            assertFalse(loadedState.upgradeState.inProgress)
            assertEquals(PlanCycle.MONTHLY, loadedState.selectedCycle)

            // Fail before succeeding
            val error = SessionState.Purchasing.Terminal.Failure(PaymentException.NetworkError(1, Exception()))
            testPurchaseProduct.setResult(Result.success(error))
            loadedState.onPayClicked(mockk())
            testObservePurchaseState.emit(error)
            testObservePurchaseState.emit(SessionState.Idle)
            assertFalse(assertIs<State.PurchaseReady>(expectMostRecentItem().upgradeState).inProgress)

            // Try again and succeed
            testPurchaseProduct.setResult(Result.success(SessionState.Purchasing.Terminal.ReadyToReconcile))
            // Note: the state can't be Idle here because it's treated as a terminal state by UpdateDialogViewModel.
            testObservePurchaseState.emit(SessionState.Purchasing.InFlight.Purchasing)
            loadedState.onPayClicked(mockk())
            assertTrue(assertIs<State.PurchaseReady>(expectMostRecentItem().upgradeState).inProgress)

            val purchase = ReconciledPurchaseSampleData.create(
                planId = testPlanName,
                cycle = PlanCycle.MONTHLY.cycleDurationMonths,
            )
            testObservePurchaseState.emit(
                SessionState.Reconciling.Terminal.Success(purchase)
            )
            assertEquals(
                State.PurchaseSuccess(purchase.orderId, "myplan", PlanCycle.MONTHLY.cycleDurationMonths, "EUR"),
                awaitItem().upgradeState
            )

            val expectedMmpEventType = MmpEventType.Subscription(
                subscriptionDetails = MmpEvent.SubscriptionDetails(
                    price = 0L,
                    currency = "EUR",
                    cycle = 1,
                    planName = "myplan",
                    couponCode = null,
                    transactionId = purchase.orderId,
                    isFirstPurchase = null,
                    isFreeToPaid = null,
                )
            )
            coVerify(exactly = 1) { mockSaveMmpEvent.invoke(expectedMmpEventType) }
        }
    }

    @Test
    fun `in-app payments disabled`() = testScope.runTest {
        isInAppAllowed = false
        viewModel.upgradeState.test {
            viewModel.loadBuiltinUpsellPlans(listOf(testPlanName))
            assertIs<State.UpgradeDisabled>(expectMostRecentItem())
        }
    }

    @Test
    fun `show error on plan load fail`() = testScope.runTest {
        testGetProducts.setProductsToReturn(emptyList())
        viewModel.loadBuiltinUpsellPlans(listOf(testPlanName))
        val state = viewModel.upgradeState.first()
        assertIs<State.LoadError>(state)
        val error = viewModel.eventErrorMessage.receiveCatching().getOrNull()
        assertEquals(R.string.error_fetching_prices, error?.messageRes)
    }

    @Test
    fun `calculate price info with and without savings`() = testScope.runTest {
        val priceInfo = UpgradeDialogViewModel.calculatePriceInfos(
            "vpn2022",
            "USD",
            listOf(
                CycleInfo(PlanCycle.MONTHLY, "m", "$testPlanName-m", emptyList(), 10_00, 15_00),
                CycleInfo(PlanCycle.YEARLY, "y", "$testPlanName-y", emptyList(), 100_00, 100_00),
            ),
            withSavePercent = true,
        )
        assertEquals(
            // Checks also the descending order by the cycle length in the list.
            listOf(
                UpgradeDialogViewModel.CycleViewInfo(
                    productId = "y",
                    offerToken = "$testPlanName-y",
                    cycle = PlanCycle.YEARLY,
                    perCycleResId = R.string.payment_price_per_year,
                    cycleLabelResId = R.string.payment_price_cycle_year_label,
                    priceInfo = UpgradeDialogViewModel.PriceInfo(
                        formattedPrice = formatPrice(100.0, "USD"),
                        savePercent = -44,
                        formattedPerMonthPrice = formatPrice(8.33, "USD"),
                        formattedRenewPrice = formatPrice(100.0, "USD"),
                        hasIntroPrice = false,
                    )
                ),
                UpgradeDialogViewModel.CycleViewInfo(
                    productId = "m",
                    offerToken = "$testPlanName-m",
                    cycle = PlanCycle.MONTHLY,
                    perCycleResId = null,
                    cycleLabelResId = R.string.payment_price_cycle_month_label,
                    priceInfo = UpgradeDialogViewModel.PriceInfo(
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
    fun `plan order matches the order of plan names to loadPlans`() = testScope.runTest {
        val products = listOf(
            createProduct("p1_1", "plan1", listOf(createOffer(PlanCycle.MONTHLY, listOf(1_00)))),
            createProduct("p2_1", "plan2", listOf(createOffer(PlanCycle.MONTHLY, listOf(1_00)))),
            createProduct("p1_12", "plan1", listOf(createOffer(PlanCycle.YEARLY, listOf(10_00)))),
        )
        testGetProducts.setProductsToReturn(products)

        viewModel.loadBuiltinUpsellPlans(listOf("plan2", "plan1"))
        assertPlanNames(listOf("plan2", "plan1"), viewModel.upgradeState.first())
    }

    @Test
    fun `WHEN prices are loaded THEN upsell_price_display is reported`() = testScope.runTest {
        val products = listOf(
            createProduct("plus_1", Constants.CURRENT_PLUS_PLAN),
            createProduct("bundle_1", Constants.CURRENT_BUNDLE_PLAN),
        )
        testGetProducts.setProductsToReturn(products)
        viewModel.reportUpgradeFlowStart(UpgradeSource.COUNTRIES, UpgradeTrigger.COUNTRY_SELECTION)
        viewModel.loadBuiltinUpsellPlans(listOf(Constants.CURRENT_PLUS_PLAN, Constants.CURRENT_BUNDLE_PLAN))
        runCurrent()

        val event = testTelemetry.collectedEvents.lastOrNull()
        assertEquals("upsell_price_display", event?.eventName)
        assertEquals("false", event?.dimensions["has_intro_price"])
    }

    private fun assertPlanNames(expected: List<String>, state: State) {
        assertIs<State.PurchaseReady>(state)
        assertEquals(expected, state.allPlans.map { it.planName })
    }
}
