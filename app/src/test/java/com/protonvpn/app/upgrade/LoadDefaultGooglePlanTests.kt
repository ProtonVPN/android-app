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
import com.protonvpn.android.ui.planupgrade.usecase.LoadDefaultGooglePlan
import com.protonvpn.test.shared.TestVpnUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.proton.core.domain.entity.AppStore
import me.proton.core.payment.domain.usecase.PaymentProvider
import me.proton.core.plan.domain.entity.DynamicPlan
import me.proton.core.plan.domain.entity.DynamicPlanInstance
import me.proton.core.plan.domain.entity.DynamicPlanState
import me.proton.core.plan.domain.entity.DynamicPlanVendor
import me.proton.core.plan.presentation.entity.PlanCycle
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val DEFAULT_CYCLES = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY)
private val PRESELECTED_CYCLE = PlanCycle.YEARLY
private fun PlanCycle.toProductId(appStore: AppStore) = "productId-$appStore-$cycleDurationMonths"

@OptIn(ExperimentalCoroutinesApi::class)
class LoadDefaultGooglePlanTests {

    private lateinit var testScope: TestScope

    private lateinit var currentVpnUser: MutableStateFlow<VpnUser?>
    private lateinit var dynamicPlans: List<DynamicPlan>
    private lateinit var availablePaymentProviders: Set<PaymentProvider>
    private lateinit var loadDefaultGooglePlan: LoadDefaultGooglePlan

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        currentVpnUser = MutableStateFlow(createVpnUser(subscribed = 0))
        availablePaymentProviders = setOf(PaymentProvider.GoogleInAppPurchase)
        dynamicPlans = listOf(
            createDynamicPlan(LoadDefaultGooglePlan.DEFAULT_PLAN_NAME_VPN)
        )
        loadDefaultGooglePlan = LoadDefaultGooglePlan(
            currentVpnUser,
            { dynamicPlans },
            { availablePaymentProviders },
            DEFAULT_CYCLES,
            PRESELECTED_CYCLE
        )
    }

    @Test
    fun `load default plans and cycles if available`() = testScope.runTest {
        val plan = loadDefaultGooglePlan()
        assertEquals(LoadDefaultGooglePlan.DEFAULT_PLAN_NAME_VPN, plan?.name)
        assertEquals(DEFAULT_CYCLES, plan?.cycles?.map { it.cycle })
        assertEquals(DEFAULT_CYCLES.map { it.toProductId(AppStore.GooglePlay) }, plan?.cycles?.map { it.productId })
        assertEquals(PRESELECTED_CYCLE, plan?.preselectedCycle)
    }

    @Test
    fun `don't load plans if other payment methods available`() = testScope.runTest {
        availablePaymentProviders = setOf(PaymentProvider.CardPayment, PaymentProvider.GoogleInAppPurchase)
        assertNull(loadDefaultGooglePlan())
    }

    @Test
    fun `don't load plan if user has subscription`() = testScope.runTest {
        currentVpnUser.value = createVpnUser(subscribed = VpnUser.VPN_SUBSCRIBED_FLAG)
        assertNull(loadDefaultGooglePlan())
    }

    @Test
    fun `don't load other plans`() = testScope.runTest {
        dynamicPlans = listOf(createDynamicPlan("other-plan"))
        assertNull(loadDefaultGooglePlan())
    }

    @Test
    fun `don't load plan if no default cycle is available`() = testScope.runTest {
        dynamicPlans = listOf(
            createDynamicPlan(LoadDefaultGooglePlan.DEFAULT_PLAN_NAME_VPN, listOf(
                PlanCycle.TWO_YEARS to AppStore.GooglePlay,
            ))
        )
        assertNull(loadDefaultGooglePlan())
    }

    @Test
    fun `fallback to available cycles`() = testScope.runTest {
        dynamicPlans = listOf(
            createDynamicPlan(LoadDefaultGooglePlan.DEFAULT_PLAN_NAME_VPN, listOf(
                PlanCycle.TWO_YEARS to AppStore.GooglePlay,
                PlanCycle.MONTHLY to AppStore.GooglePlay,
            ))
        )
        assertEquals(listOf(PlanCycle.MONTHLY), loadDefaultGooglePlan()?.cycles?.map { it.cycle })
        assertEquals(PlanCycle.MONTHLY, loadDefaultGooglePlan()?.preselectedCycle)
    }
}

private fun createVpnUser(subscribed: Int) = TestVpnUser.create(subscribed = subscribed)

fun createDynamicPlan(
    name: String,
    instances: List<Pair<PlanCycle, AppStore>> = DEFAULT_CYCLES.map { it to AppStore.GooglePlay }
) = DynamicPlan(
    name,
    0,
    DynamicPlanState.Available,
    "$name title",
    null,
    instances = instances.associate { (cycle, appStore) ->
        cycle.cycleDurationMonths to createDynamicPlanInstance(cycle, appStore)
    }
)

private fun createDynamicPlanInstance(cycle: PlanCycle, appStore: AppStore) = DynamicPlanInstance(
    cycle.cycleDurationMonths, "", Instant.MAX, mapOf(),
    mapOf(appStore to DynamicPlanVendor(cycle.toProductId(appStore), ""))
)