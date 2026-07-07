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

import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.ui.planupgrade.IapConstants
import com.protonvpn.android.ui.planupgrade.PlanCycle
import com.protonvpn.android.ui.planupgrade.usecase.CycleInfo
import com.protonvpn.android.ui.planupgrade.usecase.LoadSubscriptionPlans
import com.protonvpn.android.utils.Constants
import com.protonvpn.test.shared.TestVpnUser
import com.protonvpn.test.shared.createOffer
import com.protonvpn.test.shared.createOffersWithDiscount
import com.protonvpn.test.shared.createProduct
import com.protonvpn.test.shared.toProductId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.proton.android.payment.product.fake.FakeGetProducts
import me.proton.core.domain.entity.AppStore
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

private val DEFAULT_CYCLES = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY)
private val PRESELECTED_CYCLE = PlanCycle.YEARLY

@OptIn(ExperimentalCoroutinesApi::class)
class LoadSubscriptionPlansTests {

    private lateinit var testScope: TestScope
    private lateinit var testGetProducts: FakeGetProducts

    private lateinit var currentVpnUser: MutableStateFlow<VpnUser?>
    private lateinit var loadSubscriptionPlans: LoadSubscriptionPlans

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        currentVpnUser = MutableStateFlow(createVpnUser(subscribed = 0))
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
            vpnUserFlow = currentVpnUser,
            getProductsLazy = { testGetProducts },
            defaultCycles = DEFAULT_CYCLES,
            defaultPreselectedCycle = PRESELECTED_CYCLE
        )
    }

    @Test
    fun `load default plans and cycles if available`() = testScope.runTest {
        val plans = loadSubscriptionPlans(
            planNames = listOf(Constants.CURRENT_PLUS_PLAN),
            discountOfferTag = IapConstants.INTRO_PRICE_TAG,
        )
        assertEquals(1, plans.size)
        val plan = plans.first()
        assertEquals(Constants.CURRENT_PLUS_PLAN, plan.name)
        assertEquals(DEFAULT_CYCLES, plan.cycles.map { it.cycle })
        assertEquals(
            DEFAULT_CYCLES.map { it.toProductId(AppStore.GooglePlay, plan.name) },
            plan.cycles.map { it.productId })
        assertEquals(PRESELECTED_CYCLE, plan.preselectedCycle)
    }

    @Test
    fun `don't load plan if user has subscription`() = testScope.runTest {
        currentVpnUser.value = createVpnUser(subscribed = VpnUser.VPN_SUBSCRIBED_FLAG)
        assertEquals(
            emptyList(),
            loadSubscriptionPlans(
                planNames = listOf(Constants.CURRENT_PLUS_PLAN),
                discountOfferTag = IapConstants.INTRO_PRICE_TAG,
            )
        )
    }

    @Test
    fun `don't load other plans`() = testScope.runTest {
        val offers = createOffersWithDiscount(PlanCycle.MONTHLY, 99, 10_00)
        val otherProduct = createProduct("other", "other_plan", offers)
        testGetProducts.setProductsToReturn(listOf(otherProduct))
        assertEquals(
            emptyList(),
            loadSubscriptionPlans(
                planNames = listOf(Constants.CURRENT_PLUS_PLAN),
                discountOfferTag = IapConstants.INTRO_PRICE_TAG,
            )
        )
    }

    @Test
    fun `pick offers with offer tag and if missing, use the base plan`() = testScope.runTest {
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
            planNames = listOf(Constants.CURRENT_PLUS_PLAN),
            discountOfferTag = IapConstants.INTRO_PRICE_TAG,
        )
        assertEquals(1, loadedIntroPlans.size)
        val loadedIntroPlan = loadedIntroPlans.first()
        assertEquals(
            listOf(CycleInfo(PlanCycle.MONTHLY, "productId", "token_intro", 99, 5_00)),
            loadedIntroPlan.cycles
        )

        val loadedBasePlans = loadSubscriptionPlans(
            planNames = listOf(Constants.CURRENT_PLUS_PLAN),
            discountOfferTag = "unknown_tag",
        )
        assertEquals(1, loadedIntroPlans.size)
        val loadedBasePlan = loadedBasePlans.first()
        assertEquals(
            listOf(CycleInfo(PlanCycle.MONTHLY, "productId", "token_base", 5_00, 5_00)),
            loadedBasePlan.cycles
        )
    }

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
            planNames = listOf(Constants.CURRENT_PLUS_PLAN),
            discountOfferTag = IapConstants.INTRO_PRICE_TAG,
        )
        assertEquals(1, loadedPlans.size)
        val plan = loadedPlans.first()
        assertEquals(listOf(PlanCycle.MONTHLY), plan.cycles.map { it.cycle })
        assertEquals(PlanCycle.MONTHLY, plan.preselectedCycle)
    }
}

private fun createVpnUser(subscribed: Int) = TestVpnUser.create(subscribed = subscribed)
