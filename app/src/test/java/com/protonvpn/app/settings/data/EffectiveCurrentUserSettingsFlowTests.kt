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

package com.protonvpn.app.settings.data

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.settings.data.ApplyEffectiveUserSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsFlow
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.settings.data.SettingsFeatureFlagsFlow
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class EffectiveCurrentUserSettingsFlowTests {

    @MockK
    private lateinit var mockIsTv: IsTvCheck

    private lateinit var featureFlagsFlow: MutableStateFlow<SettingsFeatureFlagsFlow.Flags>
    private lateinit var rawSettingsFlow: MutableStateFlow<LocalUserSettings>
    private lateinit var testScope: TestScope
    private lateinit var testUserProvider: TestCurrentUserProvider

    private lateinit var effectiveSettingsFlow: EffectiveCurrentUserSettingsFlow

    private val freeUser = TestUser.freeUser.vpnUser
    private val plusUser = TestUser.plusUser.vpnUser

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        testScope = TestScope(UnconfinedTestDispatcher())

        featureFlagsFlow = MutableStateFlow(
            value = SettingsFeatureFlagsFlow.Flags(
                isIPv6Enabled = true,
                isDirectLanConnectionsEnabled = true,
                isTvNetShieldSettingEnabled = true,
                isTvCustomDnsSettingEnabled = true,
                isTvAutoConnectEnabled = true,
            )
        )
        testUserProvider = TestCurrentUserProvider(plusUser)
        rawSettingsFlow = MutableStateFlow(LocalUserSettings.Default)

        every { mockIsTv.invoke() } returns false

        val currentUser = CurrentUser(testUserProvider)
        effectiveSettingsFlow = EffectiveCurrentUserSettingsFlow(
            rawCurrentUserSettingsFlow = rawSettingsFlow,
            applyEffectiveUserSettings = ApplyEffectiveUserSettings(
                mainScope = testScope.backgroundScope,
                currentUser = currentUser,
                isTv = mockIsTv,
                flags = featureFlagsFlow,
            )
        )
    }

    @Test
    fun `LAN connections is disabled on TV when restricted`() = testScope.runTest {
        every { mockIsTv.invoke() } returns true
        rawSettingsFlow.update { it.copy(lanConnections = true) }
        assertTrue(effectiveSettings().lanConnections)
        testUserProvider.vpnUser = freeUser
        assertFalse(effectiveSettings().lanConnections)
    }

    @Test
    fun `LAN connection is disabled when restricted`() = testScope.runTest {
        rawSettingsFlow.update { it.copy(lanConnections = true) }
        assertTrue(effectiveSettings().lanConnections)
        testUserProvider.vpnUser = freeUser
        assertFalse(effectiveSettings().lanConnections)
    }

    @Test
    fun `LAN connections matches raw setting on mobile`() = testScope.runTest {
        rawSettingsFlow.update { it.copy(lanConnections = false) }
        assertFalse(effectiveSettings().lanConnections)
        rawSettingsFlow.update { it.copy(lanConnections = true) }
        assertTrue(effectiveSettings().lanConnections)
    }

    @Test
    fun `LAN connections matches raw setting on TV`() = testScope.runTest {
        every { mockIsTv.invoke() } returns true
        rawSettingsFlow.update { it.copy(lanConnections = false) }
        assertFalse(effectiveSettings().lanConnections)
        rawSettingsFlow.update { it.copy(lanConnections = true) }
        assertTrue(effectiveSettings().lanConnections)
    }

    @Test
    fun `NetShield only available to paying users`() = testScope.runTest {
        testUserProvider.vpnUser = freeUser
        rawSettingsFlow.update { it.copy(netShield = NetShieldProtocol.ENABLED_EXTENDED) }
        assertEquals(NetShieldProtocol.DISABLED, effectiveSettings().netShield)

        testUserProvider.vpnUser = plusUser
        assertEquals(NetShieldProtocol.ENABLED_EXTENDED, effectiveSettings().netShield)
    }

    @Test
    fun `NetShield can be disabled on TV only if FF is disabled`() = testScope.runTest {
        every { mockIsTv.invoke() } returns true
        featureFlagsFlow.update { it.copy(isTvNetShieldSettingEnabled = false ) }
        rawSettingsFlow.update { it.copy(netShield = NetShieldProtocol.DISABLED) }
        assertEquals(NetShieldProtocol.ENABLED, effectiveSettings().netShield)

        featureFlagsFlow.update { it.copy(isTvNetShieldSettingEnabled = true ) }
        assertEquals(NetShieldProtocol.DISABLED, effectiveSettings().netShield)
    }

    @Test
    fun `telemetry setting`() = testScope.runTest {
        rawSettingsFlow.update { it.copy(telemetry = true) }
        assertTrue(effectiveSettings().telemetry)

        rawSettingsFlow.update { it.copy(telemetry = false) }
        assertFalse(effectiveSettings().telemetry)
    }

    @Test
    fun `VPN Accelerator matches user setting`() = testScope.runTest {
        rawSettingsFlow.update { it.copy(vpnAccelerator = true) }
        assertTrue(effectiveSettings().vpnAccelerator)

        rawSettingsFlow.update { it.copy(vpnAccelerator = false) }
        assertFalse(effectiveSettings().vpnAccelerator)
    }

    @Test
    fun `VPN Accelerator enabled when restricted`() = testScope.runTest {
        rawSettingsFlow.update { it.copy(vpnAccelerator = false) }
        assertFalse(effectiveSettings().vpnAccelerator)
        testUserProvider.vpnUser = freeUser
        assertTrue(effectiveSettings().vpnAccelerator)
    }

    @Test
    fun `Split tunnel empty when restricted`() = testScope.runTest {
        val splitTunnel = SplitTunnelingSettings(
            isEnabled = true,
            mode = SplitTunnelingMode.EXCLUDE_ONLY,
            listOf("1.1.1.1"),
            listOf("app")
        )
        rawSettingsFlow.update { it.copy(splitTunneling = splitTunnel) }
        assertEquals(splitTunnel, effectiveSettings().splitTunneling)
        testUserProvider.vpnUser = freeUser
        assertEquals(SplitTunnelingSettings(), effectiveSettings().splitTunneling)
    }

    // The Favorite functionality is based on defaultProfileId.
    @Test
    fun `Quick Connect ignored when restricted, except on TV`() = testScope.runTest {
        val profileId = UUID.randomUUID()
        rawSettingsFlow.update { it.copy(defaultProfileId = profileId ) }
        testUserProvider.vpnUser = freeUser

        assertEquals(null, effectiveSettings().defaultProfileId)

        every { mockIsTv.invoke() } returns true
        assertEquals(profileId, effectiveSettings().defaultProfileId)
    }

    private suspend fun effectiveSettings() = effectiveSettingsFlow.first()
}
