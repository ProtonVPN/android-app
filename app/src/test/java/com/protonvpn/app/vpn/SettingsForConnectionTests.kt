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

import app.cash.turbine.turbineScope
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.profiles.data.toProfile
import com.protonvpn.android.redesign.recents.data.ProtocolSelectionData
import com.protonvpn.android.redesign.recents.data.SettingsOverrides
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.settings.data.ApplyEffectiveUserSettings
import com.protonvpn.android.settings.data.CustomDnsSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.settings.data.SettingsFeatureFlagsFlow
import com.protonvpn.android.theme.FakeIsLightThemeFeatureFlagEnabled
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.usecases.FakeIsIPv6FeatureFlagEnabled
import com.protonvpn.mocks.FakeGetProfileById
import com.protonvpn.mocks.FakeIsLanDirectConnectionsFeatureFlagEnabled
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createProfileEntity
import com.protonvpn.test.shared.createServer
import io.mockk.mockk
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
    private lateinit var rawSettingsFlow: MutableStateFlow<LocalUserSettings>
    private lateinit var profileById: FakeGetProfileById
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private val lanEnabledFF = MutableStateFlow(true)

    private lateinit var settingsForConnection: SettingsForConnection

    @Before
    fun setup() {
        testScope = TestScope()
        testUserProvider = TestCurrentUserProvider(vpnUser = TestUser.plusUser.vpnUser)
        currentUser = CurrentUser(testUserProvider)
        rawSettingsFlow = MutableStateFlow(LocalUserSettings.Default)
        profileById = FakeGetProfileById()
        vpnStateMonitor = VpnStateMonitor()
        settingsForConnection = SettingsForConnection(
            rawSettingsFlow = rawSettingsFlow,
            getProfileById = profileById,
            applyEffectiveUserSettings = ApplyEffectiveUserSettings(
                mainScope = testScope.backgroundScope,
                currentUser = currentUser,
                isTv = mockk(relaxed = true),
                flags = SettingsFeatureFlagsFlow(
                    isIPv6FeatureFlagEnabled = FakeIsIPv6FeatureFlagEnabled(true),
                    isDirectLanConnectionsFeatureFlagEnabled = FakeIsLanDirectConnectionsFeatureFlagEnabled(lanEnabledFF),
                    isLightThemeFeatureFlagEnabled = FakeIsLightThemeFeatureFlagEnabled(true),
                )
            ),
            vpnStatusProviderUI = VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor)
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
        rawSettingsFlow.value = LocalUserSettings.Default.copy(
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
        rawSettingsFlow.value = LocalUserSettings.Default
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

    @Test
    fun `getFlowForCurrentConnection emits updates to profile settings overrides`() = testScope.runTest {
        rawSettingsFlow.value = LocalUserSettings.Default
        val overrides = createSettingsOverrides(
            netShield = NetShieldProtocol.DISABLED
        )
        val profile = createProfileEntity(
            connectIntent = ConnectIntent.Fastest.copy(settingsOverrides = overrides)
        ).toProfile()
        profileById.set(profile)
        val connectionParams = ConnectionParams(
            connectIntentData = profile.connectIntent.toData(),
            server = createServer(),
            connectingDomain = null,
            protocol = VpnProtocol.WireGuard
        )

        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        turbineScope {
            val settingsForConnectionTurbine =
                settingsForConnection.getFlowForCurrentConnection().testIn(backgroundScope)
            val before = settingsForConnectionTurbine.awaitItem()
            assertEquals(NetShieldProtocol.DISABLED, before.connectionSettings.netShield)

            val updatedOverrides = overrides.copy(netShield = NetShieldProtocol.ENABLED_EXTENDED)
            val updatedProfile = profile.copy(
                connectIntent = ConnectIntent.Fastest.copy(settingsOverrides = updatedOverrides)
            )
            profileById.set(updatedProfile)

            val after = settingsForConnectionTurbine.awaitItem()
            assertEquals(NetShieldProtocol.ENABLED_EXTENDED, after.connectionSettings.netShield)
        }
    }

    @Test
    fun `NetShield disabled for essentials plan`() = testScope.runTest {
        testUserProvider.vpnUser = TestUser.businessEssential.vpnUser
        rawSettingsFlow.value = LocalUserSettings.Default
        val overrides = SettingsOverrides(
            protocolData = ProtocolSelectionData(VpnProtocol.OpenVPN, TransmissionProtocol.TCP),
            netShield = NetShieldProtocol.ENABLED_EXTENDED,
            randomizedNat = false,
            lanConnections = false,
            lanConnectionsAllowDirect = false,
            customDns = CustomDnsSettings(false)
        )
        assertEquals(
            NetShieldProtocol.DISABLED,
            settingsForConnection.getFor(ConnectIntent.Fastest.copy(settingsOverrides = overrides)).netShield
        )
    }

    private fun createSettingsOverrides(
        protocol: ProtocolSelection? = ProtocolSelection.SMART,
        netShield: NetShieldProtocol? = NetShieldProtocol.ENABLED_EXTENDED,
        randomizedNat: Boolean? = true,
        lanConnections: Boolean? = false,
        lanConnectionsAllowDirect: Boolean? = false,
        customDns: CustomDnsSettings? = CustomDnsSettings()
    ) = SettingsOverrides(
        protocolData = protocol?.toData(),
        netShield = netShield,
        randomizedNat = randomizedNat,
        lanConnections = lanConnections,
        lanConnectionsAllowDirect = lanConnectionsAllowDirect,
        customDns = customDns
    )
}
