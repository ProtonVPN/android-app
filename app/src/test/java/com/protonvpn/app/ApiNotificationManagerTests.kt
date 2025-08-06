/*
 * Copyright (c) 2019 Proton AG
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
package com.protonvpn.app

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.ApiNotification
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.ApiNotificationsResponse
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.AppConfigResponse
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.appconfig.ImagePrefetcher
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.ui.promooffers.PromoOfferImage
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.test.shared.ApiNotificationTestHelper.mockFullScreenImagePanel
import com.protonvpn.test.shared.ApiNotificationTestHelper.mockOffer
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createAccountUser
import io.mockk.MockKAnnotations
import io.mockk.called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import me.proton.core.network.domain.ApiResult
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApiNotificationManagerTests {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockAppConfig: AppConfig
    @RelaxedMockK
    private lateinit var mockContext: Context
    @MockK
    private lateinit var mockImagePrefetcher: ImagePrefetcher
    @MockK
    private lateinit var mockUserPlanManager: UserPlanManager
    @MockK
    private lateinit var mockApi: ProtonApiRetroFit
    @RelaxedMockK
    private lateinit var mockPeriodicUpdateManager: PeriodicUpdateManager

    private lateinit var appFeaturesPrefs: AppFeaturesPrefs
    private lateinit var currentUser: CurrentUser
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope
    private lateinit var testUserProvider: TestCurrentUserProvider
    private lateinit var infoChangeFlow: MutableSharedFlow<List<UserPlanManager.InfoChange>>
    private lateinit var appConfigFlow: MutableStateFlow<AppConfigResponse>

    private val plusUser = TestUser.plusUser.vpnUser

    private lateinit var notificationManager: ApiNotificationManager

    private fun mockResponse(vararg items: ApiNotification) {
        coEvery {
            mockApi.getApiNotifications(any(), any(), any())
        } returns ApiResult.Success(ApiNotificationsResponse(listOf(*items)))
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(MockSharedPreference())
        appFeaturesPrefs = AppFeaturesPrefs(MockSharedPreferencesProvider())
        testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)

        appConfigFlow = MutableStateFlow(
            AppConfigResponse(
                featureFlags = FeatureFlags(pollApiNotifications = true),
                defaultPortsConfig = null,
                smartProtocolConfig = null,
                ratingConfig = null
            )
        )

        testUserProvider = TestCurrentUserProvider(plusUser, createAccountUser(plusUser.userId))
        currentUser = CurrentUser(testUserProvider)

        every { mockAppConfig.appConfigUpdateEvent } returns MutableSharedFlow()
        every { mockAppConfig.appConfigFlow } returns appConfigFlow
        every { mockImagePrefetcher.prefetch(any()) } returns true

        infoChangeFlow = MutableSharedFlow()
        every { mockUserPlanManager.infoChangeFlow } returns infoChangeFlow

        mockkObject(PromoOfferImage)
        every { PromoOfferImage.getFullScreenImageMaxSizePx(any()) } returns PromoOfferImage.Size(100, 100)

        notificationManager = createNotificationsManager()
    }

    @Test
    fun testFiltering() = testScope.runTest {
        mockResponse(
            mockOffer("active1", 0L, 1L),
            mockOffer("active2", -1L, 1L),
            mockOffer("future", 1L, 2L),
            mockOffer("just ended", -1L, 0L)
        )
        notificationManager.updateNotifications()

        Assert.assertEquals(listOf("active1", "active2"), notificationManager.activeListFlow.first().map { it.id })
    }

    @Test
    fun testOrdering() = testScope.runTest {
        mockResponse(
            mockOffer("active2", 0L, 2L),
            mockOffer("active1", -1L, 1L),
        )
        notificationManager.updateNotifications()

        assertEquals(listOf("active1", "active2"), notificationManager.activeListFlow.first().map { it.id })
    }

    @Test
    fun testScheduling() = testScope.runTest {
        val apiTime = currentTime / 1000
        mockResponse(
            mockOffer("past", apiTime - 2, apiTime - 1),
            mockOffer("active", apiTime - 1, apiTime + 1),
            mockOffer("future", apiTime + 1, apiTime + 2))
        notificationManager.updateNotifications()

        val observedLists = mutableListOf<List<String>>()
        val collectJob = launch {
            notificationManager.activeListFlow
                .map { list -> list.map { it.id } }
                .toList(observedLists)
        }

        Assert.assertEquals(listOf("active"), notificationManager.activeListFlow.first().map { it.id })
        advanceTimeBy(1500)
        Assert.assertEquals(listOf("future"), notificationManager.activeListFlow.first().map { it.id })
        Assert.assertEquals(listOf(listOf("active"), listOf("future")), observedLists)

        collectJob.cancel()
    }

    @Test
    fun `when image prefetch fails notification is filtered out`() = testScope.runTest {
        setupImagePrefetchTest()

        assertEquals(listOf("success"), notificationManager.activeListFlow.first().map { it.id })
    }

    private suspend fun setupImagePrefetchTest() {
        mockResponse(
            mockOffer("success", -1, 1, iconUrl = "urlSuccess", panel = mockFullScreenImagePanel("urlSuccess", "urlSuccess")),
            mockOffer("failureBoth", -1, 1, iconUrl = "urlSuccess", panel = mockFullScreenImagePanel("urlFailure", "urlFailure")),
            mockOffer("failureDark", -1, 1, iconUrl = "urlSuccess", panel = mockFullScreenImagePanel("urlFailure", "urlSuccess")),
            mockOffer("failureLight", -1, 1, iconUrl = "urlSuccess", panel = mockFullScreenImagePanel("urlSuccess", "urlFailure"))
        )

        every { mockImagePrefetcher.prefetch(any()) } returns false
        every { mockImagePrefetcher.prefetch("urlSuccess") } returns true

        notificationManager.updateNotifications()
    }

    @Test
    fun `when there are no images no prefetch is triggered`() = testScope.runTest {
        mockResponse(
            mockOffer("success", -1, 1, iconUrl = "", panel = mockFullScreenImagePanel(null, null)),
        )
        notificationManager.updateNotifications()

        coEvery { mockImagePrefetcher.prefetch(any()) } returns false

        Assert.assertEquals(listOf("success"), notificationManager.activeListFlow.first().map { it.id })
        coVerify { mockImagePrefetcher wasNot called }
    }

    @Test
    fun `prefetch is called each time activeListFlow is collected`() = testScope.runTest {
        mockResponse(
            mockOffer("id", -1, 1, iconUrl = "url")
        )
        notificationManager.updateNotifications()
        verify(exactly = 1) { mockImagePrefetcher.prefetch("url") }
        notificationManager.activeListFlow.first()
        verify(exactly = 2) { mockImagePrefetcher.prefetch("url") }
        notificationManager.activeListFlow.first()
        verify(exactly = 3) { mockImagePrefetcher.prefetch("url") }
    }

    @Test
    fun `when user plan changes then notifications are updated`() = testScope.runTest {
        infoChangeFlow.emit(emptyList())

        coVerify { mockPeriodicUpdateManager.executeNow<Any, Any>(match { it.id == "in-app notifications" }) }
    }

    @Test
    fun `when notifications are disabled then plan changes don't trigger update`() = testScope.runTest {
        appConfigFlow.value = with(appConfigFlow.value) {
            copy(featureFlags = featureFlags.copy(pollApiNotifications = false))
        }

        infoChangeFlow.emit(emptyList())

        coVerify(exactly = 0) {
            mockPeriodicUpdateManager.executeNow<Any, Any>(match { it.id == "in-app notifications" })
        }
    }

    @Test
    fun `notifications fetched from API are restored on restart`() = testScope.runTest {
        mockResponse(
            mockOffer("offer 1", 0, 10),
            mockOffer("offer 2", 0, 20),
        )
        val expectedNotificationIds = listOf("offer 1", "offer 2")
        notificationManager.updateNotifications()
        assertEquals(expectedNotificationIds, notificationManager.activeListFlow.first().map { it.id })

        val newNotificationManager = createNotificationsManager()
        val notificationIds = newNotificationManager.activeListFlow.first().map { it.id }
        assertEquals(expectedNotificationIds, notificationIds)
    }

    private fun createNotificationsManager() = ApiNotificationManager(
        appContext = mockContext,
        mainScope = testScope.backgroundScope,
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
        wallClockMs = { testScope.currentTime },
        appConfig = mockAppConfig,
        api = mockApi,
        currentUser = currentUser,
        userPlanManager = mockUserPlanManager,
        generateNotificationsForIntroductoryOffers = mockk(relaxed = true),
        imagePrefetcher = mockImagePrefetcher,
        periodicUpdateManager = mockPeriodicUpdateManager,
        inForeground = flowOf(true),
        isLoggedIn = flowOf(true),
    )
}
