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
import com.protonvpn.android.appconfig.ApiNotificationTypes
import com.protonvpn.android.ui.promooffers.HomeScreenPromoBannerFlow
import com.protonvpn.android.ui.promooffers.PromoOfferBannerState
import com.protonvpn.android.ui.promooffers.PromoOffersPrefs
import com.protonvpn.test.shared.ApiNotificationTestHelper.mockFullScreenImagePanel
import com.protonvpn.test.shared.ApiNotificationTestHelper.mockOffer
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.runWhileCollecting
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeScreenPromoOfferBannerTests {

    @MockK
    private lateinit var mockNotificationManager: ApiNotificationManager

    private lateinit var activeNotifications: MutableStateFlow<List<ApiNotification>>
    private lateinit var promoOffersPrefs: PromoOffersPrefs

    private lateinit var homeScreenBannerFlow: HomeScreenPromoBannerFlow

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        activeNotifications = MutableStateFlow(emptyList())
        every { mockNotificationManager.activeListFlow } returns activeNotifications

        promoOffersPrefs = PromoOffersPrefs(MockSharedPreferencesProvider())
        homeScreenBannerFlow = HomeScreenPromoBannerFlow(mockNotificationManager, promoOffersPrefs)
    }

    @Test
    fun `when a banner notification is active it is emitted`() = runTest {
        val action = ApiNotificationOfferButton(url = "action url")
        val banner = mockFullScreenImagePanel("image url", "alternative text", button = action)
        val offer = mockOffer("id", type = ApiNotificationTypes.TYPE_HOME_SCREEN_BANNER, panel = banner)
        activeNotifications.value = listOf(offer)

        val uiBanner = homeScreenBannerFlow.first()
        val expected = PromoOfferBannerState(
            "image url", "alternative text", action, isDismissible = false, endTimestamp = null, notificationId = "id", reference = null
        )
        assertEquals(expected, uiBanner)
    }

    @Test
    fun `banner notifications with missing image or action are ignored`() = runTest {
        val actionNoUrl = ApiNotificationOfferButton(url = "")
        val bannerNoActionUrl = mockFullScreenImagePanel("image url", "alternative text", button = actionNoUrl)
        val offerNoActionUrl =
            mockOffer("id2", type = ApiNotificationTypes.TYPE_HOME_SCREEN_BANNER, panel = bannerNoActionUrl)
        val offerNoPanel = mockOffer("id3", type = ApiNotificationTypes.TYPE_HOME_SCREEN_BANNER)

        activeNotifications.value = listOf(offerNoPanel, offerNoActionUrl)
        assertNull(homeScreenBannerFlow.first())
    }

    @Test
    fun `notifications of other types are ignored`() = runTest {
        val action = ApiNotificationOfferButton(url = "action url")
        val banner = mockFullScreenImagePanel("image url", "alternative text", button = action)
        val popupOffer = mockOffer("id", type = ApiNotificationTypes.TYPE_ONE_TIME_POPUP, panel = banner)

        activeNotifications.value = listOf(popupOffer)
        assertNull(homeScreenBannerFlow.first())
    }

    @Test
    fun `dismissing an offer hides it`() = runTest {
        val action = ApiNotificationOfferButton(url = "action url")
        val banner = mockFullScreenImagePanel("image url", "alternative text", button = action)
        val offer = mockOffer("id", type = ApiNotificationTypes.TYPE_HOME_SCREEN_BANNER, panel = banner)
        activeNotifications.value = listOf(offer)

        val bannerStates = runWhileCollecting(homeScreenBannerFlow) {
            runCurrent()
            promoOffersPrefs.addVisitedOffer("id")
            runCurrent()
        }
        val uiBanner = PromoOfferBannerState(
            "image url", "alternative text", action, isDismissible = false, endTimestamp = null, notificationId = "id", reference = null
        )
        assertEquals(listOf(uiBanner, null), bannerStates)
    }
}
