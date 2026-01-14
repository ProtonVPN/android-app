/*
 * Copyright (c) 2022 Proton AG
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
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.bus.TrafficUpdate
import com.protonvpn.android.telemetry.CommonDimensions
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.onboarding.ReviewTracker
import com.protonvpn.android.ui.onboarding.ReviewTrackerPrefs
import com.protonvpn.android.ui.onboarding.ReviewTrackerTelemetry
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.vpn.VpnFallbackResult
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.mocks.FakeCommonDimensions
import com.protonvpn.mocks.TestTelemetryReporter
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewTrackerTests {

    private lateinit var reviewTracker: ReviewTracker

    @RelaxedMockK
    private lateinit var appConfig: AppConfig

    @RelaxedMockK
    private lateinit var trafficMonitor: TrafficMonitor

    @RelaxedMockK
    private lateinit var foregroundActivityTracker: ForegroundActivityTracker

    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var testCurrentUserProvider: TestCurrentUserProvider
    private lateinit var testScope: TestScope
    private lateinit var trackerPrefs: ReviewTrackerPrefs
    private lateinit var testTelemetry: TestTelemetryReporter
    private lateinit var testController: TestController
    private val START_TIME = 1_000_000_000L

    private val RATING_CONFIG = RatingConfig(
        eligiblePlans = listOf(TestUser.plusUser.planName),
        successfulConnectionCount = 3,
        daysSinceLastRatingCount = 1,
        daysConnectedCount = 1,
        daysFromFirstConnectionCount = 3
    )

    private var wasReviewRequested = false

    @get:Rule
    var rule = InstantTaskExecutorRule()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkObject(CountryTools)

        testScope = TestScope(UnconfinedTestDispatcher())
        testScope.advanceTimeBy(START_TIME)

        testCurrentUserProvider = TestCurrentUserProvider(TestUser.plusUser.vpnUser)
        trackerPrefs = ReviewTrackerPrefs(MockSharedPreferencesProvider(), testScope::currentTime)
        val trafficStatus = MutableLiveData<TrafficUpdate?>()
        testTelemetry = TestTelemetryReporter()
        vpnStateMonitor = VpnStateMonitor()
        every { trafficMonitor.trafficStatus } returns trafficStatus
        every { foregroundActivityTracker.foregroundActivity } returns mockk()
        every { appConfig.getRatingConfig() } returns RATING_CONFIG

        testController = TestController(testScope, trafficStatus, vpnStateMonitor)

        val commonDimensions = FakeCommonDimensions(
            mapOf(CommonDimensions.Key.USER_COUNTRY.reportedName to "US")
        )
        wasReviewRequested = false
        reviewTracker = ReviewTracker(
            testScope::currentTime,
            testScope.backgroundScope,
            appConfig,
            CurrentUser(testCurrentUserProvider),
            vpnStateMonitor,
            foregroundActivityTracker,
            trackerPrefs,
            trafficMonitor,
            ReviewTrackerTelemetry(
                TelemetryFlowHelper(testScope.backgroundScope, testTelemetry),
                commonDimensions,
                testScope::currentTime
            ),
            { _, onComplete ->
                wasReviewRequested = true
                onComplete()
            }
        )
    }

    @Test
    fun `do not trigger for ineligable plans even if other conditions are met`() = testScope.runTest {
        testCurrentUserProvider.vpnUser = TestUser.freeUser.vpnUser
        testController.addLongSession()
        assertFalse(reviewTracker.shouldRate())
        assertFalse(wasReviewRequested)
    }

    @Test
    fun `do not trigger if was triggered recently`() = testScope.runTest {
        trackerPrefs.lastReviewTimestamp = currentTime - 500
        mockOldConnectionSuccess()
        testController.addLongSession()
        assertFalse(reviewTracker.shouldRate())
    }

    @Test
    fun `trigger if last review was triggered earlier than daysSinceLastRatingCount`() = testScope.runTest {
        val fakeTimestamp = currentTime - TimeUnit.DAYS.toMillis(RATING_CONFIG.daysSinceLastRatingCount.toLong())
        mockOldConnectionSuccess()
        trackerPrefs.lastReviewTimestamp = fakeTimestamp
        testController.addLongSession()
        assertTrue(wasReviewRequested)
    }

    @Test
    fun `long enough session should trigger review`() = testScope.runTest {
        mockOldConnectionSuccess()
        assertFalse(reviewTracker.shouldRate())
        testController.addLongSession()
        assertTrue(wasReviewRequested)
    }

    @Test
    fun `trigger for old connection and multiple succesful connections review`() = testScope.runTest {
        addSuccessfulConnections()
        assertTrue(wasReviewRequested)
    }

    @Test
    fun `trigger also if connection count exceeds required limit`() = testScope.runTest {
        every { foregroundActivityTracker.foregroundActivity } returns null
        mockOldConnectionSuccess()
        repeat(RATING_CONFIG.successfulConnectionCount * 2) {
            testController.reconnect()
        }
        assertFalse(wasReviewRequested)
        every { foregroundActivityTracker.foregroundActivity } returns mockk()
        testController.connect()
        assertTrue(wasReviewRequested)
    }

    @Test
    fun `shouldRate never return true if app is in background`() = testScope.runTest {
        every { foregroundActivityTracker.foregroundActivity } returns null
        testController.addLongSession()
        assertFalse(reviewTracker.shouldRate())
    }

    @Test
    fun `any vpn error resets success count`() = testScope.runTest {
        addSuccessfulConnections()
        Assert.assertEquals(RATING_CONFIG.successfulConnectionCount, reviewTracker.connectionCount())

        vpnStateMonitor.vpnConnectionNotificationFlow.emit(VpnFallbackResult.Error(mockk(), mockk(), reason = null))
        Assert.assertEquals(0, reviewTracker.connectionCount())
    }

    @Test
    fun `do not trigger if first connection was recent`() = testScope.runTest {
        addSuccessfulConnections()
        trackerPrefs.firstConnectionTimestamp = currentTime - 500
        assertFalse(reviewTracker.shouldRate())
    }

    @Test
    fun `days since last request reported to telemetry`() = testScope.runTest {
        testController.connect()
        val minDays = with(RATING_CONFIG) { max(daysSinceLastRatingCount, daysFromFirstConnectionCount) }
        listOf(minDays, minDays, minDays + 2).forEach { delayDays ->
            testTelemetry.reset()
            testController.advanceTimeBy(delayDays.days)
            val event = testTelemetry.collectedEvents.last()
            assertEquals(delayDays.toLong(), event.values["days_since_last_prompt"])
            assertEquals("rating_booster_prompt_requested", event.eventName)
            assertEquals("vpn.any.product_prompts", event.measurementGroup)
        }
    }

    @Test
    fun `connections since last request reported to telemetry`() = testScope.runTest {
        testController.connect()
        val minDays = with(RATING_CONFIG) { max(daysSinceLastRatingCount, daysFromFirstConnectionCount) }
        advanceTimeBy(minDays.days)
        assertFalse(wasReviewRequested) // Not enough connections yet.
        repeat(RATING_CONFIG.successfulConnectionCount - 1) {
            testController.reconnect()
        }
        assertTrue(wasReviewRequested)

        assertEquals(
            RATING_CONFIG.successfulConnectionCount.toLong(),
            testTelemetry.collectedEvents.last().values["connections_since_last_prompt"]
        )

        testTelemetry.reset()
        testController.reconnect()
        testController.advanceTimeBy(RATING_CONFIG.daysSinceLastRatingCount.days)
        testController.reconnect()
        assertEquals(
            2L,
            testTelemetry.collectedEvents.last().values["connections_since_last_prompt"]
        )
    }

    private fun addSuccessfulConnections() {
        mockOldConnectionSuccess()
        repeat(RATING_CONFIG.successfulConnectionCount - 1) {
            testController.reconnect()
        }
    }

    private fun mockOldConnectionSuccess() {
        with(testController) {
            connect()
            advanceTimeBy(RATING_CONFIG.daysFromFirstConnectionCount.days)
        }
    }

    class TestController(
        private val testScope: TestScope,
        private val trafficStatusFlow: MutableLiveData<TrafficUpdate?>,
        private val vpnStateMonitor: VpnStateMonitor,
    ) {
        fun connect() {
            if (vpnStateMonitor.isConnected) {
                disconnect()
            }
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, mockk()))
            trafficStatusFlow.value = TrafficUpdate(0, testScope.currentTime, 0, 0, 0, 0, 0)
        }

        fun reconnect() = connect()

        fun disconnect() {
            vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Disconnecting, mockk()))
            trafficStatusFlow.value = null
        }

        fun advanceTimeBy(duration: Duration) = advanceTimeBy(duration.inWholeMilliseconds)

        fun advanceTimeBy(milliseconds: Long) {
            testScope.advanceTimeBy(milliseconds)
            if (vpnStateMonitor.isConnected) {
                val previousTrafficStatus = trafficStatusFlow.value
                assertNotNull(previousTrafficStatus)
                val newSessionTimeSeconds = previousTrafficStatus.sessionTimeSeconds + milliseconds / 1000
                trafficStatusFlow.value = TrafficUpdate(0, previousTrafficStatus.sessionStartTimestampMs, 0, 0, 0, 0, newSessionTimeSeconds.toInt())
            }
        }

        fun addLongSession() {
            connect()
            advanceTimeBy(2.days)
        }
    }
}
