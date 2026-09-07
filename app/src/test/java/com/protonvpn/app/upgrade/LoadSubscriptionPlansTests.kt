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
package com.protonvpn.app.upgrade

import com.protonvpn.android.ui.planupgrade.IapConstants
import com.protonvpn.android.ui.planupgrade.PaymentCycle
import com.protonvpn.android.ui.planupgrade.toISO8601
import com.protonvpn.android.ui.planupgrade.usecase.LoadPlansConfig
import com.protonvpn.android.ui.planupgrade.usecase.LoadSubscriptionPlans
import com.protonvpn.android.utils.Constants
import com.protonvpn.test.shared.createOffer
import com.protonvpn.test.shared.createOffersWithDiscount
import com.protonvpn.test.shared.createProduct
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.proton.android.payment.product.fake.FakeGetProducts
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LoadSubscriptionPlansTests {

    private lateinit var testScope: TestScope
    private lateinit var testGetProducts: FakeGetProducts

    private lateinit var loadSubscriptionPlans: LoadSubscriptionPlans

    private val tag1 = "tag1"
    private val tag2 = "tag2"

    private val monthly = PaymentCycle.Month(1)
    private val yearly = PaymentCycle.Year(1)

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        // Put all combinations in the default offers.
        val allCycles = listOf(PaymentCycle.Month(1), PaymentCycle.Year(1), PaymentCycle.Year(2))
        val products = allCycles.flatMap { paymentCycle ->
            val productPlus = createProduct(
                "productId-GooglePlay-vpn2022-${paymentCycle.toISO8601()}",
                Constants.CURRENT_PLUS_PLAN,
                offers = createOffersWithDiscount(paymentCycle, 99, 10_00),
            )
            val productUnlimited = createProduct(
                "productId-GooglePlay-bundle2022-${paymentCycle.toISO8601()}",
                Constants.CURRENT_BUNDLE_PLAN,
                offers = createOffersWithDiscount(paymentCycle, 2_99, 200_00),
            )
            listOf(productPlus, productUnlimited)
        }
        testGetProducts = FakeGetProducts().apply {
            setProductsToReturn(products)
        }
        loadSubscriptionPlans = LoadSubscriptionPlans(
            getProductsLazy = { testGetProducts },
        )
    }

    @Test
    fun `load plans and cycles if available`() = testScope.runTest {
        val cyclesToLoad = listOf(monthly, yearly)
        val plans = loadSubscriptionPlans(
            LoadPlansConfig.WithOptionalDiscount(
                planNames = listOf(Constants.CURRENT_PLUS_PLAN),
                paymentCycles = listOf(monthly, yearly),
                discountOfferTags = listOf(IapConstants.INTRO_PRICE_TAG),
            )
        )
        assertEquals(1, plans.size)
        val plan = plans.first()
        assertEquals(Constants.CURRENT_PLUS_PLAN, plan.name)
        assertEquals(cyclesToLoad, plan.cycles.map { it.cycle.paymentCycle })
    }

    @Test
    fun `don't load other plans`() = testScope.runTest {
        val offers = createOffersWithDiscount(monthly, 99, 10_00)
        val otherProduct = createProduct("other", "other_plan", offers)
        testGetProducts.setProductsToReturn(listOf(otherProduct))
        assertEquals(
            emptyList(),
            loadSubscriptionPlans(
                LoadPlansConfig.WithOptionalDiscount(
                    planNames = listOf(Constants.CURRENT_PLUS_PLAN),
                    paymentCycles = listOf(monthly),
                    discountOfferTags = listOf(IapConstants.INTRO_PRICE_TAG),
                )
            )
        )
    }

    private fun setupProductsForWithOptionalDiscountTests() {
        val offersPlusMonthly = listOf(
            createOffer(monthly, listOf(5_00), tags = emptyList(), tkn = "monthly_base"),
            createOffer(
                monthly,
                listOf(99, 5_00),
                tags = listOf(tag1),
                tkn = "monthly_discount_1"
            ),
            createOffer(
                monthly,
                listOf(2_00, 10_00),
                tags = listOf(tag2),
                tkn = "monthly_discount_2"
            ),
        )
        val offersPlusYearly = listOf(
            createOffer(yearly, listOf(50_00), tags = emptyList(), tkn = "yearly_base"),
            createOffer(yearly, listOf(25_00, 50_00), tags = listOf(tag2), tkn = "yearly_discount_2"),
        )
        val offersUnlimitedMonthly = listOf(
            createOffer(monthly, listOf(100_00), tags = emptyList(), tkn = "yearly_unlimited_base"),
            createOffer(monthly, listOf(75_00, 100_00), tags = listOf(tag1), tkn = "yearly_unlimited_discount_1"),
        )
        val plusMonthly = createProduct("idPlusMonthly", Constants.CURRENT_PLUS_PLAN, offersPlusMonthly)
        val plusYearly = createProduct("idPlusYearly", Constants.CURRENT_PLUS_PLAN, offersPlusYearly)
        val unlimitedMonthly = createProduct("idUnlimitedMonthly", Constants.CURRENT_BUNDLE_PLAN, offersUnlimitedMonthly)
        testGetProducts.setProductsToReturn(listOf(plusMonthly, plusYearly, unlimitedMonthly))
    }

    @Test
    fun `WithOptionalDiscount with multiple tags pick first tag with existing offer`() = testScope.runTest {
        setupProductsForWithOptionalDiscountTests()
        val loadedPlans = loadSubscriptionPlans(
            LoadPlansConfig.WithOptionalDiscount(
                planNames = listOf(Constants.CURRENT_PLUS_PLAN),
                paymentCycles = listOf(monthly, yearly),
                discountOfferTags = listOf(tag1, tag2),
            )
        )
        assertEquals(1, loadedPlans.size)
        val loadedPlan = loadedPlans.first()
        assertEquals(setOf("monthly_discount_1", "yearly_discount_2"), loadedPlan.cycles.mapTo(HashSet()) { it.offerToken })
    }

    @Test
    fun `WithOptionalDiscount with unknown tags picks base offer`() = testScope.runTest {
        setupProductsForWithOptionalDiscountTests()
        val loadedBasePlans = loadSubscriptionPlans(
            LoadPlansConfig.WithOptionalDiscount(
                planNames = listOf(Constants.CURRENT_PLUS_PLAN),
                paymentCycles = listOf(monthly, yearly),
                discountOfferTags = listOf("unknown_tag"),
            )
        )
        assertEquals(1, loadedBasePlans.size)
        val loadedBasePlan = loadedBasePlans.first()
        assertEquals(setOf("yearly_base", "monthly_base"), loadedBasePlan.cycles.mapTo(HashSet()) { it.offerToken })
    }

    fun setupProductsForWithOfferTagTests() {
        val baseOffer = createOffer(monthly, listOf(10_00), tags = listOf(tag1))
        val discountOffer = createOffer(monthly, listOf(5_00, 10_00), tags = listOf(tag1))
        val otherOffer = createOffer(monthly, listOf(1_00, 10_00), tags = listOf(tag2))
        val product = createProduct("plus1m", Constants.CURRENT_PLUS_PLAN, listOf(baseOffer, discountOffer, otherOffer))
        testGetProducts.setProductsToReturn(listOf(product))
    }

    @Test
    fun `WithOfferTag with only discount tag set`() = testScope.runTest {
        setupProductsForWithOfferTagTests()
        val loadedPlans = loadSubscriptionPlans(LoadPlansConfig.WithOfferTag(tag2, null))

        assertEquals(1, loadedPlans.size)
        val cycle = loadedPlans.first().cycles.firstOrNull()
        assertEquals(1_00, cycle?.currentPriceCents)
        assertEquals(10_00, cycle?.defaultPriceCents)
    }

    @Test
    fun `WithOfferTag with only base tag set`() = testScope.runTest {
        setupProductsForWithOfferTagTests()
        val loadedPlans = loadSubscriptionPlans(LoadPlansConfig.WithOfferTag(null, tag1))

        assertEquals(1, loadedPlans.size)
        val cycle = loadedPlans.first().cycles.firstOrNull()
        assertEquals(10_00, cycle?.currentPriceCents)
        assertEquals(10_00, cycle?.defaultPriceCents)
    }

    @Test
    fun `WithOfferTag with tag for both discount and base offer`() = testScope.runTest {
        setupProductsForWithOfferTagTests()
        val loadedPlans = loadSubscriptionPlans(LoadPlansConfig.WithOfferTag(tag1, tag1))

        assertEquals(1, loadedPlans.size)
        val cycle = loadedPlans.first().cycles.firstOrNull()
        assertEquals(5_00, cycle?.currentPriceCents)
        assertEquals(10_00, cycle?.defaultPriceCents)
    }

    @Test
    fun `fallback to available cycles`() = testScope.runTest {
        val offer1m = createOffer(PaymentCycle.Month(1), listOf(99))
        val offer2y = createOffer(PaymentCycle.Year(2), listOf(50_00))
        val products = listOf(
            createProduct("plus_1m", Constants.CURRENT_PLUS_PLAN, listOf(offer1m)),
            createProduct("plus_2y", Constants.CURRENT_PLUS_PLAN, listOf(offer2y)),
        )
        testGetProducts.setProductsToReturn(products)
        val loadedPlans = loadSubscriptionPlans(
            LoadPlansConfig.WithOptionalDiscount(
                planNames = listOf(Constants.CURRENT_PLUS_PLAN),
                paymentCycles = listOf(PaymentCycle.Month(1), PaymentCycle.Year(1)),
                discountOfferTags = listOf(IapConstants.INTRO_PRICE_TAG),
            )
        )
        assertEquals(1, loadedPlans.size)
        val plan = loadedPlans.first()
        assertEquals(listOf(PaymentCycle.Month(1)), plan.cycles.map { it.cycle.paymentCycle })
    }
}
