/*
 * Copyright (c) 2019 Proton Technologies AG
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

import android.app.Activity
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.ApiNotification
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.ApiNotificationOffer
import com.protonvpn.android.appconfig.ApiNotificationsResponse
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.appconfig.ImagePrefetcher
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.promooffers.PromoOfferImage
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.test.shared.ApiNotificationTestHelper.mockFullScreenImagePanel
import com.protonvpn.test.shared.ApiNotificationTestHelper.mockOffer
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestDispatcherProvider
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import me.proton.core.network.domain.ApiResult
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ApiNotificationManagerTests {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockAppConfig: AppConfig
    @RelaxedMockK
    private lateinit var mockContext: Context
    @MockK
    private lateinit var mockImagePrefercher: ImagePrefetcher
    @MockK
    private lateinit var mockCurrentUser: CurrentUser
    @MockK
    private lateinit var mockUserPlanManager: UserPlanManager
    @MockK
    private lateinit var mockApi: ProtonApiRetroFit
    @MockK
    private lateinit var mockForegroundActivityTracker: ForegroundActivityTracker

    private lateinit var appFeaturesPrefs: AppFeaturesPrefs
    private lateinit var testScope: TestCoroutineScope
    private lateinit var foregroundActivityFlow: MutableStateFlow<Activity?>
    private lateinit var planChangeFlow: MutableSharedFlow<List<UserPlanManager.InfoChange>>

    private fun mockResponse(vararg items: ApiNotification) {
        coEvery {
            mockApi.getApiNotifications(any(), any(), any())
        } returns ApiResult.Success(ApiNotificationsResponse(listOf(*items)))
    }

    private lateinit var notificationManager: ApiNotificationManager

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(MockSharedPreference())
        appFeaturesPrefs = AppFeaturesPrefs(MockSharedPreferencesProvider())
        appFeaturesPrefs.minNextNotificationUpdateTimestamp = -TimeUnit.DAYS.toMillis(2)
        val testDispatcher = TestCoroutineDispatcher()
        testScope = TestCoroutineScope(testDispatcher)

        coEvery { mockCurrentUser.isLoggedIn() } returns true
        every { mockAppConfig.appConfigUpdateEvent } returns MutableSharedFlow()
        every { mockAppConfig.getFeatureFlags() } returns FeatureFlags(pollApiNotifications = true)
        every { mockImagePrefercher.prefetch(any()) } returns true

        foregroundActivityFlow = MutableStateFlow(null)
        every { mockForegroundActivityTracker.foregroundActivityFlow } returns foregroundActivityFlow
        planChangeFlow = MutableSharedFlow()
        every { mockUserPlanManager.infoChangeFlow } returns planChangeFlow

        mockkObject(PromoOfferImage)
        every { PromoOfferImage.getFullScreenImageMaxSizePx(any()) } returns PromoOfferImage.Size(100, 100)

        notificationManager = ApiNotificationManager(
            mockContext,
            testScope,
            TestDispatcherProvider(testDispatcher),
            { testScope.currentTime },
            mockAppConfig,
            mockApi,
            mockCurrentUser,
            mockUserPlanManager,
            appFeaturesPrefs,
            mockImagePrefercher,
            mockForegroundActivityTracker
        )
    }

    @Test
    fun testFiltering() = testScope.runBlockingTest {
        mockResponse(
            mockOffer("active1", 0L, 1L),
            mockOffer("active2", -1L, 1L),
            mockOffer("future", 1L, 2L),
            mockOffer("just ended", -1L, 0L)
        )
        notificationManager.triggerUpdateIfNeeded()
        advanceUntilIdle()

        Assert.assertEquals(listOf("active1", "active2"), notificationManager.activeListFlow.first().map { it.id })
    }

    @Test
    fun testScheduling() = testScope.runBlockingTest {
        val apiTime = currentTime / 1000
        mockResponse(
            mockOffer("past", apiTime - 2, apiTime - 1),
            mockOffer("active", apiTime - 1, apiTime + 1),
            mockOffer("future", apiTime + 1, apiTime + 2))
        notificationManager.triggerUpdateIfNeeded()
        advanceUntilIdle()

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
    fun `when image prefetch fails notification is filtered out`() = testScope.runBlockingTest {
        mockResponse(
            mockOffer("success", -1, 1, iconUrl = "urlSuccess", panel = mockFullScreenImagePanel("urlSuccess")),
            mockOffer("failure", -1, 1, iconUrl = "urlSuccess", panel = mockFullScreenImagePanel("urlFailure"))
        )

        every { mockImagePrefercher.prefetch(any()) } returns false
        every { mockImagePrefercher.prefetch("urlSuccess") } returns true

        notificationManager.triggerUpdateIfNeeded()
        advanceUntilIdle()

        Assert.assertEquals(listOf("success"), notificationManager.activeListFlow.first().map { it.id })
    }

    @Test
    fun `when there are no images no prefetch is triggered`() = testScope.runBlockingTest {
        mockResponse(
            mockOffer("success", -1, 1, iconUrl = "", panel = mockFullScreenImagePanel(null)),
        )
        notificationManager.triggerUpdateIfNeeded()
        advanceUntilIdle()

        coEvery { mockImagePrefercher.prefetch(any()) } returns false

        Assert.assertEquals(listOf("success"), notificationManager.activeListFlow.first().map { it.id })
        coVerify { mockImagePrefercher wasNot called }
    }

    @Test
    fun `prefetch is called each time activeListFlow is collected`() = testScope.runBlockingTest {
        mockResponse(
            mockOffer("id", -1, 1, iconUrl = "url")
        )
        notificationManager.triggerUpdateIfNeeded()
        advanceUntilIdle()
        verify(exactly = 1) { mockImagePrefercher.prefetch("url") }
        notificationManager.activeListFlow.first()
        advanceUntilIdle() // Prefetch is triggered asynchronously.
        verify(exactly = 2) { mockImagePrefercher.prefetch("url") }
        notificationManager.activeListFlow.first()
        advanceUntilIdle()
        verify(exactly = 3) { mockImagePrefercher.prefetch("url") }
    }

    @Test
    fun `opening an activity triggers notifications update`() = testScope.runBlockingTest {
        mockResponse()
        foregroundActivityFlow.value = mockk()
        coVerify(exactly = 1) { mockApi.getApiNotifications(any(), any(), any()) }

        advanceTimeBy(TimeUnit.DAYS.toMillis(1))

        foregroundActivityFlow.value = null
        coVerify(exactly = 1) { mockApi.getApiNotifications(any(), any(), any()) }
    }

    @Test
    fun `at least 3 hours must pass between updates`() = testScope.runBlockingTest {
        val baseRefreshInterval = TimeUnit.HOURS.toMillis(3)
        mockResponse()
        notificationManager.triggerUpdateIfNeeded()
        coVerify(exactly = 1) { mockApi.getApiNotifications(any(), any(), any()) }

        advanceTimeBy(baseRefreshInterval - 1)
        notificationManager.triggerUpdateIfNeeded()
        coVerify(exactly = 1) { mockApi.getApiNotifications(any(), any(), any()) }

        advanceTimeBy(
            1L + (baseRefreshInterval * 0.2f).toLong() // Advance 20% of the interval to account for jitter.
        )
        notificationManager.triggerUpdateIfNeeded()
        coVerify(exactly = 2) { mockApi.getApiNotifications(any(), any(), any()) }
    }

    @Test
    fun `when user plan changes notifications are fetched again`() = testScope.runBlockingTest {
        mockResponse(
            mockOffer("id", -1, 1, iconUrl = "url")
        )
        notificationManager.triggerUpdateIfNeeded()
        advanceUntilIdle()
        coVerify(exactly = 1) { mockApi.getApiNotifications(any(), any(), any()) }

        mockResponse()
        planChangeFlow.emit(emptyList())
        advanceUntilIdle()
        coVerify(exactly = 2) { mockApi.getApiNotifications(any(), any(), any()) }
        Assert.assertEquals(emptyList<ApiNotificationOffer>(), notificationManager.activeListFlow.first())
    }
}
