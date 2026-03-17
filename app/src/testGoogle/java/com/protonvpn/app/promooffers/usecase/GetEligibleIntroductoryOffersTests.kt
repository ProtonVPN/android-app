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

package com.protonvpn.app.promooffers.usecase

import com.protonvpn.android.promooffers.usecase.GetEligibleIntroductoryOffers
import com.protonvpn.android.ui.planupgrade.IsInAppUpgradeAllowedUseCase
import com.protonvpn.android.ui.planupgrade.usecase.LoadGoogleSubscriptionPlans
import com.protonvpn.test.shared.InMemoryObjectStore
import com.protonvpn.test.shared.TestVpnUser
import com.protonvpn.test.shared.createDynamicPlan
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import me.proton.core.payment.domain.usecase.PaymentProvider
import me.proton.core.plan.domain.entity.DynamicPlanPrice
import me.proton.core.plan.presentation.entity.PlanCycle
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class GetEligibleIntroductoryOffersTests {

    @MockK
    private lateinit var mockInAppUpgradeAllowed: IsInAppUpgradeAllowedUseCase

    private lateinit var spyLoadGooglePlans: LoadGoogleSubscriptionPlans
    private lateinit var testScope: TestScope

    private val plans = listOf(
        createDynamicPlan(
            "vpn2022",
            mapOf(PlanCycle.MONTHLY to mapOf(plan("PLN", 10_00)))
        ),
        createDynamicPlan(
            "bundle2022",
            mapOf(PlanCycle.MONTHLY to mapOf(plan("PLN", 20_00)))
        )
    )

    // Monthly intro prices are all set to 500.
    private val offerVpn2022 =
        GetEligibleIntroductoryOffers.Offer("vpn2022", PlanCycle.MONTHLY, "PLN", 500)
    private val offerBundle2022 =
        GetEligibleIntroductoryOffers.Offer("bundle2022", PlanCycle.MONTHLY, "PLN", 500)

    private lateinit var getOffers: GetEligibleIntroductoryOffers

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        testScope = TestScope()
        coEvery { mockInAppUpgradeAllowed.invoke() } returns true

        val adjustedPrices = FakeDynamicPlansAdjustedPrices(
            rawDynamicPlans = { plans }
        ).apply {
            currency = "PLN"
            introPrices = mapOf(PlanCycle.MONTHLY to 500)
        }
        val loadGoogleSubscriptionPlans = LoadGoogleSubscriptionPlans(
            vpnUserFlow = flowOf(TestVpnUser.create(maxTier = 0, subscribed = 0)),
            rawDynamicPlans = { plans },
            dynamicPlansAdjustedPrices = adjustedPrices::invoke,
            availablePaymentProviders = { setOf(PaymentProvider.GoogleInAppPurchase) },
            defaultCycles = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY),
            defaultPreselectedCycle = PlanCycle.YEARLY,
        )
        spyLoadGooglePlans = spyk(loadGoogleSubscriptionPlans)

        getOffers = GetEligibleIntroductoryOffers(
            spyLoadGooglePlans,
            mockInAppUpgradeAllowed,
            InMemoryObjectStore(),
            testScope::currentTime
        )
    }

    @Test
    fun `WHEN different plans are queried THEN they are requested from load`() = testScope.runTest {
        assertEquals(
            listOf(offerVpn2022),
            getOffers(listOf("vpn2022"))
        )
        assertEquals(
            listOf(offerBundle2022),
            getOffers(listOf("bundle2022"))
        )
        coVerify(exactly = 1) {
            spyLoadGooglePlans.invoke(listOf("vpn2022"))
            spyLoadGooglePlans.invoke(listOf("bundle2022"))
        }
        // From cache
        advanceTimeBy(1.days)
        assertEquals(
            listOf(offerVpn2022),
            getOffers(listOf("vpn2022"))
        )
        assertEquals(
            listOf(offerBundle2022),
            getOffers(listOf("bundle2022"))
        )
        assertEquals(
            listOf(offerVpn2022, offerBundle2022),
            getOffers(listOf("vpn2022", "bundle2022"))
        )
        coVerify(exactly = 1) {
            spyLoadGooglePlans.invoke(listOf("vpn2022"))
            spyLoadGooglePlans.invoke(listOf("bundle2022"))
        }
        coVerify(exactly = 0) {
            spyLoadGooglePlans.invoke(listOf("vpn2022", "bundle2022"))
        }
    }

    @Test
    fun `WHEN 2 days pass THEN data is loaded from Google again`() = testScope.runTest {
        getOffers(listOf("vpn2022"))
        advanceTimeBy(1.days)
        getOffers(listOf("bundle2022"))
        advanceTimeBy(1.5.days)
        coVerify(exactly = 1) {
            spyLoadGooglePlans.invoke(listOf("vpn2022"))
            spyLoadGooglePlans.invoke(listOf("bundle2022"))
        }

        assertEquals(
            listOf(offerVpn2022, offerBundle2022),
            getOffers(listOf("vpn2022", "bundle2022"))
        )
        coVerify(exactly = 1) {
            spyLoadGooglePlans.invoke(listOf("vpn2022"))
            spyLoadGooglePlans.invoke(listOf("bundle2022"))
            spyLoadGooglePlans.invoke(listOf("vpn2022", "bundle2022"))
        }
    }
}

private fun plan(currency: String, price: Int): Pair<String, DynamicPlanPrice> =
    currency to DynamicPlanPrice("", currency, price)