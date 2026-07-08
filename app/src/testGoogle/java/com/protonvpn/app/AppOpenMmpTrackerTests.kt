/*
 * Copyright (c) 2026 Proton AG
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
import com.protonvpn.android.app.AppOpenMmpTracker
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.mmp.events.MmpEventType
import com.protonvpn.android.mmp.events.usecases.SaveMmpEvent
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppOpenMmpTrackerTests {

    private val activity = Robolectric.buildActivity(Activity::class.java).create().get()

    @RelaxedMockK
    private lateinit var mockSaveMmpEvent: SaveMmpEvent

    private lateinit var localUserSettingsFlow: MutableStateFlow<LocalUserSettings>

    private lateinit var foregroundActivityTracker: ForegroundActivityTracker

    private lateinit var appFeaturesPrefs: AppFeaturesPrefs

    private lateinit var foregroundActivityFlow: MutableStateFlow<Activity?>

    private lateinit var testScope: TestScope

    private lateinit var appOpenMmpTracker: AppOpenMmpTracker

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        testScope = TestScope(context = UnconfinedTestDispatcher())

        foregroundActivityFlow = MutableStateFlow(value = null)

        foregroundActivityTracker = ForegroundActivityTracker(
            mainScope = testScope.backgroundScope,
            internalForegroundActivityFlow = foregroundActivityFlow,
        )

        localUserSettingsFlow = MutableStateFlow(value = LocalUserSettings.Default)

        val userSettings = EffectiveCurrentUserSettings(
            mainScope = testScope.backgroundScope,
            effectiveCurrentUserSettingsFlow = localUserSettingsFlow,
        )

        appFeaturesPrefs = AppFeaturesPrefs(prefsProvider = MockSharedPreferencesProvider())

        appOpenMmpTracker = AppOpenMmpTracker(
            userSettings = userSettings,
            mainScope = testScope.backgroundScope,
            now = testScope::currentTime,
            foregroundActivityTracker = { foregroundActivityTracker },
            appFeaturesPrefs = { appFeaturesPrefs },
            saveMmpEvent = { mockSaveMmpEvent },
        )
    }

    @Test
    fun `GIVEN telemetry disabled WHEN tracking app opening THEN no event is saved`() = testScope.runTest {
        localUserSettingsFlow.value = LocalUserSettings.Default.copy(telemetry = false)
        foregroundActivityFlow.value = activity
        appFeaturesPrefs.lastAppInForegroundTimestamp = null

        appOpenMmpTracker.start()

        coVerify(exactly = 0) {
            mockSaveMmpEvent.invoke(eventType = any(), isSessionRestartRequired = any())
        }
    }

    @Test
    fun `GIVEN telemetry enabled WHEN app is open fist time THEN open event is saved`() = testScope.runTest {
        localUserSettingsFlow.value = LocalUserSettings.Default.copy(telemetry = true)
        foregroundActivityFlow.value = null
        appOpenMmpTracker.start()
        val expectedEventType = MmpEventType.Open

        foregroundActivityFlow.value = activity

        coVerify(exactly = 1) {
            mockSaveMmpEvent.invoke(eventType = expectedEventType, isSessionRestartRequired = false)
        }
    }

    @Test
    fun `GIVEN telemetry enabled WHEN app is open fresh THEN open event is saved`() = testScope.runTest {
        localUserSettingsFlow.value = LocalUserSettings.Default.copy(telemetry = true)
        appFeaturesPrefs.lastAppInForegroundTimestamp = 1776772151562
        appOpenMmpTracker.start()
        val expectedEventType = MmpEventType.Open

        foregroundActivityFlow.value = activity

        coVerify(exactly = 1) {
            mockSaveMmpEvent.invoke(eventType = expectedEventType, isSessionRestartRequired = false)
        }
    }

    @Test
    fun `GIVEN telemetry enabled WHEN app comes from background to foreground before threshold THEN open event restoring session is not saved`() = testScope.runTest {
        localUserSettingsFlow.value = LocalUserSettings.Default.copy(telemetry = true)
        foregroundActivityFlow.value = activity
        val lastAppInForegroundTimestamp = 1776767728299
        appFeaturesPrefs.lastAppInForegroundTimestamp = lastAppInForegroundTimestamp
        appOpenMmpTracker.start()
        val expectedEventType = MmpEventType.Open

        coVerify(exactly = 1) {
            mockSaveMmpEvent.invoke(eventType = expectedEventType, isSessionRestartRequired = false)
        }

        advanceTimeBy(delayTimeMillis = lastAppInForegroundTimestamp)
        foregroundActivityFlow.value = null
        advanceTimeBy(delayTimeMillis = 29.minutes.inWholeMilliseconds)
        foregroundActivityFlow.value = activity
        advanceUntilIdle()

        coVerify(exactly = 0) {
            mockSaveMmpEvent.invoke(eventType = expectedEventType, isSessionRestartRequired = true)
        }
    }

    @Test
    fun `GIVEN telemetry enabled WHEN app comes from background to foreground after threshold THEN open event restoring session is saved`() = testScope.runTest {
        localUserSettingsFlow.value = LocalUserSettings.Default.copy(telemetry = true)
        foregroundActivityFlow.value = activity
        val lastAppInForegroundTimestamp = 1776767728299
        appFeaturesPrefs.lastAppInForegroundTimestamp = lastAppInForegroundTimestamp
        appOpenMmpTracker.start()
        val expectedEventType = MmpEventType.Open

        coVerify(exactly = 1) {
            mockSaveMmpEvent.invoke(eventType = expectedEventType, isSessionRestartRequired = false)
        }

        advanceTimeBy(delayTimeMillis = lastAppInForegroundTimestamp)
        foregroundActivityFlow.value = null
        advanceTimeBy(delayTimeMillis = 30.minutes.inWholeMilliseconds)
        foregroundActivityFlow.value = activity
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mockSaveMmpEvent.invoke(eventType = expectedEventType, isSessionRestartRequired = true)
        }
    }

}
