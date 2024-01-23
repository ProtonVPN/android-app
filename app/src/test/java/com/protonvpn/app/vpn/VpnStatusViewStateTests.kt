/*
 * Copyright (c) 2023 Proton AG
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
package com.protonvpn.app.vpn

import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.netshield.NetShieldStats
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ui.StatusBanner
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewState
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewStateFlow
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createServer
import com.protonvpn.test.shared.mockVpnUser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VpnStatusViewStateFlowTest {

    @MockK
    private lateinit var vpnStatusProviderUi: VpnStatusProviderUI

    @MockK
    private lateinit var vpnConnectionManager: VpnConnectionManager

    @RelaxedMockK
    private lateinit var mockCurrentUser: CurrentUser

    private lateinit var testScope: TestScope
    private lateinit var settingsFlow: MutableStateFlow<LocalUserSettings>
    private lateinit var serverListUpdaterPrefs: ServerListUpdaterPrefs
    private lateinit var vpnStatusViewStateFlow: VpnStatusViewStateFlow
    private val server: Server = createServer()
    private val connectionParams = ConnectionParams(ConnectIntent.Default, server, null, null)
    private lateinit var statusFlow: MutableStateFlow<VpnStatusProviderUI.Status>
    private lateinit var netShieldStatsFlow: MutableStateFlow<NetShieldStats>
    private lateinit var vpnUserFlow: MutableStateFlow<VpnUser?>

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val testCoroutineScheduler = TestCoroutineScheduler()
        val testDispatcher = UnconfinedTestDispatcher(testCoroutineScheduler)
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)

        serverListUpdaterPrefs = ServerListUpdaterPrefs(MockSharedPreferencesProvider())
        serverListUpdaterPrefs.ipAddress = "1.1.1.1"
        serverListUpdaterPrefs.lastKnownCountry = "US"

        statusFlow = MutableStateFlow(VpnStatusProviderUI.Status(VpnState.Connected, connectionParams))
        every { vpnStatusProviderUi.uiStatus } returns statusFlow
        netShieldStatsFlow = MutableStateFlow(NetShieldStats())
        every { vpnConnectionManager.netShieldStats } returns netShieldStatsFlow

        vpnUserFlow = MutableStateFlow(TestUser.plusUser.vpnUser)
        every { mockCurrentUser.vpnUserFlow } returns vpnUserFlow
        mockCurrentUser.mockVpnUser { vpnUserFlow.value }
        settingsFlow = MutableStateFlow(LocalUserSettings.Default)
        val effectiveUserSettings = EffectiveCurrentUserSettings(testScope.backgroundScope, settingsFlow)
        vpnStatusViewStateFlow = VpnStatusViewStateFlow(
            vpnStatusProviderUi,
            serverListUpdaterPrefs,
            vpnConnectionManager,
            effectiveUserSettings,
            mockCurrentUser,
            flowOf(null)
        )
    }

    @Test
    fun `change in vpnStatus changes StatusViewState flow`() = runTest {
        statusFlow.emit(VpnStatusProviderUI.Status(VpnState.Disabled, null))
        assert(vpnStatusViewStateFlow.first() is VpnStatusViewState.Disabled)
        statusFlow.emit(VpnStatusProviderUI.Status(VpnState.Connecting, connectionParams))
        assert(vpnStatusViewStateFlow.first() is VpnStatusViewState.Connecting)
        statusFlow.emit(VpnStatusProviderUI.Status(VpnState.Connected, connectionParams))
        assert(vpnStatusViewStateFlow.first() is VpnStatusViewState.Connected)
    }

    @Test
    fun `change in netShield stats are reflected in StatusViewState flow`() = runTest {
        statusFlow.emit(VpnStatusProviderUI.Status(VpnState.Connected, connectionParams))
        assert(vpnStatusViewStateFlow.first() is VpnStatusViewState.Connected)
        netShieldStatsFlow.emit(NetShieldStats(3, 3, 3000))
        val netShieldStats =
            ((vpnStatusViewStateFlow.first() as VpnStatusViewState.Connected).banner as StatusBanner.NetShieldBanner).netShieldState.netShieldStats
        assert(netShieldStats.adsBlocked == 3L)
        assert(netShieldStats.trackersBlocked == 3L)
        assert(netShieldStats.savedBytes == 3000L)
    }
}
