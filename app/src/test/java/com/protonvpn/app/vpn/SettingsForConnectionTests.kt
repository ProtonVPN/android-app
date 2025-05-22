/*
 * Copyright (c) 2025 Proton AG
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

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.redesign.recents.data.ProtocolSelectionData
import com.protonvpn.android.redesign.recents.data.SettingsOverrides
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.settings.data.CustomDnsSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.mocks.FakeGetProfileById
import com.protonvpn.mocks.FakeIsLanDirectConnectionsFeatureFlagEnabled
import com.protonvpn.test.shared.TestCurrentUserProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsForConnectionTests {

    private lateinit var testScope: TestScope
    private lateinit var testUserProvider: TestCurrentUserProvider
    private lateinit var currentUser: CurrentUser
    private lateinit var effectiveSettingsFlow: MutableStateFlow<LocalUserSettings>
    private lateinit var profileById: FakeGetProfileById
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private val lanEnabledFF = MutableStateFlow(true)

    private lateinit var settingsForConnection: SettingsForConnection

    @Before
    fun setup() {
        testScope = TestScope()
        testUserProvider = TestCurrentUserProvider(vpnUser = null)
        currentUser = CurrentUser(testUserProvider)
        effectiveSettingsFlow = MutableStateFlow(LocalUserSettings.Default)
        profileById = FakeGetProfileById()
        vpnStateMonitor = VpnStateMonitor()
        settingsForConnection = SettingsForConnection(
            EffectiveCurrentUserSettings(testScope.backgroundScope, effectiveSettingsFlow),
            profileById,
            FakeIsLanDirectConnectionsFeatureFlagEnabled(lanEnabledFF),
            VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor)
        )
    }

    @Test
    fun `settingsForConnection return effective settings for null intent`() = testScope.runTest {
        assertEquals(LocalUserSettings.Default, settingsForConnection.getFor(null))
    }

    @Test
    fun `settingsForConnection return effective settings for intent with no overrides`() = testScope.runTest {
        assertEquals(LocalUserSettings.Default, settingsForConnection.getFor(ConnectIntent.Fastest))
    }

    @Test
    fun `overrides from intent are applied`() = testScope.runTest {
        effectiveSettingsFlow.value = LocalUserSettings.Default.copy(
            protocol = ProtocolSelection.STEALTH,
            netShield = NetShieldProtocol.ENABLED_EXTENDED,
            randomizedNat = true,
            lanConnections = true,
            lanConnectionsAllowDirect = true,
            customDns = CustomDnsSettings(true)
        )
        val overrides = SettingsOverrides(
            protocolData = ProtocolSelectionData(VpnProtocol.OpenVPN, TransmissionProtocol.TCP),
            netShield = NetShieldProtocol.DISABLED,
            randomizedNat = false,
            lanConnections = false,
            lanConnectionsAllowDirect = false,
            customDns = CustomDnsSettings(false)
        )
        assertEquals(LocalUserSettings.Default.copy(
            protocol = ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.TCP),
            netShield = NetShieldProtocol.DISABLED,
            randomizedNat = false,
            lanConnections = false,
            lanConnectionsAllowDirect = false,
            customDns = CustomDnsSettings(false)
        ), settingsForConnection.getFor(ConnectIntent.Fastest.copy(settingsOverrides = overrides)))
    }

    @Test
    fun `overrides are not applied if feature flags are disabled`() = testScope.runTest {
        effectiveSettingsFlow.value = LocalUserSettings.Default
        val overrides = SettingsOverrides(
            lanConnections = true,
            lanConnectionsAllowDirect = true,
            customDns = CustomDnsSettings(),
            protocolData = null,
            netShield = null,
            randomizedNat = null,
        )
        lanEnabledFF.value = false
        assertEquals(
            LocalUserSettings.Default.copy(lanConnections = true),
            settingsForConnection.getFor(ConnectIntent.Fastest.copy(settingsOverrides = overrides))
        )
    }
}
