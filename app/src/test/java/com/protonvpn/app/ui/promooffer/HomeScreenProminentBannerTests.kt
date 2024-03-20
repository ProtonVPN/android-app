/*
 * Copyright (c) 2024. Proton AG
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

import com.protonvpn.android.appconfig.ApiNotification
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.ApiNotificationOfferButton
import com.protonvpn.android.appconfig.ApiNotificationProminentBanner
import com.protonvpn.android.appconfig.ApiNotificationProminentBannerStyle
import com.protonvpn.android.appconfig.ApiNotificationTypes
import com.protonvpn.android.ui.promooffers.HomeScreenProminentBannerFlow
import com.protonvpn.android.ui.promooffers.ProminentBannerState
import com.protonvpn.android.ui.promooffers.PromoOffersPrefs
import com.protonvpn.test.shared.ApiNotificationTestHelper.mockOffer
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.proton.core.util.kotlin.deserialize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class HomeScreenProminentBannerTests {

    @MockK
    private lateinit var mockNotificationManager: ApiNotificationManager

    private lateinit var activeNotifications: MutableStateFlow<List<ApiNotification>>
    private lateinit var offersPrefs: PromoOffersPrefs

    private lateinit var prominentBannerFlow: HomeScreenProminentBannerFlow

    private val offer = mockOffer(
        "id1",
        reference = "ref",
        type = ApiNotificationTypes.TYPE_HOME_PROMINENT_BANNER,
        prominentBanner = ApiNotificationProminentBanner(
            title = "Banner title",
            actionButton = ApiNotificationOfferButton("Confirm"),
            dismissButtonText = "Close",
            style = ApiNotificationProminentBannerStyle.REGULAR,
        )
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        offersPrefs = PromoOffersPrefs(MockSharedPreferencesProvider())

        activeNotifications = MutableStateFlow(emptyList())
        every { mockNotificationManager.activeListFlow } returns activeNotifications

        prominentBannerFlow = HomeScreenProminentBannerFlow(mockNotificationManager, offersPrefs)
    }

    @Test
    fun `notification is emitted by flow`() = runTest {
        activeNotifications.value = listOf(offer)
        val banner = prominentBannerFlow.first()
        val expected = ProminentBannerState(
            title = "Banner title",
            description = null,
            actionButton = ApiNotificationOfferButton("Confirm"),
            dismissButtonText = "Close",
            notificationId = "id1",
            reference = "ref",
            style = ApiNotificationProminentBannerStyle.REGULAR
        )
        assertEquals(expected, banner)
    }

    @Test
    fun `dismissed notification is ignored`() = runTest {
        activeNotifications.value = listOf(offer)
        offersPrefs.addVisitedOffer(offer.id)
        val banner = prominentBannerFlow.first()
        assertNull(banner)
    }

    @Test
    fun `notification without banner is ignored`() = runTest {
        val brokenOffer = mockOffer(
            id = "broken",
            type = 3,
            prominentBanner = null,
        )
        activeNotifications.value = listOf(brokenOffer)
        val banner = prominentBannerFlow.first()
        assertNull(banner)
    }

    @Test
    fun `deserialize minimal prominent banner notification`() {
        val json = """
            {
                "NotificationID": "ID",
                "StartTime": 1000,
                "EndTime": 2000,
                "Type": 3,
                "ProminentBanner": {
                    "DismissButtonText": "Close",
                    "Style": "Warning"
                }
            }
        """.trimIndent()
        val expected = ApiNotification(
            id = "ID", startTime = 1000, endTime = 2000, type = ApiNotificationTypes.TYPE_HOME_PROMINENT_BANNER,
            prominentBanner = ApiNotificationProminentBanner(
                dismissButtonText = "Close", style = ApiNotificationProminentBannerStyle.WARNING
            )
        )
        assertEquals(expected, json.deserialize<ApiNotification>())
    }

    @Test
    fun `deserialize full prominent banner notification`() {
        val json = """
            {
                "NotificationID": "ID",
                "StartTime": 1000,
                "EndTime": 2000,
                "Type": 3,
                "ProminentBanner": {
                    "Title": "Title",
                    "Description": "Description",
                    "ActionButton": {
                        "Text": "Action",
                        "URL": "https://action",
                        "Action": "OpenURL",
                        "Behaviors": [ "AutoLogin" ]
                    },
                    "DismissButtonText": "Close",
                    "Style": "Warning"
                }
            }
        """.trimIndent()
        val expected = ApiNotification(
            id = "ID", startTime = 1000, endTime = 2000, type = ApiNotificationTypes.TYPE_HOME_PROMINENT_BANNER,
            prominentBanner = ApiNotificationProminentBanner(
                title = "Title",
                description = "Description",
                actionButton = ApiNotificationOfferButton(
                    text = "Action",
                    url = "https://action",
                    action = "OpenURL",
                    actionBehaviors = listOf("AutoLogin")
                ),
                dismissButtonText = "Close",
                style = ApiNotificationProminentBannerStyle.WARNING
            )
        )
        assertEquals(expected, json.deserialize<ApiNotification>())
    }
}
