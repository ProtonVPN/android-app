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
import com.protonvpn.android.ui.planupgrade.usecase.LoadGoogleSubscriptionPlans
import com.protonvpn.android.utils.Constants
import com.protonvpn.test.shared.TestVpnUser
import com.protonvpn.test.shared.createDynamicPlan
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
import me.proton.core.plan.presentation.entity.PlanCycle
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

private val DEFAULT_CYCLES = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY)
private val PRESELECTED_CYCLE = PlanCycle.YEARLY

@OptIn(ExperimentalCoroutinesApi::class)
class LoadGoogleSubscriptionPlansTests {

    private lateinit var testScope: TestScope

    private lateinit var currentVpnUser: MutableStateFlow<VpnUser?>
    private lateinit var dynamicPlans: List<DynamicPlan>
    private lateinit var dynamicPlansAdjustedPrices: List<DynamicPlan>
    private lateinit var availablePaymentProviders: Set<PaymentProvider>
    private lateinit var loadGoogleSubscriptionPlans: LoadGoogleSubscriptionPlans

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        currentVpnUser = MutableStateFlow(createVpnUser(subscribed = 0))
        availablePaymentProviders = setOf(PaymentProvider.GoogleInAppPurchase)
        setDynamicPlans(
            createDynamicPlan(Constants.CURRENT_PLUS_PLAN, DEFAULT_CYCLES)
        )
        loadGoogleSubscriptionPlans = LoadGoogleSubscriptionPlans(
            vpnUserFlow = currentVpnUser,
            rawDynamicPlans = { dynamicPlans },
            dynamicPlansAdjustedPrices = { dynamicPlansAdjustedPrices },
            availablePaymentProviders = { availablePaymentProviders },
            defaultCycles = DEFAULT_CYCLES,
            defaultPreselectedCycle = PRESELECTED_CYCLE
        )
    }

    @Test
    fun `load default plans and cycles if available`() = testScope.runTest {
        val plans = loadGoogleSubscriptionPlans(listOf(Constants.CURRENT_PLUS_PLAN))
        assertEquals(1, plans.size)
        val plan = plans.first()
        assertEquals(Constants.CURRENT_PLUS_PLAN, plan.name)
        assertEquals(DEFAULT_CYCLES, plan.cycles.map { it.cycle })
        assertEquals(DEFAULT_CYCLES.map { it.toProductId(AppStore.GooglePlay) }, plan.cycles.map { it.productId })
        assertEquals(PRESELECTED_CYCLE, plan.preselectedCycle)
    }

    @Test
    fun `don't load plans if other payment methods available`() = testScope.runTest {
        availablePaymentProviders = setOf(PaymentProvider.CardPayment, PaymentProvider.GoogleInAppPurchase)
        assertEquals(emptyList(), loadGoogleSubscriptionPlans(listOf(Constants.CURRENT_PLUS_PLAN)))
    }

    @Test
    fun `don't load plan if user has subscription`() = testScope.runTest {
        currentVpnUser.value = createVpnUser(subscribed = VpnUser.VPN_SUBSCRIBED_FLAG)
        assertEquals(emptyList(), loadGoogleSubscriptionPlans(listOf(Constants.CURRENT_PLUS_PLAN)))
    }

    @Test
    fun `don't load other plans`() = testScope.runTest {
        setDynamicPlans(createDynamicPlan("other-plan", DEFAULT_CYCLES))
        assertEquals(emptyList(), loadGoogleSubscriptionPlans(listOf(Constants.CURRENT_PLUS_PLAN)))
    }

    @Test
    fun `don't load plan if no default cycle is available`() = testScope.runTest {
        setDynamicPlans(
            createDynamicPlan(Constants.CURRENT_PLUS_PLAN, listOf(PlanCycle.TWO_YEARS)),
            createDynamicPlan(Constants.CURRENT_BUNDLE_PLAN, DEFAULT_CYCLES)
        )
        val loadedPlans = loadGoogleSubscriptionPlans(listOf(Constants.CURRENT_PLUS_PLAN, Constants.CURRENT_BUNDLE_PLAN))
        // Plus plan ignored.
        assertEquals(listOf(Constants.CURRENT_BUNDLE_PLAN), loadedPlans.map { it.name })
    }

    @Test
    fun `load plans even if non-default cycles are missing`() = testScope.runTest {
        dynamicPlans = listOf(
            createDynamicPlan(Constants.CURRENT_PLUS_PLAN, listOf(PlanCycle.MONTHLY, PlanCycle.TWO_YEARS))
        )
        dynamicPlansAdjustedPrices = listOf(
            createDynamicPlan(Constants.CURRENT_PLUS_PLAN, listOf(PlanCycle.MONTHLY))
        )
        val loadedPlans = loadGoogleSubscriptionPlans(listOf(Constants.CURRENT_PLUS_PLAN, Constants.CURRENT_BUNDLE_PLAN))
        assertEquals(listOf(Constants.CURRENT_PLUS_PLAN), loadedPlans.map { it.name })
    }

    @Test
    fun `don't load plans when all prices are missing`() = testScope.runTest {
        dynamicPlans = listOf(
            createDynamicPlan(Constants.CURRENT_PLUS_PLAN, listOf(PlanCycle.MONTHLY, PlanCycle.TWO_YEARS))
        )
        dynamicPlansAdjustedPrices = emptyList()
        val loadedPlans = loadGoogleSubscriptionPlans(listOf(Constants.CURRENT_PLUS_PLAN, Constants.CURRENT_BUNDLE_PLAN))
        assertEquals(emptyList(), loadedPlans.map { it.name })
    }

    @Test
    fun `throw PartialPrices when prices for some cycles are missing`() = testScope.runTest {
        dynamicPlans = listOf(
            createDynamicPlan(Constants.CURRENT_PLUS_PLAN, listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY))
        )
        dynamicPlansAdjustedPrices = listOf(
            createDynamicPlan(Constants.CURRENT_PLUS_PLAN, listOf(PlanCycle.MONTHLY))
        )
        try {
            val loadedPlans = loadGoogleSubscriptionPlans(listOf(Constants.CURRENT_PLUS_PLAN))
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
        dynamicPlansAdjustedPrices = listOf(
            createDynamicPlan(Constants.CURRENT_PLUS_PLAN, listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY))
        )
        try {
            val loadedPlans = loadGoogleSubscriptionPlans(
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
        val loadedPlans = loadGoogleSubscriptionPlans(listOf(Constants.CURRENT_PLUS_PLAN))
        assertEquals(1, loadedPlans.size)
        val plan = loadedPlans.first()
        assertEquals(listOf(PlanCycle.MONTHLY), plan.cycles.map { it.cycle })
        assertEquals(PlanCycle.MONTHLY, plan.preselectedCycle)
    }

    private fun setDynamicPlans(vararg newPlans: DynamicPlan) {
        dynamicPlans = newPlans.toList()
        dynamicPlansAdjustedPrices = newPlans.toList()
    }
}

private fun createVpnUser(subscribed: Int) = TestVpnUser.create(subscribed = subscribed)

private fun createDynamicPlan(
    name: String,
    cycles: List<PlanCycle>,
    appStore: AppStore = AppStore.GooglePlay
) = createDynamicPlan(
    name = name,
    prices = cycles.associateWith { emptyMap() },
    appStore = appStore
)
