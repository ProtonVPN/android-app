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
import com.protonvpn.android.ui.planupgrade.usecase.CycleInfo
import com.protonvpn.android.ui.planupgrade.usecase.LoadGoogleSubscriptionPlans
import com.protonvpn.android.utils.Constants
import com.protonvpn.test.shared.TestLoadGoogleOffers
import com.protonvpn.test.shared.TestVpnUser
import com.protonvpn.test.shared.createDynamicPlan
import com.protonvpn.test.shared.createGiapOffer
import com.protonvpn.test.shared.toProductId
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.proton.core.domain.entity.AppStore
import me.proton.core.payment.domain.usecase.PaymentProvider
import me.proton.core.plan.domain.entity.DynamicPlan
import me.proton.core.plan.domain.entity.DynamicPlanPrice
import me.proton.core.plan.presentation.entity.PlanCycle
import me.proton.core.util.kotlin.filterNotNullValues
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

private val DEFAULT_CYCLES = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY)
private val PRESELECTED_CYCLE = PlanCycle.YEARLY
private val INTRO_TAG = listOf(IapConstants.INTRO_PRICE_TAG)

@OptIn(ExperimentalCoroutinesApi::class)
class LoadGoogleSubscriptionPlansTests {

    private lateinit var testScope: TestScope

    private lateinit var currentVpnUser: MutableStateFlow<VpnUser?>
    private lateinit var dynamicPlans: List<DynamicPlan>
    private lateinit var availablePaymentProviders: Set<PaymentProvider>
    private lateinit var testLoadGoogleOffers: TestLoadGoogleOffers
    private lateinit var loadGoogleSubscriptionPlans: LoadGoogleSubscriptionPlans

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        currentVpnUser = MutableStateFlow(createVpnUser(subscribed = 0))
        availablePaymentProviders = setOf(PaymentProvider.GoogleInAppPurchase)
        // Put all combinations in the default offers.
        val allCycles = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY, PlanCycle.TWO_YEARS)
        testLoadGoogleOffers = TestLoadGoogleOffers(
            allCycles.map { planCycle ->
                createGiapOffer(Constants.CURRENT_PLUS_PLAN, planCycle, listOf(99, 10_00))
            } + allCycles.map { planCycle ->
                createGiapOffer(Constants.CURRENT_BUNDLE_PLAN, planCycle, listOf(2_99, 200_00))
            }
        )
        setDynamicPlans(
            createDynamicPlan(Constants.CURRENT_PLUS_PLAN, DEFAULT_CYCLES)
        )
        loadGoogleSubscriptionPlans = LoadGoogleSubscriptionPlans(
            vpnUserFlow = currentVpnUser,
            rawDynamicPlans = { dynamicPlans },
            loadGoogleOffers = testLoadGoogleOffers::invoke,
            availablePaymentProviders = { availablePaymentProviders },
            defaultCycles = DEFAULT_CYCLES,
            defaultPreselectedCycle = PRESELECTED_CYCLE
        )
    }

    @Test
    fun `load default plans and cycles if available`() = testScope.runTest {
        val plans = loadGoogleSubscriptionPlans(IapConstants.INTRO_PRICE_TAG, listOf(Constants.CURRENT_PLUS_PLAN))
        assertEquals(1, plans.size)
        val plan = plans.first()
        assertEquals(Constants.CURRENT_PLUS_PLAN, plan.name)
        assertEquals(DEFAULT_CYCLES, plan.cycles.map { it.cycle })
        assertEquals(DEFAULT_CYCLES.map { it.toProductId(AppStore.GooglePlay, plan.name) }, plan.cycles.map { it.productId })
        assertEquals(PRESELECTED_CYCLE, plan.preselectedCycle)
    }

    @Test
    fun `don't load plans if other payment methods available`() = testScope.runTest {
        availablePaymentProviders = setOf(PaymentProvider.CardPayment, PaymentProvider.GoogleInAppPurchase)
        assertEquals(
            emptyList(),
            loadGoogleSubscriptionPlans(IapConstants.INTRO_PRICE_TAG, listOf(Constants.CURRENT_PLUS_PLAN))
        )
    }

    @Test
    fun `don't load plan if user has subscription`() = testScope.runTest {
        currentVpnUser.value = createVpnUser(subscribed = VpnUser.VPN_SUBSCRIBED_FLAG)
        assertEquals(
            emptyList(),
            loadGoogleSubscriptionPlans(IapConstants.INTRO_PRICE_TAG, listOf(Constants.CURRENT_PLUS_PLAN))
        )
    }

    @Test
    fun `don't load other plans`() = testScope.runTest {
        setDynamicPlans(createDynamicPlan("other-plan", DEFAULT_CYCLES))
        assertEquals(
            emptyList(),
            loadGoogleSubscriptionPlans(IapConstants.INTRO_PRICE_TAG, listOf(Constants.CURRENT_PLUS_PLAN))
        )
    }

    @Test
    fun `don't load plan if no default cycle is available`() = testScope.runTest {
        setDynamicPlans(
            createDynamicPlan(Constants.CURRENT_PLUS_PLAN, listOf(PlanCycle.TWO_YEARS)),
            createDynamicPlan(Constants.CURRENT_BUNDLE_PLAN, DEFAULT_CYCLES)
        )
        val loadedPlans = loadGoogleSubscriptionPlans(
            IapConstants.INTRO_PRICE_TAG,
            listOf(Constants.CURRENT_PLUS_PLAN, Constants.CURRENT_BUNDLE_PLAN)
        )
        // Plus plan ignored.
        assertEquals(listOf(Constants.CURRENT_BUNDLE_PLAN), loadedPlans.map { it.name })
    }

    @Test
    fun `load plans even if non-default cycles are missing`() = testScope.runTest {
        setDynamicPlans(
            createDynamicPlan(Constants.CURRENT_PLUS_PLAN, listOf(PlanCycle.MONTHLY, PlanCycle.TWO_YEARS))
        )
        val loadedPlans = loadGoogleSubscriptionPlans(
            IapConstants.INTRO_PRICE_TAG,
            listOf(Constants.CURRENT_PLUS_PLAN, Constants.CURRENT_BUNDLE_PLAN)
        )
        assertEquals(listOf(Constants.CURRENT_PLUS_PLAN), loadedPlans.map { it.name })
    }

    @Test
    fun `don't load plans when all prices are missing`() = testScope.runTest {
        dynamicPlans = listOf(
            createDynamicPlan(Constants.CURRENT_PLUS_PLAN, listOf(PlanCycle.MONTHLY, PlanCycle.TWO_YEARS))
        )
        testLoadGoogleOffers.offers = emptyList()
        val loadedPlans = loadGoogleSubscriptionPlans(
            IapConstants.INTRO_PRICE_TAG,
            listOf(Constants.CURRENT_PLUS_PLAN, Constants.CURRENT_BUNDLE_PLAN)
        )
        assertEquals(emptyList(), loadedPlans.map { it.name })
    }

    @Test
    fun `pick offers with offer tag and if missing, pick by fallback tag`() = testScope.runTest {
        dynamicPlans = listOf(
            createDynamicPlan(Constants.CURRENT_PLUS_PLAN, listOf(PlanCycle.MONTHLY))
        )
        val offer = createGiapOffer(
            productName = Constants.CURRENT_PLUS_PLAN,
            cycle = PlanCycle.MONTHLY,
            pricingPhases = listOf(1_00)
        )
        testLoadGoogleOffers.offers = listOf(
            offer.copy(tags = listOf("unknown")),
            offer.copy(tags = listOf("unknown", IapConstants.BASE_PRICE_TAG), pricingPhasesCents = listOf(5_00)),
            offer.copy(tags = listOf("unknown", IapConstants.INTRO_PRICE_TAG), pricingPhasesCents = listOf(99)),
        )
        val loadedPlans = loadGoogleSubscriptionPlans(
            IapConstants.INTRO_PRICE_TAG,
            listOf(Constants.CURRENT_PLUS_PLAN)
        )
        assertEquals(1, loadedPlans.size)
        val loadedPlan = loadedPlans.first()
        assertEquals(
            listOf(CycleInfo(PlanCycle.MONTHLY, "productId-GooglePlay-vpn2022-1", "token_1", 99, 99)),
            loadedPlan.cycles
        )
    }

    @Test
    fun `throw PartialPrices when prices for some cycles are missing`() = testScope.runTest {
        dynamicPlans = listOf(
            createDynamicPlan(Constants.CURRENT_PLUS_PLAN, listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY))
        )
        testLoadGoogleOffers.offers = listOf(
            createGiapOffer(Constants.CURRENT_PLUS_PLAN, PlanCycle.MONTHLY, listOf(1_00))
        )
        try {
            val loadedPlans = loadGoogleSubscriptionPlans(
                IapConstants.INTRO_PRICE_TAG,
                listOf(Constants.CURRENT_PLUS_PLAN)
            )
            assertTrue("Plans loaded: ${loadedPlans.map { it.name } }", false)
        } catch (e: LoadGoogleSubscriptionPlans.PartialPrices) {
            assertEquals(
                "Google prices available only for a subset of plans/cycles: [vpn2022_MONTHLY]; missing for: [vpn2022_YEARLY]",
                e.message
            )
        }
    }

    @Test
    fun `throw PartialPrices when prices for some plans are missing`() = testScope.runTest {
        dynamicPlans = listOf(
            createDynamicPlan(Constants.CURRENT_PLUS_PLAN, listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY)),
            createDynamicPlan(Constants.CURRENT_BUNDLE_PLAN, listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY))
        )
        testLoadGoogleOffers.offers = listOf(
            createGiapOffer(Constants.CURRENT_PLUS_PLAN, PlanCycle.MONTHLY, listOf(1_00)),
            createGiapOffer(Constants.CURRENT_PLUS_PLAN, PlanCycle.YEARLY, listOf(20_00))
        )
        try {
            val loadedPlans = loadGoogleSubscriptionPlans(
                IapConstants.INTRO_PRICE_TAG,
                listOf(Constants.CURRENT_PLUS_PLAN, Constants.CURRENT_BUNDLE_PLAN)
            )
            assertTrue("Plans loaded: ${loadedPlans.map { it.name } }", false)
        } catch (e: LoadGoogleSubscriptionPlans.PartialPrices) {
            assertEquals(
                "Google prices available only for a subset of plans/cycles: [vpn2022_MONTHLY, vpn2022_YEARLY]; missing for: [bundle2022_MONTHLY, bundle2022_YEARLY]",
                e.message
            )
        }
    }

    @Test
    fun `fallback to available cycles`() = testScope.runTest {
        setDynamicPlans(
            createDynamicPlan(
                Constants.CURRENT_PLUS_PLAN,
                listOf(PlanCycle.TWO_YEARS, PlanCycle.MONTHLY)
            )
        )
        val loadedPlans = loadGoogleSubscriptionPlans(
            IapConstants.INTRO_PRICE_TAG,
            listOf(Constants.CURRENT_PLUS_PLAN)
        )
        assertEquals(1, loadedPlans.size)
        val plan = loadedPlans.first()
        assertEquals(listOf(PlanCycle.MONTHLY), plan.cycles.map { it.cycle })
        assertEquals(PlanCycle.MONTHLY, plan.preselectedCycle)
    }

    private fun setDynamicPlans(vararg newPlans: DynamicPlan) {
        dynamicPlans = newPlans.toList()
    }

    @Test
    fun `WHEN price is missing THEN plan is filtered out`() = testScope.runTest {
        setDynamicPlans(
            createDynamicPlan(
                name = Constants.CURRENT_PLUS_PLAN,
                cycles = listOf(PlanCycle.TWO_YEARS, PlanCycle.MONTHLY)
            ),
            createDynamicPlan(
                name = Constants.CURRENT_BUNDLE_PLAN,
                cycles = listOf(PlanCycle.TWO_YEARS, PlanCycle.MONTHLY),
                prices = listOf(200_00, null)
            )
        )
        val loadedPlans = loadGoogleSubscriptionPlans(
            IapConstants.INTRO_PRICE_TAG,
            listOf(Constants.CURRENT_PLUS_PLAN)
        )
        assertEquals(1, loadedPlans.size)
        assertEquals(Constants.CURRENT_PLUS_PLAN, loadedPlans.first().name)
    }
}

private fun createVpnUser(subscribed: Int) = TestVpnUser.create(subscribed = subscribed)

private fun createDynamicPlan(
    name: String,
    cycles: List<PlanCycle>,
    prices: List<Int?>? = null,
    appStore: AppStore = AppStore.GooglePlay
): DynamicPlan {
    val prices: List<Int?> = prices ?: cycles.map {
        when(it) {
            PlanCycle.YEARLY -> 50_00
            PlanCycle.MONTHLY -> 1_00
            PlanCycle.FREE -> 0
            PlanCycle.TWO_YEARS -> 100_00
            PlanCycle.OTHER -> throw IllegalArgumentException()
        }
    }
    return createDynamicPlan(
        name = name,
        prices = cycles.zip(prices).associate { (cycle, price) ->
            cycle to price?.let { createDynamicPlanPrice(cycle, "EUR", it) }
        }.filterNotNullValues(),
        appStore = appStore
    )
}

private fun createDynamicPlanPrice(cycle: PlanCycle, currency: String, price: Int): Map<String, DynamicPlanPrice> =
    mapOf(currency to DynamicPlanPrice("${currency}_$cycle", currency, price, null))
