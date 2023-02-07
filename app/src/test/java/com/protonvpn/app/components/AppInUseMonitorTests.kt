/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.app.components

import android.app.Activity
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.components.AppInUseMonitor
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppInUseMonitorTests {

    @MockK
    private lateinit var mockForegroundActivityTracker: ForegroundActivityTracker
    @MockK
    private lateinit var mockActivity: Activity

    private lateinit var foregroundActivityFlow: MutableStateFlow<Activity?>
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var prefs: AppFeaturesPrefs
    private lateinit var testScope: TestScope

    private lateinit var appInUseMonitor: AppInUseMonitor

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        testScope = TestScope(UnconfinedTestDispatcher())
        vpnStateMonitor = VpnStateMonitor()
        prefs = AppFeaturesPrefs(MockSharedPreferencesProvider())
        foregroundActivityFlow = MutableStateFlow(null)

        every { mockForegroundActivityTracker.foregroundActivityFlow } returns foregroundActivityFlow

        appInUseMonitor = AppInUseMonitor(
            testScope.backgroundScope,
            mockForegroundActivityTracker,
            VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor),
            { testScope.currentTime },
            prefs
        )
    }

    @Test
    fun `when no activity in foreground and VPN disconnected then app is not in use`() = testScope.runTest {
        assertFalse(appInUseMonitor.isInUseFlow.value)
    }

    @Test
    fun `when foreground activity is not null then app is in use`() = testScope.runTest {
        foregroundActivityFlow.value = mockActivity
        assertTrue(appInUseMonitor.isInUseFlow.value)
    }

    @Test
    fun `when VPN connecting or connected then app is in use`() = testScope.runTest {
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connecting, null))
        assertTrue(appInUseMonitor.isInUseFlow.value)
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, null))
        assertTrue(appInUseMonitor.isInUseFlow.value)
    }

    @Test
    fun `when app in use then wasInUseIn always returns true`() = testScope.runTest {
        foregroundActivityFlow.value = mockActivity
        advanceTimeBy(1000)
        assertTrue(appInUseMonitor.wasInUseIn(1))
    }

    @Test
    fun `when app not in use then wasInUseIn returns true for time when app stopped being in use`() = testScope.runTest {
        foregroundActivityFlow.value = mockActivity
        advanceTimeBy(1000)
        foregroundActivityFlow.value = null
        advanceTimeBy(1000)
        assertTrue(appInUseMonitor.wasInUseIn(1001))
        assertFalse(appInUseMonitor.wasInUseIn(999))
    }

    @Test
    fun `the last time app is in use is written to prefs`() = testScope.runTest {
        advanceTimeBy(1000)
        foregroundActivityFlow.value = mockActivity
        assertEquals(1000, prefs.lastAppInUseTimestamp)

        advanceTimeBy(1000)
        foregroundActivityFlow.value = null
        assertEquals(2000, prefs.lastAppInUseTimestamp)
    }
}
