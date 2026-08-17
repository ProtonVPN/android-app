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

import com.protonvpn.android.promooffers.usecase.GetEligibleOffers
import com.protonvpn.android.ui.planupgrade.IapConstants
import com.protonvpn.android.ui.planupgrade.IsInAppUpgradeAllowedUseCase
import com.protonvpn.android.ui.planupgrade.PlanCycle
import com.protonvpn.android.ui.planupgrade.usecase.LoadPlansConfig
import com.protonvpn.android.ui.planupgrade.usecase.LoadSubscriptionPlans
import com.protonvpn.test.shared.InMemoryObjectStore
import com.protonvpn.test.shared.createOffer
import com.protonvpn.test.shared.createOffersWithDiscount
import com.protonvpn.test.shared.createProduct
import com.protonvpn.test.shared.toProductId
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import me.proton.android.payment.product.fake.FakeGetProducts
import me.proton.core.domain.entity.AppStore
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class GetEligibleOffersTests {

    @MockK
    private lateinit var mockInAppUpgradeAllowed: IsInAppUpgradeAllowedUseCase

    private lateinit var spyLoadSubscriptionPlans: LoadSubscriptionPlans
    private lateinit var testScope: TestScope

    private val customTag = "custom-tag"
    // Monthly intro prices are all set to 500.
    private val offerVpn2022 = GetEligibleOffers.Offer(
        planName = "vpn2022",
        cycle = PlanCycle.MONTHLY,
        currency = "PLN",
        currentPriceCents = 500,
        offerTags = listOf(IapConstants.INTRO_PRICE_TAG)
    )
    private val offerBundle2022 = GetEligibleOffers.Offer(
        planName = "bundle2022",
        cycle = PlanCycle.MONTHLY,
        currency = "PLN",
        currentPriceCents = 500,
        offerTags = listOf(IapConstants.INTRO_PRICE_TAG)
    )
    private val offerVpn2022Custom = GetEligibleOffers.Offer(
        planName = "vpn2022",
        cycle = PlanCycle.MONTHLY,
        currency = "PLN",
        currentPriceCents = 100,
        offerTags = listOf(customTag)
    )

    private lateinit var getOffers: GetEligibleOffers

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        testScope = TestScope()
        coEvery { mockInAppUpgradeAllowed.invoke() } returns true

        val fakeProducts = listOf(
            createProduct(
                id = PlanCycle.MONTHLY.toProductId(AppStore.GooglePlay, "vpn2022"),
                planName = "vpn2022",
                offers = buildList {
                    addAll(createOffersWithDiscount(PlanCycle.MONTHLY, 500, 1000, "PLN"))
                    add(createOffer(PlanCycle.MONTHLY, listOf(100, 1000), "PLN", listOf(customTag)))
                },
            ),
            createProduct(
                id = PlanCycle.MONTHLY.toProductId(AppStore.GooglePlay, "bundle2022"),
                planName = "bundle2022",
                offers = createOffersWithDiscount(PlanCycle.MONTHLY, 500, 2000, "PLN"),
            ),
        )
        val fakeGetProducts = FakeGetProducts().apply { setProductsToReturn(fakeProducts) }
        val loadSubscriptionPlans = LoadSubscriptionPlans({ fakeGetProducts })
        spyLoadSubscriptionPlans = spyk(loadSubscriptionPlans)

        getOffers = GetEligibleOffers(
            spyLoadSubscriptionPlans,
            mockInAppUpgradeAllowed,
            InMemoryObjectStore(),
            testScope::currentTime
        )
    }

    @Test
    fun `WHEN different plans are queried THEN they are requested from load`() = testScope.runTest {
        val planCycles = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY)
        val loadConfigVpn2022 = LoadPlansConfig.WithOfferTagAndFilter(listOf("vpn2022"), planCycles, IapConstants.INTRO_PRICE_TAG)
        val loadConfigIntroPrice = LoadPlansConfig.WithOfferTag(IapConstants.INTRO_PRICE_TAG)
        assertEquals(
            listOf(offerVpn2022),
            getOffers(loadConfigVpn2022)
        )
        assertEquals(
            listOf(offerVpn2022, offerBundle2022),
            getOffers(loadConfigIntroPrice)
        )
        coVerify(exactly = 1) {
            spyLoadSubscriptionPlans.invoke(loadConfigVpn2022)
            spyLoadSubscriptionPlans.invoke(loadConfigIntroPrice)
        }
        // From cache
        advanceTimeBy(1.days)
        assertEquals(
            listOf(offerVpn2022),
            getOffers(loadConfigVpn2022)
        )
        assertEquals(
            listOf(offerVpn2022, offerBundle2022),
            getOffers(loadConfigIntroPrice)
        )

        val loadConfigBundle2022 = LoadPlansConfig.WithOfferTagAndFilter(listOf("bundle2022"), planCycles, IapConstants.INTRO_PRICE_TAG)
        assertEquals(
            listOf(offerBundle2022),
            getOffers(loadConfigBundle2022)
        )
        coVerify(exactly = 1) {
            spyLoadSubscriptionPlans.invoke(loadConfigBundle2022)
        }

        // Request vpn2022 with a different tag, requires loading plans again.
        val loadConfigVpn2022Tag = LoadPlansConfig.WithOfferTagAndFilter(listOf("vpn2022"), planCycles, "custom-tag")
        assertEquals(
            listOf(offerVpn2022Custom),
            getOffers(loadConfigVpn2022Tag)
        )
        coVerify(exactly = 1) {
            spyLoadSubscriptionPlans.invoke(loadConfigVpn2022Tag)
        }

        // Verify no extra calls.
        coVerify(exactly = 1) {
            listOf(loadConfigVpn2022, loadConfigVpn2022Tag, loadConfigBundle2022, loadConfigIntroPrice)
                .forEach { spyLoadSubscriptionPlans.invoke(it) }
        }
    }

    @Test
    fun `WHEN 2 days pass THEN data is loaded from Google again`() = testScope.runTest {
        val planCycles = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY)
        val loadConfigVpn2022 = LoadPlansConfig.WithOfferTagAndFilter(listOf("vpn2022"), planCycles, IapConstants.INTRO_PRICE_TAG)
        val loadConfigIntroPrice = LoadPlansConfig.WithOfferTag(IapConstants.INTRO_PRICE_TAG)
        getOffers(loadConfigVpn2022)
        advanceTimeBy(1.days)
        getOffers(loadConfigIntroPrice)
        coVerify(exactly = 1) {
            spyLoadSubscriptionPlans.invoke(loadConfigVpn2022)
            spyLoadSubscriptionPlans.invoke(loadConfigIntroPrice)
        }

        advanceTimeBy(1.5.days)
        // Load.
        assertEquals(
            listOf(offerVpn2022),
            getOffers(loadConfigVpn2022)
        )
        // From cache.
        assertEquals(
            listOf(offerVpn2022, offerBundle2022),
            getOffers(loadConfigIntroPrice)
        )

        coVerify(exactly = 2) {
            spyLoadSubscriptionPlans.invoke(loadConfigVpn2022)
        }
        coVerify(exactly = 1) {
            spyLoadSubscriptionPlans.invoke(loadConfigIntroPrice)
        }
    }
}
