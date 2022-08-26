/*
 * Copyright (c) 2022. Proton AG
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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.SmartProtocolConfig
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.PhysicalServer
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.ProtonVpnBackendProvider
import com.protonvpn.android.vpn.VpnBackend
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.MockedServers
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProtonVpnBackendProviderTests {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    @RelaxedMockK
    private lateinit var mockOpenVpnBackend: VpnBackend
    @RelaxedMockK
    private lateinit var mockIKEv2Backend: VpnBackend
    @RelaxedMockK
    private lateinit var mockWireGuardBackend: VpnBackend

    @MockK
    private lateinit var mockAppConfig: AppConfig

    private lateinit var vpnBackendProvider: VpnBackendProvider
    private lateinit var userData: UserData

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(MockSharedPreference())
        userData = UserData.create()

        every { mockAppConfig.getSmartProtocolConfig() } returns SmartProtocolConfig(
            true, true, true
        )
        vpnBackendProvider = ProtonVpnBackendProvider(
            mockAppConfig,
            mockIKEv2Backend,
            mockOpenVpnBackend,
            mockWireGuardBackend,
            userData
        )
    }

    @Test
    fun `smart protocol uses only UDP for WireGuard`() = runBlockingTest {
        val profile = Profile.getTempProfile(ServerWrapper.makePreBakedFastest(), null)
        val server = MockedServers.server
        vpnBackendProvider.prepareConnection(
            ProtocolSelection(VpnProtocol.Smart),
            profile,
            server,
            false
        )

        coVerify { mockWireGuardBackend.prepareForConnection(profile, server, setOf(TransmissionProtocol.UDP), true, any(), any()) }
    }

    @Test
    fun `when default protocol is Smart pingAll uses only UDP for WireGuard`() = runBlockingTest {
        val server = MockedServers.server
        vpnBackendProvider.pingAll(listOf(PhysicalServer(server, server.connectingDomains.first())), null)

        coVerify { mockWireGuardBackend.prepareForConnection(any(), server, setOf(TransmissionProtocol.UDP), true, any(), any()) }
    }

    @Test
    fun `when default protocol is WireGuard TLS or TCP pingAll uses all TransmissonProtocols for WireGuard`() = runBlockingTest {
        userData.protocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS)
        val server = MockedServers.server
        vpnBackendProvider.pingAll(listOf(PhysicalServer(server, server.connectingDomains.first())), null)

        val allTransmissionProtocols = TransmissionProtocol.values().toSet()
        coVerify { mockWireGuardBackend.prepareForConnection(any(), server, allTransmissionProtocols, true, any(), any()) }
    }
}
