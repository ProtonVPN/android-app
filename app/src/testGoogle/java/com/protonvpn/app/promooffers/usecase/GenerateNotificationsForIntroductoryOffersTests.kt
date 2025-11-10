/*
 * Copyright (c) 2025. Proton AG
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

import com.protonvpn.android.appconfig.ApiNotification
import com.protonvpn.android.appconfig.ApiNotificationTypes
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.ui.planupgrade.IsInAppUpgradeAllowedUseCase
import com.protonvpn.android.ui.planupgrade.usecase.LoadGoogleSubscriptionPlans
import com.protonvpn.android.ui.promooffers.usecase.FakeIsIapClientSidePromoFeatureFlagEnabled
import com.protonvpn.android.ui.promooffers.usecase.GenerateNotificationsForIntroductoryOffers
import com.protonvpn.android.ui.promooffers.usecase.GetEligibleIntroductoryOffers
import com.protonvpn.mocks.TestDefaultLocaleProvider
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestVpnUser
import com.protonvpn.test.shared.createDynamicPlan
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import me.proton.core.domain.entity.UserId
import me.proton.core.payment.domain.usecase.PaymentProvider
import me.proton.core.plan.domain.entity.DynamicPlan
import me.proton.core.plan.domain.entity.DynamicPlanPrice
import me.proton.core.plan.presentation.entity.PlanCycle
import me.proton.core.util.kotlin.equalsNoCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.util.Locale
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class GenerateNotificationsForIntroductoryOffersTests {

    @MockK
    private lateinit var mockInAppUpgradeAllowed: IsInAppUpgradeAllowedUseCase

    private lateinit var featureFlagFlow: MutableStateFlow<Boolean>
    private lateinit var testLocaleProvider: TestDefaultLocaleProvider
    private lateinit var testCurrentUserProvider: TestCurrentUserProvider
    private lateinit var testScope: TestScope

    private lateinit var generateNotificationsForIntroductoryOffers: GenerateNotificationsForIntroductoryOffers

    private val freeVpnUser = TestVpnUser.create(maxTier = 0, subscribed = 0)
    // Plan data must match the hardcoded conditions in the notification.
    private val vpnPlus = createDynamicPlan(
        "vpn2022",
        mapOf(
            PlanCycle.MONTHLY to mapOf(
                plan("PLN", 10_00),
                plan("EUR",  4_00),
                plan("USD",  4_00),
                plan("CZK", 10_00),
            ),
            PlanCycle.YEARLY to mapOf(
                plan("PLN", 100_00),
                plan("EUR",  40_00),
                plan("USD",  40_00),
                plan("CZK", 100_00),
            )
        )
    )
    private val bundle = createDynamicPlan(
        "bundle2022",
        mapOf(
            PlanCycle.MONTHLY to mapOf(
                plan("PLN", 50_00),
                plan("EUR", 20_00),
            ),
            PlanCycle.YEARLY to mapOf(
                plan("PLN", 500_00),
                plan("EUR", 200_00),
            )
        )
    )

    private lateinit var dynamicPlans: List<DynamicPlan>
    private lateinit var fakeDynamicPlansAdjustedPrices: FakeDynamicPlansAdjustedPrices

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        testScope = TestScope()
        testLocaleProvider = TestDefaultLocaleProvider(Locale.US)
        testCurrentUserProvider = TestCurrentUserProvider(freeVpnUser)
        val currentUser = CurrentUser(testCurrentUserProvider)

        dynamicPlans = listOf(vpnPlus, bundle)
        fakeDynamicPlansAdjustedPrices = FakeDynamicPlansAdjustedPrices({ dynamicPlans })
        val loadGoogleSubscriptionPlans = LoadGoogleSubscriptionPlans(
            vpnUserFlow = currentUser.vpnUserFlow,
            rawDynamicPlans = { dynamicPlans },
            dynamicPlansAdjustedPrices = fakeDynamicPlansAdjustedPrices::invoke,
            availablePaymentProviders = { setOf(PaymentProvider.GoogleInAppPurchase) },
            defaultCycles = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY),
            defaultPreselectedCycle = PlanCycle.YEARLY,

        )
        coEvery { mockInAppUpgradeAllowed.invoke() } returns true

        val getEligibleIntroductoryOffers =
            GetEligibleIntroductoryOffers(loadGoogleSubscriptionPlans, mockInAppUpgradeAllowed, testScope::currentTime)

        featureFlagFlow = MutableStateFlow(true)
        generateNotificationsForIntroductoryOffers = GenerateNotificationsForIntroductoryOffers(
            isIapClientSidePromoFeatureFlagEnabled = FakeIsIapClientSidePromoFeatureFlagEnabled(featureFlagFlow),
            currentUser = currentUser,
            getEligibleIntroductoryOffers = getEligibleIntroductoryOffers,
            appFeaturesPrefs = AppFeaturesPrefs(MockSharedPreferencesProvider()),
            locale = testLocaleProvider,
            clock = testScope::currentTime
        )
    }

    @Test
    fun `notifications are generated only for intro prices`() = testScope.runTest {
        fakeDynamicPlansAdjustedPrices.currency = "USD"
        fakeDynamicPlansAdjustedPrices.introPrices = mapOf(PlanCycle.MONTHLY to 99)

        val notifications = generateNotificationsForIntroductoryOffers()
        assertEquals(2, notifications.size)
        assertImages(
            expectedBannerUrl = "file:///android_asset/promooffers/internal_intro_price_banner_vpn2022_1_usd_99_en_any_dark.png",
            expectedFullscreenUrl = "file:///android_asset/promooffers/internal_intro_price_modal_vpn2022_1_usd_99_en_any_dark.png",
            notifications = notifications
        )
    }

    @Test
    fun `GIVEN currency EUR and language LT THEN fallback images are used`() = testScope.runTest {
        fakeDynamicPlansAdjustedPrices.currency = "EUR"
        fakeDynamicPlansAdjustedPrices.introPrices = mapOf(PlanCycle.MONTHLY to 1_00)
        testLocaleProvider.locale = Locale("lt", "lt")

        val notifications = generateNotificationsForIntroductoryOffers()
        assertEquals(2, notifications.size)
        assertImages(
            expectedBannerUrl = "file:///android_asset/promooffers/internal_intro_price_banner_vpn2022_1_any_any_any_any_dark.png",
            expectedFullscreenUrl = "file:///android_asset/promooffers/internal_intro_price_modal_vpn2022_1_any_any_any_any_dark.png",
            notifications = notifications
        )
    }

    @Test
    fun `GIVEN no plan has intro prices THEN no notifications are generated`() = testScope.runTest {
        fakeDynamicPlansAdjustedPrices.currency = "EUR"

        val notifications = generateNotificationsForIntroductoryOffers()
        assertEquals(emptyList<ApiNotification>(), notifications)
    }

    @Test
    fun `WHEN time passes THEN start and end time are relative to first call`() = testScope.runTest {
        fakeDynamicPlansAdjustedPrices.currency = "EUR"
        fakeDynamicPlansAdjustedPrices.introPrices = mapOf(PlanCycle.MONTHLY to 99)

        val baseTimestampS = 1_000L
        advanceTimeBy(baseTimestampS.seconds)
        val expectedStartTimeS: Long = baseTimestampS + 5 * 3600 // 5 hours
        val expectedEndTimeS: Long = baseTimestampS + 3 * 86400 // 3 days
        val notifications = generateNotificationsForIntroductoryOffers()

        assertEquals(2, notifications.size)
        assertEquals(notifications[0].startTime, notifications[1].startTime)
        assertEquals(notifications[0].endTime, notifications[1].endTime)
        assertEquals(expectedStartTimeS, notifications[0].startTime)
        assertEquals(expectedEndTimeS, notifications[0].endTime)

        repeat(6) {
            advanceTimeBy(12.hours)
            val updatedNotifications = generateNotificationsForIntroductoryOffers()
            assertEquals(expectedStartTimeS, updatedNotifications[0].startTime)
            assertEquals(expectedEndTimeS, updatedNotifications[0].endTime)
        }

        advanceTimeBy(1.hours)
        val notificationPastOfferPeriod = generateNotificationsForIntroductoryOffers()
        assertEquals(emptyList<ApiNotification>(), notificationPastOfferPeriod)
    }

    @Test
    fun `GIVEN offer period has finished (3 days) THEN no notifications are generated`() = testScope.runTest {
        fakeDynamicPlansAdjustedPrices.currency = "EUR"
        fakeDynamicPlansAdjustedPrices.introPrices = mapOf(PlanCycle.MONTHLY to 99)

        advanceTimeBy(1) // Need to have non-zero time - 0 is special.
        generateNotificationsForIntroductoryOffers()
        fakeDynamicPlansAdjustedPrices.resetWasCalled()

        advanceTimeBy(3.days + 1.milliseconds)
        val notifications = generateNotificationsForIntroductoryOffers()
        assertEquals(emptyList<ApiNotification>(), notifications)
        assertFalse(fakeDynamicPlansAdjustedPrices.wasCalled)
    }

    @Test
    fun `GIVEN FF is disabled WHEN generate is called THEN offer period doesn't start`() = testScope.runTest {
        fakeDynamicPlansAdjustedPrices.currency = "EUR"
        fakeDynamicPlansAdjustedPrices.introPrices = mapOf(PlanCycle.MONTHLY to 99)
        featureFlagFlow.value = false

        val beforeFfEnabled = generateNotificationsForIntroductoryOffers()
        assertEquals(emptyList<ApiNotification>(), beforeFfEnabled)

        val baseTimestampS = 1_000L
        advanceTimeBy(baseTimestampS.seconds)
        val expectedStartTimeS: Long = baseTimestampS + 5 * 3600 // 5 hours
        val expectedEndTimeS: Long = baseTimestampS + 3 * 86400 // 3 days
        featureFlagFlow.value = true
        val afterFfEnabled = generateNotificationsForIntroductoryOffers()
        assertEquals(2, afterFfEnabled.size)
        assertEquals(expectedStartTimeS, afterFfEnabled[0].startTime)
        assertEquals(expectedEndTimeS, afterFfEnabled[0].endTime)
    }


    private fun assertImages(
        expectedBannerUrl: String,
        expectedFullscreenUrl: String,
        notifications: List<ApiNotification>
    ) {
        val banner = notifications.find { it.type == ApiNotificationTypes.TYPE_HOME_SCREEN_BANNER }
        val iapFullScreen = notifications.find { it.type == ApiNotificationTypes.TYPE_INTERNAL_ONE_TIME_IAP_POPUP }
        assertNotNull(banner)
        assertNotNull(iapFullScreen)
        assertEquals(
            expectedBannerUrl,
            banner.offer?.panel?.fullScreenImage?.source?.first()?.url
        )
        assertEquals(
            expectedFullscreenUrl,
            iapFullScreen.offer?.panel?.fullScreenImage?.source?.first()?.url
        )
    }
}

private class FakeDynamicPlansAdjustedPrices(private val rawDynamicPlans: () -> List<DynamicPlan>) {

    lateinit var currency: String
    var introPrices: Map<PlanCycle, Int> = emptyMap()

    var wasCalled = false
        private set

    fun resetWasCalled() {
        wasCalled = false
    }

    fun invoke(userId: UserId?): List<DynamicPlan> {
        wasCalled = true
        return rawDynamicPlans().map { plan ->
            plan.copy(instances = plan.instances.mapValues { (cycleMonths, instance) ->
                val planCycle = PlanCycle.entries.find { it.cycleDurationMonths == cycleMonths }!!
                val prices = instance.price
                    .filterKeys { it equalsNoCase currency }
                    .mapValues { (_, price) ->
                        val introPrice = introPrices[planCycle]
                        if (introPrice != null) {
                            price.copy(default = price.current, current = introPrice)
                        } else {
                            price
                        }
                    }
                instance.copy(price = prices)
            })
        }
    }
}

private fun plan(currency: String, price: Int): Pair<String, DynamicPlanPrice> =
    currency to DynamicPlanPrice("", currency, price)
