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
import com.protonvpn.android.ui.planupgrade.PlanCycle
import com.protonvpn.android.ui.planupgrade.usecase.CycleInfo
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

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        // Put all combinations in the default offers.
        val allCycles = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY, PlanCycle.TWO_YEARS)
        val products = allCycles.flatMap { planCycle ->
            val productPlus = createProduct(
                "productId-GooglePlay-vpn2022-${planCycle.cycleDurationMonths}",
                Constants.CURRENT_PLUS_PLAN,
                offers = createOffersWithDiscount(planCycle, 99, 10_00),
            )
            val productUnlimited = createProduct(
                "productId-GooglePlay-bundle2022-${planCycle.cycleDurationMonths}",
                Constants.CURRENT_BUNDLE_PLAN,
                offers = createOffersWithDiscount(planCycle, 2_99, 200_00),
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
        val cyclesToLoad = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY)
        val plans = loadSubscriptionPlans(
            LoadPlansConfig.WithOptionalDiscount(
                planNames = listOf(Constants.CURRENT_PLUS_PLAN),
                planCycles = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY),
                discountOfferTag = IapConstants.INTRO_PRICE_TAG,
            )
        )
        assertEquals(1, plans.size)
        val plan = plans.first()
        assertEquals(Constants.CURRENT_PLUS_PLAN, plan.name)
        assertEquals(cyclesToLoad, plan.cycles.map { it.cycle })
    }

    @Test
    fun `don't load other plans`() = testScope.runTest {
        val offers = createOffersWithDiscount(PlanCycle.MONTHLY, 99, 10_00)
        val otherProduct = createProduct("other", "other_plan", offers)
        testGetProducts.setProductsToReturn(listOf(otherProduct))
        assertEquals(
            emptyList(),
            loadSubscriptionPlans(
                LoadPlansConfig.WithOptionalDiscount(
                    planNames = listOf(Constants.CURRENT_PLUS_PLAN),
                    planCycles = listOf(PlanCycle.MONTHLY),
                    discountOfferTag = IapConstants.INTRO_PRICE_TAG,
                )
            )
        )
    }

    @Test
    fun `WithOptionalDiscount picks offers with offer tag and if missing, uses the base plan`() = testScope.runTest {
        val offers = listOf(
            createOffer(PlanCycle.MONTHLY, listOf(5_00), tags = emptyList(), token = "token_base"),
            createOffer(
                PlanCycle.MONTHLY,
                listOf(99, 5_00),
                tags = listOf(IapConstants.INTRO_PRICE_TAG),
                token = "token_intro"
            ),
            createOffer(
                PlanCycle.MONTHLY,
                listOf(2_00, 10_00),
                tags = listOf(),
                token = "token_intro_2"
            ),
        )
        val product = createProduct("productId", Constants.CURRENT_PLUS_PLAN, offers)
        testGetProducts.setProductsToReturn(listOf(product))

        val loadedIntroPlans = loadSubscriptionPlans(
            LoadPlansConfig.WithOptionalDiscount(
                planNames = listOf(Constants.CURRENT_PLUS_PLAN),
                planCycles = listOf(PlanCycle.MONTHLY),
                discountOfferTag = IapConstants.INTRO_PRICE_TAG,
            )
        )
        assertEquals(1, loadedIntroPlans.size)
        val loadedIntroPlan = loadedIntroPlans.first()
        val expectedCycle = CycleInfo(PlanCycle.MONTHLY, "productId", "token_intro", listOf(IapConstants.INTRO_PRICE_TAG), 99, 5_00)
        assertEquals(listOf(expectedCycle), loadedIntroPlan.cycles)

        val loadedBasePlans = loadSubscriptionPlans(
            LoadPlansConfig.WithOptionalDiscount(
                planNames = listOf(Constants.CURRENT_PLUS_PLAN),
                planCycles = listOf(PlanCycle.MONTHLY),
                discountOfferTag = "unknown_tag",
            )
        )
        assertEquals(1, loadedIntroPlans.size)
        val loadedBasePlan = loadedBasePlans.first()
        assertEquals(
            listOf(CycleInfo(PlanCycle.MONTHLY, "productId", "token_base", emptyList(), 5_00, 5_00)),
            loadedBasePlan.cycles
        )
    }

    // TODO: test WithOfferTag

    @Test
    fun `fallback to available cycles`() = testScope.runTest {
        val offer1m = createOffer(PlanCycle.MONTHLY, listOf(99))
        val offer2y = createOffer(PlanCycle.TWO_YEARS, listOf(50_00))
        val products = listOf(
            createProduct("plus_1m", Constants.CURRENT_PLUS_PLAN, listOf(offer1m)),
            createProduct("plus_2y", Constants.CURRENT_PLUS_PLAN, listOf(offer2y)),
        )
        testGetProducts.setProductsToReturn(products)
        val loadedPlans = loadSubscriptionPlans(
            LoadPlansConfig.WithOptionalDiscount(
                planNames = listOf(Constants.CURRENT_PLUS_PLAN),
                planCycles = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY),
                discountOfferTag = IapConstants.INTRO_PRICE_TAG,
            )
        )
        assertEquals(1, loadedPlans.size)
        val plan = loadedPlans.first()
        assertEquals(listOf(PlanCycle.MONTHLY), plan.cycles.map { it.cycle })
    }
}
