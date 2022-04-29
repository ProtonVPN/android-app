/*
 * Copyright (c) 2022 Proton Technologies AG
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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.RatingConfig
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.bus.TrafficUpdate
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.onboarding.ReviewTracker
import com.protonvpn.android.ui.onboarding.ReviewTrackerPrefs
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.vpn.VpnFallbackResult
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.mockVpnUser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkObject
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewTrackerTests {

    private lateinit var reviewTracker: ReviewTracker

    @RelaxedMockK
    private lateinit var appConfig: AppConfig

    @RelaxedMockK
    private lateinit var vpnStateMonitor: VpnStateMonitor

    @RelaxedMockK
    private lateinit var trafficMonitor: TrafficMonitor

    @RelaxedMockK
    private lateinit var foregroundActivityTracker: ForegroundActivityTracker

    @RelaxedMockK
    private lateinit var currentUser: CurrentUser

    @RelaxedMockK
    private lateinit var vpnUser: VpnUser
    private val vpnStatus = MutableStateFlow(VpnStateMonitor.Status(VpnState.Disabled, null))
    private val vpnConnectionNotificationFlow = MutableSharedFlow<VpnFallbackResult>()
    private val trafficStatus = MutableLiveData<TrafficUpdate?>()
    private val trackerPrefs = ReviewTrackerPrefs(MockSharedPreferencesProvider())

    @get:Rule
    var rule = InstantTaskExecutorRule()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkObject(CountryTools)

        every { vpnStateMonitor.status } returns vpnStatus
        every { vpnStateMonitor.vpnConnectionNotificationFlow } returns vpnConnectionNotificationFlow
        every { trafficMonitor.trafficStatus } returns trafficStatus
        every { foregroundActivityTracker.foregroundActivity } returns mockk()
        every { appConfig.getRatingConfig() } returns RatingConfig(
            eligiblePlans = listOf("plus"),
            successfulConnectionCount = 3,
            daysSinceLastRatingCount = 1,
            daysConnectedCount = 1,
            daysFromFirstConnectionCount = 3
        )

        every { vpnUser.planName } returns "plus"
        currentUser.mockVpnUser { vpnUser }
        reviewTracker = ReviewTracker(
            mockk(relaxed = true),
            { System.currentTimeMillis() },
            TestCoroutineScope(),
            appConfig,
            currentUser,
            vpnStateMonitor,
            foregroundActivityTracker,
            trackerPrefs,
            trafficMonitor
        )
    }

    @Test
    fun `do not trigger for ineligable plans even if other conditions are met`() = runBlockingTest {
        every { vpnUser.planName } returns "free"
        addLongSession()
        assertFalse(reviewTracker.shouldRate())
    }

    @Test
    fun `do not trigger if was triggered recently`() = runBlockingTest {
        trackerPrefs.lastReviewTimestamp = System.currentTimeMillis()
        addLongSession()
        assertFalse(reviewTracker.shouldRate())
    }

    @Test
    fun `trigger if last review was triggered earlier than daysSinceLastRatingCount`() = runBlockingTest {
        val fakeTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(appConfig.getRatingConfig().daysSinceLastRatingCount.toLong())
        trackerPrefs.lastReviewTimestamp = fakeTimestamp
        addLongSession()
        assertTrue(reviewTracker.shouldRate())
    }

    @Test
    fun `long enough session should trigger review`() = runBlockingTest {
        assertFalse(reviewTracker.shouldRate())
        addLongSession()
        assertTrue(reviewTracker.shouldRate())
    }

    @Test
    fun `shouldRate never return true if app is in background`() = runBlockingTest {
        every { foregroundActivityTracker.foregroundActivity } returns null
        addLongSession()
        assertFalse(reviewTracker.shouldRate())
    }

    @Test
    fun `any vpn error resets success count`() = runBlockingTest {
        addSuccessfulConnections()
        Assert.assertEquals(appConfig.getRatingConfig().successfulConnectionCount, reviewTracker.connectionCount())

        vpnConnectionNotificationFlow.emit(VpnFallbackResult.Error(mockk()))
        Assert.assertEquals(0, reviewTracker.connectionCount())
    }

    private fun addSuccessfulConnections() {
        repeat(appConfig.getRatingConfig().successfulConnectionCount) {
            vpnStatus.value = VpnStateMonitor.Status(VpnState.Connected, mockk())
            vpnStatus.value = VpnStateMonitor.Status(VpnState.Disconnecting, mockk())
        }
    }

    private fun addLongSession() {
        trafficStatus.value = TrafficUpdate(0, 0, 0, 0, 0, 172800)
    }
}