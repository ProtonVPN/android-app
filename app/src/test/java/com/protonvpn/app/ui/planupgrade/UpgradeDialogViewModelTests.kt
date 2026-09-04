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
import com.protonvpn.android.promooffers.data.ApiNotification
import com.protonvpn.android.promooffers.data.ApiNotificationOfferPanel
import com.protonvpn.android.promooffers.data.ApiNotificationProductDetails
import com.protonvpn.android.promooffers.data.ApiNotificationProductDetailsGoogle
import com.protonvpn.android.promooffers.data.ApiNotificationTypes
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.telemetry.UpgradeTrigger
import com.protonvpn.android.ui.planupgrade.PaymentCycle
import com.protonvpn.android.ui.planupgrade.PaymentRecurrence
import com.protonvpn.android.ui.planupgrade.PlanCycle
import com.protonvpn.android.ui.planupgrade.UpgradeDialogViewModel
import com.protonvpn.android.ui.planupgrade.UpgradeDialogViewModel.State
import com.protonvpn.android.ui.planupgrade.comparison_table.FakeIsUpsellComparisonTableExperimentEnabled
import com.protonvpn.android.ui.planupgrade.usecase.CycleInfo
import com.protonvpn.android.ui.planupgrade.usecase.GetUpgradeDialogPlansConfig
import com.protonvpn.android.ui.planupgrade.usecase.LoadSubscriptionPlans
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.formatPrice
import com.protonvpn.mocks.FakeCommonDimensions
import com.protonvpn.mocks.TestTelemetryReporter
import com.protonvpn.test.shared.ApiNotificationTestHelper.mockOffer
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestVpnUser
import com.protonvpn.test.shared.createOffer
import com.protonvpn.test.shared.createOffersWithDiscount
import com.protonvpn.test.shared.createProduct
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import java.util.Locale
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class UpgradeDialogViewModelTests {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockSaveMmpEvent: SaveMmpEvent

    private lateinit var testScope: TestScope
    private lateinit var getUpgradeDialogPlansConfig: GetUpgradeDialogPlansConfig
    private lateinit var viewModel: UpgradeDialogViewModel
    private lateinit var loadSubscriptionPlans: LoadSubscriptionPlans
    private lateinit var testGetProducts: FakeGetProducts
    private lateinit var testPurchaseProduct: FakePurchaseProduct
    private lateinit var testObservePurchaseState: FakeObserveSessionState
    private lateinit var testTelemetry: TestTelemetryReporter

    private var isInAppAllowed = true
    private lateinit var activeNotifications: MutableStateFlow<List<ApiNotification>>
    private val testPlanName = "myplan"

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)
        Locale.setDefault(Locale.US)

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

        activeNotifications = MutableStateFlow(emptyList())
        getUpgradeDialogPlansConfig = GetUpgradeDialogPlansConfig(
            isInAppUpgradeAllowed = { isInAppAllowed },
            activeNotificationsFlow = activeNotifications,
            awaitNotificationsUpdate = {},
        )
        viewModel = UpgradeDialogViewModel(
            upgradeTelemetry = upgradeTelemetry,
            getUpgradeDialogPlansConfig = getUpgradeDialogPlansConfig,
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
            assertEquals(PaymentCycle.Month(1), loadedState.selectedCycle)

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

            val purchase = ReconciledPurchaseSampleData.create(planId = testPlanName)
            testObservePurchaseState.emit(
                SessionState.Reconciling.Terminal.Success(purchase)
            )
            assertEquals(
                State.PurchaseSuccess(purchase.orderId, "myplan", PaymentCycle.Month(1), "EUR"),
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
        val cycleMonthly = PlanCycle(PaymentCycle.Month(1), PaymentRecurrence.Finite(1))
        val cycleYearly = PlanCycle(PaymentCycle.Year(1), PaymentRecurrence.Infinite)
        val priceInfo = UpgradeDialogViewModel.calculatePriceInfos(
            "vpn2022",
            "USD",
            listOf(
                CycleInfo(cycleMonthly, "m", "$testPlanName-m", emptyList(), 10_00, 15_00),
                CycleInfo(cycleYearly, "y", "$testPlanName-y", emptyList(), 100_00, 100_00),
            ),
            withSavePercent = true,
        )
        assertEquals(
            // Checks also the descending order by the cycle length in the list.
            listOf(
                UpgradeDialogViewModel.CycleViewInfo(
                    productId = "y",
                    offerToken = "$testPlanName-y",
                    cycle = cycleYearly,
                    priceInfo = UpgradeDialogViewModel.PriceInfo(
                        formattedPrice = formatPrice(100.0, "USD"),
                        savePercent = -44,
                        formattedPerMonthPrice = formatPrice(8.33, "USD"),
                        formattedRenewPrice = formatPrice(100.0, "USD"),
                        hasDiscountPrice = false,
                    )
                ),
                UpgradeDialogViewModel.CycleViewInfo(
                    productId = "m",
                    offerToken = "$testPlanName-m",
                    cycle = cycleMonthly,
                    priceInfo = UpgradeDialogViewModel.PriceInfo(
                        formattedPrice = formatPrice(10.0, "USD"),
                        savePercent = -33,
                        formattedRenewPrice = formatPrice(15.0, "USD"),
                        hasDiscountPrice = true,
                    )
                ),
            ),
            priceInfo.toList()
        )
    }

    @Test
    fun `plan order matches the order of plan names to loadPlans`() = testScope.runTest {
        val products = listOf(
            createProduct("p1_1", "plan1", listOf(createOffer(PaymentCycle.Month(1), listOf(1_00)))),
            createProduct("p2_1", "plan2", listOf(createOffer(PaymentCycle.Month(1), listOf(1_00)))),
            createProduct("p1_12", "plan1", listOf(createOffer(PaymentCycle.Year(1), listOf(10_00)))),
        )
        testGetProducts.setProductsToReturn(products)
        viewModel.loadBuiltinUpsellPlans(listOf("plan2", "plan1"))
        assertPlanNames(listOf("plan2", "plan1"), viewModel.upgradeState.first())
    }

    @Test
    fun `WHEN prices are loaded THEN upsell_price_display is reported`() = testScope.runTest {
        val offer = createOffer(PaymentCycle.Month(1), listOf(99), tags = listOf("notification"))
        val products = listOf(
            createProduct("plus_1", Constants.CURRENT_PLUS_PLAN, listOf(offer)),
            createProduct("bundle_1", Constants.CURRENT_BUNDLE_PLAN, listOf(offer)),
        )
        testGetProducts.setProductsToReturn(products)
        activeNotifications.value = listOf(
            createUpgradeOverrideNotification(
                type = ApiNotificationTypes.TYPE_BUILTIN_UPSELL_PADLOCK,
                offerTag = "notification",
                reference = "notificationRef"
            )
        )
        viewModel.loadBuiltinUpsellPlans(listOf(Constants.CURRENT_PLUS_PLAN, Constants.CURRENT_BUNDLE_PLAN))
        advanceUntilIdle()

        val event = testTelemetry.collectedEvents.lastOrNull()
        assertEquals("upsell_price_display", event?.eventName)
        assertEquals("false", event?.dimensions["has_intro_price"])
        assertEquals("notificationRef", event?.dimensions["reference"])
    }

    @Test
    fun `GIVEN notifications for builtin upsells WHEN loading plans THEN offer is overridden by notification`() = testScope.runTest {
        setupProductWithIntroAndCustomDiscount(
            introPrice = 99,
            customDiscountPrice = 199,
            basePrice = 499,
            customDiscountTag = "notification"
        )
        activeNotifications.value = listOf(
            createUpgradeOverrideNotification(ApiNotificationTypes.TYPE_BUILTIN_UPSELL_PADLOCK, "notification")
        )
        viewModel.loadBuiltinUpsellPlans(listOf(Constants.CURRENT_PLUS_PLAN))
        runCurrent()

        val state = viewModel.upgradeState.first()
        assertIs<State.PurchaseReady>(state)
        assertEquals(listOf("€1.99"), state.selectedPlan.cycles.map { it.priceInfo.formattedPrice })
        assertEquals(listOf("€4.99"), state.selectedPlan.cycles.map { it.priceInfo.formattedRenewPrice })
    }

    @Test
    fun `GIVEN notifications for builtin upsells but no offer WHEN loading plans THEN default offers are loaded`() = testScope.runTest {
        setupProductWithIntroAndCustomDiscount(
            introPrice = 99,
            customDiscountPrice = 199,
            basePrice = 499,
            customDiscountTag = "other"
        )
        activeNotifications.value = listOf(
            createUpgradeOverrideNotification(ApiNotificationTypes.TYPE_BUILTIN_UPSELL_PADLOCK, "notification")
        )
        viewModel.loadBuiltinUpsellPlans(listOf(Constants.CURRENT_PLUS_PLAN))
        runCurrent()

        val state = viewModel.upgradeState.first()
        assertIs<State.PurchaseReady>(state)
        assertEquals(listOf("€0.99"), state.selectedPlan.cycles.map { it.priceInfo.formattedPrice })
        assertEquals(listOf("€4.99"), state.selectedPlan.cycles.map { it.priceInfo.formattedRenewPrice })
    }

    @Test
    fun `GIVEN notification for builtin upsell WHEN get plans config THEN notification reference is provided`() = testScope.runTest {
        setupProductWithIntroAndCustomDiscount(
            introPrice = 99,
            customDiscountPrice = 199,
            basePrice = 499,
            customDiscountTag = "notification"
        )
        activeNotifications.value = listOf(
            createUpgradeOverrideNotification(
                ApiNotificationTypes.TYPE_BUILTIN_UPSELL_PADLOCK,
                "notification",
                reference = "notification-reference"
            )
        )

        val config = getUpgradeDialogPlansConfig.forBuiltinUpsell(
            ApiNotificationTypes.TYPE_BUILTIN_UPSELL_PADLOCK,
            listOf(Constants.CURRENT_PLUS_PLAN)
        )
        assertEquals("notification-reference", config?.notificationReference)
    }

    private fun assertPlanNames(expected: List<String>, state: State) {
        assertIs<State.PurchaseReady>(state)
        assertEquals(expected, state.allPlans.map { it.planName })
    }

    private fun setupProductWithIntroAndCustomDiscount(
        introPrice: Int,
        customDiscountPrice: Int,
        basePrice: Int,
        customDiscountTag: String
    ) {
        val defaultOffers = createOffersWithDiscount(PaymentCycle.Month(1), introPrice, basePrice)
        val notificationOffer = createOffer(PaymentCycle.Month(1), listOf(customDiscountPrice, basePrice), tags = listOf(customDiscountTag))
        val offers = defaultOffers + notificationOffer
        val products = listOf(createProduct("plus_1", Constants.CURRENT_PLUS_PLAN, offers))
        testGetProducts.setProductsToReturn(products)
    }

    private fun createUpgradeOverrideNotification(type: Int, offerTag: String, reference: String = offerTag) =
        mockOffer(
            id = "id",
            type = type,
            reference = reference,
            panel = ApiNotificationOfferPanel(
                iapProductDetails = ApiNotificationProductDetails(
                    ApiNotificationProductDetailsGoogle(offerTag = offerTag)
                )
            )
        )
}

private fun UpgradeDialogViewModel.loadBuiltinUpsellPlans(supportedPlanNames: List<String>) =
    loadBuiltinUpsellPlans(
        ApiNotificationTypes.TYPE_BUILTIN_UPSELL_PADLOCK,
        supportedPlanNames,
        shouldReportTelemetry = true,
        UpgradeSource.COUNTRIES,
        UpgradeTrigger.COUNTRY_SELECTION,
        countryId = null
    )
