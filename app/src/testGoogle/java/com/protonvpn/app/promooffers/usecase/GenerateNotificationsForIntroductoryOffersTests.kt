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

import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.promooffers.data.ApiNotification
import com.protonvpn.android.promooffers.data.ApiNotificationTypes
import com.protonvpn.android.promooffers.usecase.FakeIsIapClientSidePromo12mEnabled
import com.protonvpn.android.promooffers.usecase.FakeIsIapClientSidePromoCyclicEnabled
import com.protonvpn.android.promooffers.usecase.FakeIsIapClientSidePromoFeatureFlagEnabled
import com.protonvpn.android.promooffers.usecase.GenerateNotificationsForIntroductoryOffers
import com.protonvpn.android.promooffers.usecase.GetEligibleIntroductoryOffers
import com.protonvpn.android.ui.planupgrade.IapConstants
import com.protonvpn.android.ui.planupgrade.IsInAppUpgradeAllowedUseCase
import com.protonvpn.android.ui.planupgrade.usecase.LoadGoogleSubscriptionPlans
import com.protonvpn.mocks.TestDefaultLocaleProvider
import com.protonvpn.test.shared.InMemoryObjectStore
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestLoadGoogleOffers
import com.protonvpn.test.shared.TestVpnUser
import com.protonvpn.test.shared.createDynamicPlan
import com.protonvpn.test.shared.createGiapOffer
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import me.proton.core.payment.domain.usecase.PaymentProvider
import me.proton.core.plan.domain.entity.DynamicPlan
import me.proton.core.plan.domain.entity.DynamicPlanPrice
import me.proton.core.plan.presentation.entity.PlanCycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class GenerateNotificationsForIntroductoryOffersTests {

    @MockK
    private lateinit var mockInAppUpgradeAllowed: IsInAppUpgradeAllowedUseCase

    private lateinit var isIapEnabledFF: FakeIsIapClientSidePromoFeatureFlagEnabled
    private lateinit var isCyclicEnabledFF: FakeIsIapClientSidePromoCyclicEnabled
    private lateinit var is12mEnabledFF: FakeIsIapClientSidePromo12mEnabled
    private lateinit var testLocaleProvider: TestDefaultLocaleProvider
    private lateinit var testCurrentUserProvider: TestCurrentUserProvider
    private lateinit var testScope: TestScope

    private lateinit var generateNotificationsForIntroductoryOffers: GenerateNotificationsForIntroductoryOffers

    private val freeVpnUser = TestVpnUser.create(maxTier = 0, subscribed = 0)

    private val introTags = listOf(IapConstants.INTRO_PRICE_TAG)
    private val baseTags = listOf(IapConstants.BASE_PRICE_TAG)

    // Plan data must match the hardcoded conditions in the notification.
    private val vpnPlus = "vpn2022"
    private val bundle = "bundle2022"
    private val vpnPlusPlan = createDynamicPlan(
        vpnPlus,
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
    private val bundlePlan = createDynamicPlan(
        bundle,
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
    private lateinit var testLoadGoogleOffers: TestLoadGoogleOffers

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        testScope = TestScope()
        testLocaleProvider = TestDefaultLocaleProvider(Locale.US)
        testCurrentUserProvider = TestCurrentUserProvider(freeVpnUser)
        val currentUser = CurrentUser(testCurrentUserProvider)

        dynamicPlans = listOf(vpnPlusPlan, bundlePlan)
        testLoadGoogleOffers = TestLoadGoogleOffers()
        val loadGoogleSubscriptionPlans = LoadGoogleSubscriptionPlans(
            vpnUserFlow = currentUser.vpnUserFlow,
            rawDynamicPlans = { dynamicPlans },
            loadGoogleOffers = testLoadGoogleOffers::invoke,
            availablePaymentProviders = { setOf(PaymentProvider.GoogleInAppPurchase) },
            defaultCycles = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY),
            defaultPreselectedCycle = PlanCycle.YEARLY,
        )
        coEvery { mockInAppUpgradeAllowed.invoke() } returns true

        val getEligibleIntroductoryOffers =
            GetEligibleIntroductoryOffers(
                loadGoogleSubscriptionPlans,
                mockInAppUpgradeAllowed,
                InMemoryObjectStore(),
                testScope::currentTime
            )

        isIapEnabledFF = FakeIsIapClientSidePromoFeatureFlagEnabled(true)
        isCyclicEnabledFF = FakeIsIapClientSidePromoCyclicEnabled(true)
        is12mEnabledFF = FakeIsIapClientSidePromo12mEnabled(false)
        generateNotificationsForIntroductoryOffers = GenerateNotificationsForIntroductoryOffers(
            isIapClientSidePromoFeatureFlagEnabled = isIapEnabledFF,
            isIapClientSidePromoCyclicEnabled = isCyclicEnabledFF,
            isIapClientSidePromo12mEnabled = is12mEnabledFF,
            currentUser = currentUser,
            getEligibleIntroductoryOffers = getEligibleIntroductoryOffers,
            appFeaturesPrefs = AppFeaturesPrefs(MockSharedPreferencesProvider()),
            locale = testLocaleProvider,
            clock = testScope::currentTime
        )

        testScope.advanceTimeBy(1_000) // 0 timestamp is special.
    }

    @Test
    fun `notifications are generated only for intro prices`() = testScope.runTest {
        testLoadGoogleOffers.offers = listOf(
            createGiapOffer(vpnPlus, PlanCycle.MONTHLY, listOf(99, 10_00), currency = "USD", tags = introTags),
            createGiapOffer(vpnPlus, PlanCycle.MONTHLY, listOf(10_00), currency = "USD", tags = baseTags),
        )

        val notifications = generateNotificationsForIntroductoryOffers(false)
        assertEquals(2, notifications.size)
        assertImages(
            expectedBannerUrl = "file:///android_asset/promooffers/internal_intro_price_banner_vpn2022_1_usd_99_en_any_dark.png",
            expectedFullscreenUrl = "file:///android_asset/promooffers/internal_intro_price_modal_vpn2022_1_usd_99_en_any_dark.png",
            notifications = notifications
        )
    }

    @Test
    fun `GIVEN currency EUR and language LT THEN fallback images are used`() = testScope.runTest {
        testLoadGoogleOffers.offers = listOf(
            createGiapOffer(vpnPlus, PlanCycle.MONTHLY, listOf(99, 10_00), currency = "EUR"),
        )
        testLocaleProvider.locale = Locale("lt", "lt")

        val notifications = generateNotificationsForIntroductoryOffers(false)
        assertEquals(2, notifications.size)
        assertImages(
            expectedBannerUrl = "file:///android_asset/promooffers/internal_intro_price_banner_vpn2022_1_any_any_any_any_dark.png",
            expectedFullscreenUrl = "file:///android_asset/promooffers/internal_intro_price_modal_vpn2022_1_any_any_any_any_dark.png",
            notifications = notifications
        )
    }

    @Test
    fun `GIVEN no plan has intro prices THEN no notifications are generated`() = testScope.runTest {
        testLoadGoogleOffers.offers = listOf(
            createGiapOffer(vpnPlus, PlanCycle.MONTHLY, listOf(99), currency = "EUR", tags = introTags),
        )

        val notifications = generateNotificationsForIntroductoryOffers(false)
        assertEquals(emptyList<ApiNotification>(), notifications)
    }

    @Test
    fun `WHEN time passes THEN start and end time are relative to first call`() = testScope.runTest {
        testLoadGoogleOffers.offers = listOf(
            createGiapOffer(vpnPlus, PlanCycle.MONTHLY, listOf(99, 10_00), currency = "EUR", tags = introTags),
        )

        val baseTimestampS = currentTime.milliseconds.inWholeSeconds
        val expectedStartTimeS: Long = baseTimestampS + 5 * 3600 // 5 hours
        val expectedEndTimeS: Long = baseTimestampS + 3 * 86400 // 3 days
        val notifications = generateNotificationsForIntroductoryOffers(false)

        assertEquals(2, notifications.size)
        assertEquals(notifications[0].startTime, notifications[1].startTime)
        assertEquals(notifications[0].endTime, notifications[1].endTime)
        assertEquals(expectedStartTimeS, notifications[0].startTime)
        assertEquals(expectedEndTimeS, notifications[0].endTime)

        repeat(6) {
            advanceTimeBy(12.hours)
            val updatedNotifications = generateNotificationsForIntroductoryOffers(false)
            assertEquals(expectedStartTimeS, updatedNotifications[0].startTime)
            assertEquals(expectedEndTimeS, updatedNotifications[0].endTime)
        }

        advanceTimeBy(1.hours)
        val notificationPastOfferPeriod = generateNotificationsForIntroductoryOffers(false)
        assertEquals(emptyList<ApiNotification>(), notificationPastOfferPeriod)
    }

    @Test
    fun `GIVEN offer period has finished (3 days) THEN no notifications are generated`() = testScope.runTest {
        testLoadGoogleOffers.offers = listOf(
            createGiapOffer(vpnPlus, PlanCycle.MONTHLY, listOf(99, 10_00), currency = "EUR", tags = introTags),
        )

        generateNotificationsForIntroductoryOffers(false)
        testLoadGoogleOffers.resetWasCalled()

        advanceTimeBy(3.days + 1.milliseconds)
        val notifications = generateNotificationsForIntroductoryOffers(false)
        assertEquals(emptyList<ApiNotification>(), notifications)
        assertFalse(testLoadGoogleOffers.wasCalled)
    }

    @Test
    fun `GIVEN FF is disabled WHEN generate is called THEN offer period doesn't start`() = testScope.runTest {
        testLoadGoogleOffers.offers = listOf(
            createGiapOffer(vpnPlus, PlanCycle.MONTHLY, listOf(99, 10_00), currency = "EUR", tags = introTags),
        )
        isIapEnabledFF.setEnabled(false)

        val beforeFfEnabled = generateNotificationsForIntroductoryOffers(false)
        assertEquals(emptyList<ApiNotification>(), beforeFfEnabled)

        val baseTimestampS = currentTime.milliseconds.inWholeSeconds
        val expectedStartTimeS: Long = baseTimestampS + 5 * 3600 // 5 hours
        val expectedEndTimeS: Long = baseTimestampS + 3 * 86400 // 3 days
        isIapEnabledFF.setEnabled(true)
        val afterFfEnabled = generateNotificationsForIntroductoryOffers(false)
        assertEquals(2, afterFfEnabled.size)
        assertEquals(expectedStartTimeS, afterFfEnabled[0].startTime)
        assertEquals(expectedEndTimeS, afterFfEnabled[0].endTime)
    }

    @Test
    fun `GIVEN cyclic FF is enabled WHEN generate is called after 80 days THEN new offers are generated`() = testScope.runTest {
        testLoadGoogleOffers.offers = listOf(
            createGiapOffer(vpnPlus, PlanCycle.MONTHLY, listOf(99, 10_00), currency = "EUR", tags = introTags),
        )
        advanceTimeBy(1.days)
        val firstRoundNotifications = generateNotificationsForIntroductoryOffers(false)
        assertTrue(firstRoundNotifications.isNotEmpty())

        advanceTimeBy(5.days)
        val cooldownPeriodNotifications = generateNotificationsForIntroductoryOffers(true)
        assertTrue(cooldownPeriodNotifications.isEmpty())

        advanceTimeBy(80.days)
        val noTriggerNotifications = generateNotificationsForIntroductoryOffers(false)
        assertTrue(noTriggerNotifications.isEmpty())

        val secondRoundNotifications = generateNotificationsForIntroductoryOffers(true)
        assertTrue(secondRoundNotifications.isNotEmpty())
    }

    @Test
    fun `WHEN second round offers are generated THEN their start time is now`() = testScope.runTest {
        testLoadGoogleOffers.offers = listOf(
            createGiapOffer(vpnPlus, PlanCycle.MONTHLY, listOf(99, 10_00), currency = "EUR", tags = introTags),
        )

        generateNotificationsForIntroductoryOffers(true)
        advanceTimeBy(90.days)

        val baseTimestampS = currentTime.milliseconds.inWholeSeconds
        val expectedStartTimeS: Long = baseTimestampS // 5 hours
        val expectedEndTimeS: Long = baseTimestampS + 3 * 86400 // 3 days
        val notifications = generateNotificationsForIntroductoryOffers(true)
        assertEquals(2, notifications.size)
        assertEquals(expectedStartTimeS, notifications[0].startTime)
        assertEquals(expectedEndTimeS, notifications[0].endTime)
    }

    @Test
    fun `GIVEN second round offers were generated WHEN generate is called without trigger THEN the offers are regenerated`() = testScope.runTest {
        testLoadGoogleOffers.offers = listOf(
            createGiapOffer(vpnPlus, PlanCycle.MONTHLY, listOf(99, 10_00), currency = "EUR", tags = introTags),
        )

        generateNotificationsForIntroductoryOffers(true)
        advanceTimeBy(90.days)

        val triggeredNotifications = generateNotificationsForIntroductoryOffers(true)
        advanceTimeBy(2.days)
        val noTriggerNotifications = generateNotificationsForIntroductoryOffers(false)
        assertEquals(triggeredNotifications, noTriggerNotifications)
    }

    @Test
    fun `GIVEN cyclic FF is disabled WHEN generate is called after 80 days THEN no offers are generated`() = testScope.runTest {
        testLoadGoogleOffers.offers = listOf(
            createGiapOffer(vpnPlus, PlanCycle.MONTHLY, listOf(99, 10_00), currency = "EUR", tags = introTags),
        )
        isCyclicEnabledFF.setEnabled(false)

        advanceTimeBy(1.days)
        val firstRoundOffers = generateNotificationsForIntroductoryOffers(false)
        assertTrue(firstRoundOffers.isNotEmpty())

        advanceTimeBy(100.days)
        val lateOffers = generateNotificationsForIntroductoryOffers(false)
        assertTrue(lateOffers.isEmpty())
    }

    @Test
    fun `GIVEN intro prices for monthly and yearly WHEN 12m FF is disabled THEN 1m offers are generated`() = testScope.runTest {
        testLoadGoogleOffers.offers = listOf(
            createGiapOffer(vpnPlus, PlanCycle.MONTHLY, listOf(99, 10_00), currency = "USD", tags = introTags),
            createGiapOffer(vpnPlus, PlanCycle.YEARLY, listOf(2_00, 100_00), currency = "USD", tags = introTags),
        )

        val notifications = generateNotificationsForIntroductoryOffers(false)
        assertEquals(2, notifications.size)
        assertImages(
            expectedBannerUrl = "file:///android_asset/promooffers/internal_intro_price_banner_vpn2022_1_usd_99_en_any_dark.png",
            expectedFullscreenUrl = "file:///android_asset/promooffers/internal_intro_price_modal_vpn2022_1_usd_99_en_any_dark.png",
            notifications = notifications
        )
        val banner = notifications.find { it.type == ApiNotificationTypes.TYPE_HOME_SCREEN_BANNER }
        assertEquals("IntroPricePromoBanner", banner?.reference)
    }

    @Test
    fun `GIVEN intro prices for monthly and yearly WHEN 12m FF is enabled THEN 12m offers are generated`() = testScope.runTest {
        testLoadGoogleOffers.offers = listOf(
            createGiapOffer(vpnPlus, PlanCycle.MONTHLY, listOf(99, 10_00), currency = "USD", tags = introTags),
            createGiapOffer(vpnPlus, PlanCycle.YEARLY, listOf(2_00, 100_00), currency = "USD", tags = introTags),
        )
        is12mEnabledFF.setEnabled(true)

        val notifications = generateNotificationsForIntroductoryOffers(false)
        assertEquals(2, notifications.size)
        assertImages(
            expectedBannerUrl = "file:///android_asset/promooffers/internal_intro_price_banner_vpn2022_12_any_any_any_any_dark.png",
            expectedFullscreenUrl = "file:///android_asset/promooffers/internal_intro_price_modal_vpn2022_12_any_any_any_any_dark.png",
            notifications = notifications
        )
        val banner = notifications.find { it.type == ApiNotificationTypes.TYPE_HOME_SCREEN_BANNER }
        assertEquals("IntroPricePromoBanner12", banner?.reference)
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

private fun plan(currency: String, price: Int): Pair<String, DynamicPlanPrice> =
    currency to DynamicPlanPrice("", currency, price)
