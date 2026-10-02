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

package com.protonvpn.app.ui.promooffer

import com.protonvpn.android.promooffers.data.ApiNotification
import com.protonvpn.android.promooffers.data.ApiNotificationManager
import com.protonvpn.android.promooffers.data.ApiNotificationOfferButton
import com.protonvpn.android.promooffers.data.ApiNotificationOfferPanel
import com.protonvpn.android.promooffers.data.ApiNotificationProductDetails
import com.protonvpn.android.promooffers.data.ApiNotificationProductDetailsGoogle
import com.protonvpn.android.promooffers.data.ApiNotificationProminentBanner
import com.protonvpn.android.promooffers.data.ApiNotificationProminentBannerStyle
import com.protonvpn.android.promooffers.data.ApiNotificationTypes
import com.protonvpn.android.promooffers.data.ApiNotificationsResponse
import com.protonvpn.android.promooffers.ui.HomeScreenProminentBannerFlow
import com.protonvpn.android.promooffers.ui.HomeScreenPromoBannerFlow
import com.protonvpn.android.ui.planupgrade.usecase.GetUpgradeDialogPlansConfig
import com.protonvpn.app.testRules.RobolectricHiltAndroidRule
import com.protonvpn.test.shared.ApiNotificationTestHelper.mockFullScreenImagePanel
import com.protonvpn.test.shared.ApiNotificationTestHelper.mockOffer
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import me.proton.core.util.kotlin.serialize
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Verify that overlapping notifications are correctly resolved:
 * the notification that ends first gets displayed in each given slot.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class NotificationTimeOverlapIntegrationTests {

    @get:Rule
    val hiltRule = RobolectricHiltAndroidRule(this)

    @Inject
    lateinit var apiNotificationManager: ApiNotificationManager

    @Inject
    lateinit var homeScreenBannerFlow: HomeScreenPromoBannerFlow

    @Inject
    lateinit var prominentBannerFlow: HomeScreenProminentBannerFlow

    @Inject
    lateinit var testScope: TestScope

    private lateinit var getUpgradeDialogPlansConfig: GetUpgradeDialogPlansConfig

    @Before
    fun setup() {
        hiltRule.inject()
        // Timestamp 0 is special (see ServerListUpdaterTests), run tests with a common clock value.
        testScope.advanceTimeBy(1.days)
        // Real ApiNotificationManager wiring, only the unrelated in-app-purchase-allowed gate is
        // stubbed out (same approach UpgradeDialogViewModelTests uses), so it doesn't get in the
        // way of the notification-selection behavior under test.
        getUpgradeDialogPlansConfig = GetUpgradeDialogPlansConfig(
            isInAppUpgradeAllowed = { true },
            activeNonOnboardingNotificationsFlow = apiNotificationManager.activeNonOnboardingNotificationsFlow,
            activeOnboardingNotificationsFlow = apiNotificationManager.activeOnboardingNotificationsFlow,
            awaitNotificationsUpdate = apiNotificationManager::awaitUpdateFinish,
        )
    }

    @Test
    fun `home screen banner shows the campaign with the closest end time and reverts once it expires`() =
        testScope.runTest {
            setupNotifications(
                banner("long", 0.days, 30.days),
                banner("short", 5.days, 10.days),
            )

            assertSelectionsAtCheckpoints(
                listOf("long", "short", "long") to {
                    homeScreenBannerFlow(isNighMode = true).first()?.notificationId
                },
            )
        }

    @Test
    fun `prominent banner shows the campaign with the closest end time and reverts once it expires`() =
        testScope.runTest {
            setupNotifications(
                prominentBanner("long", 0.days, 30.days),
                prominentBanner("short", 5.days, 10.days),
            )

            assertSelectionsAtCheckpoints(
                listOf("long", "short", "long") to { prominentBannerFlow.first()?.notificationId },
            )
        }

    @Test
    fun `builtin upsell plan config uses the campaign with the closest end time and reverts once it expires`() =
        testScope.runTest {
            val type = ApiNotificationTypes.TYPE_BUILTIN_UPSELL_PADLOCK
            setupNotifications(
                builtinUpsell("long", 0.days, 30.days, type),
                builtinUpsell("short", 5.days, 10.days, type),
            )

            assertSelectionsAtCheckpoints(
                listOf("long", "short", "long") to {
                    getUpgradeDialogPlansConfig.forBuiltinUpsell(type, listOf("vpnplus")).notificationReference
                },
            )
        }

    @Test
    fun `each notification type resolves its own closest end time independently when all types are active at once`() =
        testScope.runTest {
            val upsellType = ApiNotificationTypes.TYPE_BUILTIN_UPSELL_PADLOCK
            setupNotifications(
                banner("banner-long", 0.days, 30.days),
                banner("banner-short", 5.days, 10.days),
                prominentBanner("prominent-long", 0.days, 30.days),
                prominentBanner("prominent-short", 5.days, 10.days),
                builtinUpsell("upsell-long", 0.days, 30.days, upsellType),
                builtinUpsell("upsell-short", 5.days, 10.days, upsellType),
            )

            assertSelectionsAtCheckpoints(
                listOf("banner-long", "banner-short", "banner-long") to {
                    homeScreenBannerFlow(isNighMode = true).first()?.notificationId
                },
                listOf("prominent-long", "prominent-short", "prominent-long") to {
                    prominentBannerFlow.first()?.notificationId
                },
                listOf("upsell-long", "upsell-short", "upsell-long") to {
                    getUpgradeDialogPlansConfig.forBuiltinUpsell(upsellType, listOf("vpnplus")).notificationReference
                },
            )
        }

    // On day 3 only the long campaign of each type is active, on day 7 both are (the short one
    // ends sooner), on day 11 the short one has expired and only the long one remains - matching
    // the spec's own example. Every given (expectedIds, select) pair is checked at each
    // checkpoint, so several consumers can be verified against the same shared notification list.
    private suspend fun TestScope.assertSelectionsAtCheckpoints(
        vararg checks: Pair<List<String>, suspend () -> String?>
    ) {
        val deltas = listOf(3.days, 4.days, 4.days)
        deltas.forEachIndexed { checkpoint, delta ->
            advanceTimeBy(delta)
            checks.forEach { (expectedIds, select) -> assertEquals(expectedIds[checkpoint], select()) }
        }
    }

    private fun nowS(): Long = testScope.currentTime / 1000

    private fun setupNotifications(vararg notifications: ApiNotification) {
        apiNotificationManager.setTestNotificationsResponseJson(
            ApiNotificationsResponse(notifications.toList()).serialize()
        )
    }

    private fun banner(id: String, start: Duration, end: Duration) = mockOffer(
        id = id,
        start = absoluteS(start),
        end = absoluteS(end),
        type = ApiNotificationTypes.TYPE_HOME_SCREEN_BANNER,
        panel = mockFullScreenImagePanel(
            // file: URLs are excluded from image prefetching, same as mockOffer()'s default iconUrl.
            darkModeImageUrl = "file:///android_asset/$id.png",
            lightThemeImageUrl = "file:///android_asset/$id.png",
            button = ApiNotificationOfferButton(url = "https://protonvpn.com/$id"),
        ),
    )

    private fun prominentBanner(id: String, start: Duration, end: Duration) = mockOffer(
        id = id,
        start = absoluteS(start),
        end = absoluteS(end),
        type = ApiNotificationTypes.TYPE_HOME_PROMINENT_BANNER,
        prominentBanner = ApiNotificationProminentBanner(
            title = "$id title",
            dismissButtonText = "Close",
            style = ApiNotificationProminentBannerStyle.REGULAR,
        ),
    )

    private fun builtinUpsell(id: String, start: Duration, end: Duration, type: Int) = mockOffer(
        id = id,
        start = absoluteS(start),
        end = absoluteS(end),
        type = type,
        reference = id,
        panel = ApiNotificationOfferPanel(
            iapProductDetails = ApiNotificationProductDetails(
                google = ApiNotificationProductDetailsGoogle(offerTag = "$id-tag")
            )
        ),
    )

    // Notification start/end times are relative to the test's baseline clock reading.
    private fun absoluteS(offset: Duration): Long = nowS() + offset.inWholeSeconds
}
