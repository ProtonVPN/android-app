/*
* Copyright (c) 2021 Proton AG
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
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.vpn.ConnectionParamsWireguard
import com.protonvpn.android.models.vpn.usecase.ComputeAllowedIPs
import com.protonvpn.android.models.vpn.usecase.ProvideLocalNetworks
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.test.shared.createServer
import com.protonvpn.test.shared.dummyConnectingDomain
import com.wireguard.config.Config
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WireguardParamsTests {

    @get:Rule var rule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockCertRepository: CertificateRepository

    private lateinit var localNetworks: List<String>
    private lateinit var connectionParams: ConnectionParamsWireguard

    private var v6SupportEnabled: Boolean = true

    private val vpnAppPackage = "ch.protonvpn.android"
    private val dummyKeyBase64 = "o0AixWIjxr61AwsKjrTIM+f9iHWZlWUOYZQyroX+zz4="
    private val exampleAppPackage = "com.example.app"
    private val ipv6IncludeAll = "0:0:0:0:0:0:0:0/0"
    private val splitTunnelingIncludeOnly1App = SplitTunnelingSettings(
        isEnabled = true,
        mode = SplitTunnelingMode.INCLUDE_ONLY,
        includedApps = listOf(exampleAppPackage),
        includedIps = emptyList(),
        // Add some excludes to test that they are ignored:
        excludedApps = listOf("com.example.excluded"),
        excludedIps = listOf("123.123.123.123"),
    )
    private val splitTunnelingIncludeOnly1Ip = SplitTunnelingSettings(
        isEnabled = true,
        mode = SplitTunnelingMode.INCLUDE_ONLY,
        includedApps = emptyList(),
        includedIps = listOf("1.2.3.4"),
        // Add some excludes to test that they are ignored:
        excludedApps = listOf("com.example.excluded"),
        excludedIps = listOf("123.123.123.123"),
    )
    private val splitTunnelingExcludeOnly1App = SplitTunnelingSettings(
        isEnabled = true,
        mode = SplitTunnelingMode.EXCLUDE_ONLY,
        excludedApps = listOf(exampleAppPackage),
        excludedIps = emptyList(),
        // Add some includes to test that they are ignored:
        includedApps = listOf("com.example.excluded"),
        includedIps = listOf("123.123.123.123"),
    )
    private val splitTunnelingExcludeOnly1Ip = SplitTunnelingSettings(
        isEnabled = true,
        mode = SplitTunnelingMode.EXCLUDE_ONLY,
        excludedApps = emptyList(),
        excludedIps = listOf("1.2.3.4"),
        // Add some includes to test that they are ignored:
        includedApps = listOf("com.example.excluded"),
        includedIps = listOf("123.123.123.123"),
    )
    private val splitTunnelingDisabled = SplitTunnelingSettings(
        isEnabled = false,
        includedApps = listOf("com.example.excluded"),
        includedIps = listOf("123.123.123.123"),
        excludedApps = listOf("com.example.excluded"),
        excludedIps = listOf("123.123.123.123"),
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        coEvery { mockCertRepository.getX25519Key(any()) } returns dummyKeyBase64

        v6SupportEnabled = true
        localNetworks = emptyList<String>()
        connectionParams = createConnectionParams(ConnectIntent.Fastest)
    }

    @Test
    fun `split tunneling include - apps only configuration allows all IPs`() = runTest {
        val settings = LocalUserSettings(splitTunneling = splitTunnelingIncludeOnly1App)
        val config = getTunnelConfigInTest(settings)
        val allowedIps = config.peers.first().allowedIps.map { it.toString() }
        assertEquals(listOf("0.0.0.0/0", ipv6IncludeAll), allowedIps)
    }

    @Test
    fun `split tunneling include - apps only configuration supports allow LAN`() = runTest {
        val settings = LocalUserSettings(
            splitTunneling = splitTunnelingIncludeOnly1App,
            lanConnections = true,
        )
        localNetworks = listOf("10.0.0.0/8")
        val config = getTunnelConfigInTest(settings)
        val allowedIps = config.peers.first().allowedIps.map { it.toString() }

        assertEquals(
            setOf("0.0.0.0/5", "8.0.0.0/7", "10.2.0.1/32", "11.0.0.0/8", "12.0.0.0/6",
                "16.0.0.0/4", "32.0.0.0/3", "64.0.0.0/2", "128.0.0.0/1", "0:0:0:0:0:0:0:0/0"),
            allowedIps.toSet()
        )
    }

    @Test
    fun `split tunneling include - Proton VPN added to included apps`() = runTest {
        val settings = LocalUserSettings(splitTunneling = splitTunnelingIncludeOnly1App)
        val config = getTunnelConfigInTest(settings)
        val includedApps = config.`interface`.includedApplications
        val excludedApps = config.`interface`.excludedApplications
        assertEquals(setOf(exampleAppPackage, vpnAppPackage), includedApps.toSet())
        assertTrue(excludedApps.isEmpty())
    }

    @Test
    fun `split tunneling include - IPs only configuration allows all apps`() = runTest {
        val settings = LocalUserSettings(splitTunneling = splitTunnelingIncludeOnly1Ip)
        val config = getTunnelConfigInTest(settings)
        with(config.`interface`) {
            assertTrue(includedApplications.isEmpty())
            assertTrue(excludedApplications.isEmpty())
        }
    }

    @Test
    fun `split tunneling include - local agent IP added to included IPs`() = runTest {
        val settings = LocalUserSettings(splitTunneling = splitTunnelingIncludeOnly1Ip)
        val config = getTunnelConfigInTest(settings)
        val allowedIps = config.peers.first().allowedIps.map { it.toString() }
        assertEquals(setOf("1.2.3.4/32", "10.2.0.1/32", "2a07:b944:0:0:0:0:2:1/128"), allowedIps.toSet())
    }

    @Test
    fun `split tunneling disabled`() = runTest {
        val settings = LocalUserSettings(splitTunneling = splitTunnelingDisabled)
        val config = getTunnelConfigInTest(settings)
        with(config.`interface`) {
            assertTrue(includedApplications.isEmpty())
            assertTrue(excludedApplications.isEmpty())
        }
        val allowedIps = config.peers.first().allowedIps.map { it.toString() }
        assertEquals(setOf("0.0.0.0/0", ipv6IncludeAll), allowedIps.toSet())
    }

    @Test
    fun `split tunneling exclude - exclude only apps`() = runTest {
        val settings = LocalUserSettings(splitTunneling = splitTunnelingExcludeOnly1App)
        val config = getTunnelConfigInTest(settings)
        with (config.`interface`) {
            assertTrue(includedApplications.isEmpty())
            assertEquals(setOf(exampleAppPackage), excludedApplications)
        }
        val allowedIps = config.peers.first().allowedIps.map { it.toString() }
        assertEquals(setOf("0.0.0.0/0", ipv6IncludeAll), allowedIps.toSet())
    }

    @Test
    fun `Guest Hole - only VPN app goes via the tunnel (split tunneling settings ignored)`() = runTest {
        suspend fun test(splitTunneling: SplitTunnelingSettings) {
            val config = getTunnelConfigInTest(LocalUserSettings(splitTunneling = splitTunneling))
            with(config.`interface`) {
                assertEquals(setOf(vpnAppPackage), includedApplications)
                assertTrue(excludedApplications.isEmpty())
            }
            val allowedIps = config.peers.first().allowedIps.map { it.toString() }
            assertEquals(setOf("0.0.0.0/0", ipv6IncludeAll), allowedIps.toSet())
        }

        connectionParams = createConnectionParams(AnyConnectIntent.GuestHole("dummyServerId"))
        test(splitTunnelingIncludeOnly1Ip)
        test(splitTunnelingIncludeOnly1App)
        test(splitTunnelingExcludeOnly1Ip)
        test(splitTunnelingExcludeOnly1App)
    }

    private suspend fun getTunnelConfigInTest(
        settings: LocalUserSettings,
    ): Config = connectionParams.getTunnelConfig(
        myPackageName = vpnAppPackage,
        userSettings = settings,
        sessionId = null,
        certificateRepository = mockCertRepository,
        computeAllowedIPs = ComputeAllowedIPs(ProvideLocalNetworks { localNetworks }),
    )

    private fun createConnectionParams(connectIntent: AnyConnectIntent, ipV6Server: Boolean = true) =
        ConnectionParamsWireguard(
            connectIntent,
            createServer(isIpV6Supported = ipV6Server),
            51820,
            dummyConnectingDomain,
            "1.1.1.1",
            TransmissionProtocol.UDP,
            ipv6SettingEnabled = true,
        )
}
