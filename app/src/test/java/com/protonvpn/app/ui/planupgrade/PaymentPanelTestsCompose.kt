/*
 * Copyright (c) 2026. Proton AG
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

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.di.Distinct
import com.protonvpn.android.mmp.events.usecases.SaveMmpEvent
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.ui.planupgrade.IapConstants
import com.protonvpn.android.ui.planupgrade.PaymentCycle
import com.protonvpn.android.ui.planupgrade.PaymentPanel
import com.protonvpn.android.ui.planupgrade.UpgradeDialogViewModel
import com.protonvpn.android.ui.planupgrade.usecase.LoadPlansConfig
import com.protonvpn.android.ui.planupgrade.usecase.LoadSubscriptionPlans
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.app.testRules.RobolectricHiltAndroidRule
import com.protonvpn.test.shared.createOffer
import com.protonvpn.test.shared.createOffersWithDiscount
import com.protonvpn.test.shared.createProduct
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import me.proton.android.payment.di.ScreenUseCaseModule
import me.proton.android.payment.product.fake.FakeGetProducts
import me.proton.android.payment.product.model.Product
import me.proton.android.payment.product.usecase.GetProducts
import me.proton.android.payment.purchase.fake.FakeObserveSessionState
import me.proton.android.payment.purchase.fake.FakePurchaseProduct
import me.proton.android.payment.purchase.model.SessionState
import me.proton.android.payment.purchase.usecase.ObserveSessionState
import me.proton.android.payment.purchase.usecase.PurchaseProduct
import me.proton.android.payment.subscription.fake.FakeGetSubscriptions
import me.proton.android.payment.subscription.usecase.GetSubscriptions
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.ui.compose.FusionComposeTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import javax.inject.Inject
import javax.inject.Singleton

@UninstallModules(ScreenUseCaseModule::class)
@RunWith(RobolectricTestRunner::class)
@HiltAndroidTest
class PaymentPanelTestsCompose : FusionComposeTest() {

    @Module
    @InstallIn(SingletonComponent::class)
    class FakePaymentsModule {
        @Provides
        @Singleton
        fun fakeGetProducts() = FakeGetProducts()

        @Provides
        fun getProducts(fake: FakeGetProducts): GetProducts = fake

        @Provides
        fun observeSessionState(): ObserveSessionState = FakeObserveSessionState().apply {
            // FakeObserveSessionState uses SharedFlow instead of StateFlow and starts with no state.
            this.emit(SessionState.Idle)
        }

        @Provides
        fun purchaseProduct(): PurchaseProduct = FakePurchaseProduct()

        @Provides
        fun getSubscriptions(): GetSubscriptions = FakeGetSubscriptions()

        // Note: ideally we would inject a GetPaymentStatus that is fixed to enabled.
    }

    @Inject lateinit var testGetProducts: FakeGetProducts
    @Inject lateinit var viewModelInjector: UpgradeDialogViewModelInjector

    @get:Rule
    val hiltRule = RobolectricHiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
        ShadowLog.stream = System.out // Redirect Logcat to console
    }

    @Test
    fun renewTextBasePlanOnly() {
        val baseOffer = createOffer(PaymentCycle.Month(1), listOf(199))
        setupComposablesAndLoadPlans(
            listOf(createProduct("id", "vpn2022", listOf(baseOffer))),
            LoadPlansConfig.WithOptionalDiscount(listOf("vpn2022"), listOf(PaymentCycle.Month(1)), "unknownTag")
        )
        node.withTag("renewInfoText")
            .assertContainsText("Subscription auto renews at €1.99/month")
    }

    @Test
    fun renewTextOfferSingleCycle() {
        val discountOffers = createOffersWithDiscount(PaymentCycle.Week(1), 99, 199, "USD")
        setupComposablesAndLoadPlans(
            listOf(createProduct("id", "vpn2022",discountOffers)),
            LoadPlansConfig.WithOfferTag(IapConstants.INTRO_PRICE_TAG)
        )
        node.withTag("renewInfoText")
            .assertContainsText("Special offer. Auto renews at $1.99/week")
    }

    @Test
    fun renewTextOfferMultiCycle() {
        val discount2yOffers = createOffersWithDiscount(
            paymentCycle = PaymentCycle.Year(1),
            discountPriceCents = 4999,
            basePriceCents = 9999,
            currency = "PLN",
            offerCycleCount = 2,
        )
        setupComposablesAndLoadPlans(
            listOf(createProduct("id", "vpn2022", discount2yOffers)),
            LoadPlansConfig.WithOfferTag(IapConstants.INTRO_PRICE_TAG)
        )
        node.withTag("renewInfoText")
            .assertContainsText("PLN 49.99/year for the first 2 years, then auto renews at PLN 99.99/year")
    }

    @Test
    fun renewTextOffer2MonthCycle() {
        val discount = createOffersWithDiscount(
            PaymentCycle.Month(2),
            discountPriceCents = 199,
            basePriceCents = 599,
            currency = "USD",
        )
        setupComposablesAndLoadPlans(
            listOf(createProduct("id", "vpn2022", discount)),
            LoadPlansConfig.WithOfferTag(IapConstants.INTRO_PRICE_TAG)
        )
        node.withTag("renewInfoText")
            .assertContainsText("Special offer. Auto renews at $5.99 every 2 months")
    }

    @Test
    fun renewTextOfferMultiCycle2MonthCycle() {
        val discount = createOffersWithDiscount(
            PaymentCycle.Month(2),
            discountPriceCents = 199,
            basePriceCents = 599,
            currency = "USD",
            offerCycleCount = 2,
        )
        setupComposablesAndLoadPlans(
            listOf(createProduct("id", "vpn2022", discount)),
            LoadPlansConfig.WithOfferTag(IapConstants.INTRO_PRICE_TAG)
        )
        node.withTag("renewInfoText")
            .assertContainsText("$1.99 every 2 months for the first 4 months, then auto renews at $5.99 every 2 months")
    }

    @Test
    fun cyclePerMonthPrice() {
        val tags = listOf("tag")
        val offers = listOf(
            createOffer(PaymentCycle.Week(1), listOf(100), tags = tags),
            createOffer(PaymentCycle.Month(1), listOf(200), tags = tags),
            createOffer(PaymentCycle.Month(2), listOf(300), tags = tags),
            createOffer(PaymentCycle.Year(1), listOf(600), tags = tags),
        )
        // All products must be on the same plan.
        val products = offers.map { offer -> createProduct("id", "plan", listOf(offer)) }
        setupComposablesAndLoadPlans(products, LoadPlansConfig.WithOfferTag(tags.first()))

        assertPriceString("cycleP1W", "€1.00", null)
        assertPriceString("cycleP1M", "€2.00", null)
        assertPriceString("cycleP2M", "€3.00", "€1.50 /month")
        assertPriceString("cycleP1Y", "€6.00", "€0.50 /month")
    }

    @Test
    fun cycleNoPerMonthPriceOnMultiCycleMonthlyOffer() {
        val offers = createOffersWithDiscount(PaymentCycle.Month(1), 99, 199, offerCycleCount = 2)
        val products = listOf(createProduct("id", "plan", offers))
        setupComposablesAndLoadPlans(products, LoadPlansConfig.WithOfferTag(IapConstants.INTRO_PRICE_TAG))

        assertPriceString("cycleP1M", "€0.99", null)
    }

    private fun setupComposablesAndLoadPlans(
        products: List<Product>,
        loadPlansConfig: LoadPlansConfig,
        preselectedCycle: PaymentCycle? = null
    ) {
        testGetProducts.setProductsToReturn(products)
        val viewModel = viewModelInjector.createViewModel()
        viewModel.loadPlans(loadPlansConfig, preselectedCycle, showDiscountBadge = false)
        composeRule.setContent {
            val state = viewModel.fullPanelState.collectAsStateWithLifecycle().value
            PaymentPanel(state, {})
        }
        composeRule.onRoot().printToLog("Compose")
    }

    private fun assertPriceString(
        cycleTag: String,
        expectedPrice: String,
        expectedPerMonthPrice: String?,
    ) {
        // Use ComposeTestRule directly, Fusion doesn't offer assert for equal text 🙄
        val pricesNodes = composeRule.onNodeWithTag(cycleTag, useUnmergedTree = true)
            .onChildren()
            .filterToOne(hasTestTag("prices"))
            .onChildren()
        pricesNodes.onFirst().assertTextEquals(expectedPrice)
        if (expectedPerMonthPrice != null) {
            pricesNodes.onLast().assertTextEquals(expectedPerMonthPrice)
        } else {
            pricesNodes.assertCountEquals(1)
        }
    }
}

@Distinct
class UpgradeDialogViewModelInjector @Inject constructor(
    private val upgradeTelemetry: UpgradeTelemetry,
    private val loadSubscriptionPlans: LoadSubscriptionPlans,
    private val purchaseProduct: PurchaseProduct,
    private val observeSessionState: ObserveSessionState,
    private val userPlanManager: UserPlanManager,
    private val saveMmpEvent: SaveMmpEvent,
) {
    fun createViewModel() = UpgradeDialogViewModel(
        isInAppUpgradeAllowed = suspend { true },
        upgradeTelemetry = upgradeTelemetry,
        loadSubscriptionPlans = loadSubscriptionPlans::invoke,
        purchaseProduct = purchaseProduct,
        observePaymentSessionState = observeSessionState,
        userPlanManager = userPlanManager,
        saveMmpEvent = saveMmpEvent,
    )
}
