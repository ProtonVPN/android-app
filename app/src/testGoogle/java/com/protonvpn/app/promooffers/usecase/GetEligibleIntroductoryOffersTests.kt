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
import com.protonvpn.android.ui.planupgrade.IapConstants
import com.protonvpn.android.ui.planupgrade.IsInAppUpgradeAllowedUseCase
import com.protonvpn.android.ui.planupgrade.PlanCycle
import com.protonvpn.android.ui.planupgrade.usecase.LoadSubscriptionPlans
import com.protonvpn.test.shared.InMemoryObjectStore
import com.protonvpn.test.shared.TestVpnUser
import com.protonvpn.test.shared.createOffersWithDiscount
import com.protonvpn.test.shared.createProduct
import com.protonvpn.test.shared.toProductId
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
import me.proton.android.payment.product.fake.FakeGetProducts
import me.proton.core.domain.entity.AppStore
import me.proton.core.plan.domain.entity.DynamicPlanPrice
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class GetEligibleIntroductoryOffersTests {

    @MockK
    private lateinit var mockInAppUpgradeAllowed: IsInAppUpgradeAllowedUseCase

    private lateinit var spyLoadSubscriptionPlans: LoadSubscriptionPlans
    private lateinit var testScope: TestScope

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

        val introTag = listOf(IapConstants.INTRO_PRICE_TAG)
        val fakeProducts = listOf(
            createProduct(
                id = PlanCycle.MONTHLY.toProductId(AppStore.GooglePlay, "vpn2022"),
                planName = "vpn2022",
                offers = createOffersWithDiscount(PlanCycle.MONTHLY, 500, 1000, "PLN")
            ),
            createProduct(
                id = PlanCycle.MONTHLY.toProductId(AppStore.GooglePlay, "bundle2022"),
                planName = "bundle2022",
                offers = createOffersWithDiscount(PlanCycle.MONTHLY, 500, 2000, "PLN"),
            )
        )
        val fakeGetProducts = FakeGetProducts().apply { setProductsToReturn(fakeProducts) }
        val loadSubscriptionPlans = LoadSubscriptionPlans(
            vpnUserFlow = flowOf(TestVpnUser.create(maxTier = 0, subscribed = 0)),
            getProductsLazy = { fakeGetProducts },
            defaultCycles = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY),
            defaultPreselectedCycle = PlanCycle.YEARLY,
        )
        spyLoadSubscriptionPlans = spyk(loadSubscriptionPlans)

        getOffers = GetEligibleIntroductoryOffers(
            spyLoadSubscriptionPlans,
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
            spyLoadSubscriptionPlans.invoke(listOf("vpn2022"), IapConstants.INTRO_PRICE_TAG)
            spyLoadSubscriptionPlans.invoke(listOf("bundle2022"), IapConstants.INTRO_PRICE_TAG)
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
            spyLoadSubscriptionPlans.invoke(listOf("vpn2022"), IapConstants.INTRO_PRICE_TAG)
            spyLoadSubscriptionPlans.invoke(listOf("bundle2022"), IapConstants.INTRO_PRICE_TAG)
        }
        coVerify(exactly = 0) {
            spyLoadSubscriptionPlans.invoke(listOf("vpn2022", "bundle2022"), IapConstants.INTRO_PRICE_TAG)
        }
    }

    @Test
    fun `WHEN 2 days pass THEN data is loaded from Google again`() = testScope.runTest {
        getOffers(listOf("vpn2022"))
        advanceTimeBy(1.days)
        getOffers(listOf("bundle2022"))
        advanceTimeBy(1.5.days)
        coVerify(exactly = 1) {
            spyLoadSubscriptionPlans.invoke(listOf("vpn2022"), IapConstants.INTRO_PRICE_TAG)
            spyLoadSubscriptionPlans.invoke(listOf("bundle2022"), IapConstants.INTRO_PRICE_TAG)
        }

        assertEquals(
            listOf(offerVpn2022, offerBundle2022),
            getOffers(listOf("vpn2022", "bundle2022"))
        )
        coVerify(exactly = 1) {
            spyLoadSubscriptionPlans.invoke(listOf("vpn2022"), IapConstants.INTRO_PRICE_TAG)
            spyLoadSubscriptionPlans.invoke(listOf("bundle2022"), IapConstants.INTRO_PRICE_TAG)
            spyLoadSubscriptionPlans.invoke(listOf("vpn2022", "bundle2022"), IapConstants.INTRO_PRICE_TAG)
        }
    }
}

private fun plan(currency: String, price: Int): Pair<String, DynamicPlanPrice> =
    currency to DynamicPlanPrice("", currency, price)
