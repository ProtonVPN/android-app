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

package com.protonvpn.app.api

import com.protonvpn.android.api.DohEnabled
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.system.measureTimeMillis

@OptIn(ExperimentalCoroutinesApi::class)
class DohEnabledTests {

    private lateinit var testScope: TestScope

    private lateinit var userSettingsFlow: MutableSharedFlow<LocalUserSettings>
    private lateinit var vpnStateMonitor: VpnStateMonitor

    private lateinit var dohEnabled: DohEnabled
    private lateinit var dohProvider: DohEnabled.Provider

    private val settings = LocalUserSettings.Default

    @Before
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)

        userSettingsFlow = MutableSharedFlow(replay = 1)
        vpnStateMonitor = VpnStateMonitor()
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Disabled, null))

        dohEnabled = DohEnabled()
        dohProvider = DohEnabled.Provider(
            testScope.backgroundScope,
            dohEnabled,
            userSettingsFlow,
            vpnStateMonitor
        )
    }

    @Test
    fun `dohEnabled blocks until settings are available`() = testScope.runTest {
        launch(Dispatchers.IO) {
            delay(1_000)
            userSettingsFlow.emit(settings.copy(apiUseDoh = false))
        }
        val timePassedMs = measureTimeMillis {
            assertFalse(dohEnabled())
        }
        assertTrue(timePassedMs > 950)
    }

    @Test
    fun `setting changes are propagated to DohEnabled`() = testScope.runTest {
        userSettingsFlow.emit(settings.copy(apiUseDoh = true))
        assertTrue(dohEnabled())
        userSettingsFlow.emit(settings.copy(apiUseDoh = false))
        assertFalse(dohEnabled())
    }

    @Test
    fun `when VPN is connected DoH is disabled`() = testScope.runTest {
        userSettingsFlow.emit(settings.copy(apiUseDoh = true))
        assertTrue(dohEnabled())
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, null))
        assertFalse(dohEnabled())
    }
}
