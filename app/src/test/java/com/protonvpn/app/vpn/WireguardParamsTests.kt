/*
* Copyright (c) 2021 Proton Technologies AG
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
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.test.shared.dummyConnectingDomain
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WireguardParamsTests {

    @get:Rule var rule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockCertRepository: CertificateRepository

    private lateinit var connectionParams: ConnectionParamsWireguard

    private val vpnAppPackage = "ch.protonvpn.android"
    private val dummyKeyBase64 = "o0AixWIjxr61AwsKjrTIM+f9iHWZlWUOYZQyroX+zz4="
    private val exampleAppPackage = "com.example.app"
    private val ipv6Include = "2000:0:0:0:0:0:0:0/3"
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

        connectionParams = createConnectionParams(ConnectIntent.Fastest)
    }

    @Test
    fun testAllowedIpsCalculation() {
        val defaultAllowedIps = "0.0.0.0/0"
        val ipToExclude = "134.209.78.99"
        val allowedIpsWithExclusion = "0.0.0.0/1, 128.0.0.0/6, 132.0.0.0/7, 134.0.0.0/9, 134.128.0.0/10," +
            " 134.192.0.0/12, 134.208.0.0/16, 134.209.0.0/18, 134.209.64.0/21, 134.209.72.0/22," +
            " 134.209.76.0/23, 134.209.78.0/26, 134.209.78.64/27, 134.209.78.96/31, 134.209.78.98/32," +
            " 134.209.78.100/30, 134.209.78.104/29, 134.209.78.112/28, 134.209.78.128/25, 134.209.79.0/24," +
            " 134.209.80.0/20, 134.209.96.0/19, 134.209.128.0/17, 134.210.0.0/15, 134.212.0.0/14," +
            " 134.216.0.0/13, 134.224.0.0/11, 135.0.0.0/8, 136.0.0.0/5, 144.0.0.0/4, 160.0.0.0/3, 192.0.0.0/2"

        assertEquals(
            defaultAllowedIps,
            connectionParams.excludedIpsToAllowedIps(emptyList()).joinToString { it.toCanonicalString() }
        )
        assertEquals(
            allowedIpsWithExclusion,
            connectionParams.excludedIpsToAllowedIps(listOf(ipToExclude)).joinToString { it.toCanonicalString() }
        )
        assertEquals(
            "0.0.0.0/5, 8.0.0.0/7, 10.0.0.0/23, 10.0.3.0/24, 10.0.4.0/22, 10.0.8.0/21, 10.0.16.0/20, " +
                "10.0.32.0/19, 10.0.64.0/18, 10.0.128.0/17, 10.1.0.0/16, 10.2.0.0/15, 10.4.0.0/14, 10.8.0.0/13, " +
                "10.16.0.0/12, 10.32.0.0/11, 10.64.0.0/10, 10.128.0.0/9, 11.0.0.0/8, 12.0.0.0/6, " +
                "16.0.0.0/4, 32.0.0.0/3, 64.0.0.0/2, 128.0.0.0/1",
            connectionParams.excludedIpsToAllowedIps(listOf("10.0.2.16/24")).joinToString { it.toCanonicalString() }
        )
    }

    @Test
    fun `loopback addresses are never in allowed IPs`() {
        val allowedIps = connectionParams.excludedIpsToAllowedIps(listOf("126.0.0.1"))
        assertFalse(allowedIps.any { it.toCanonicalString().startsWith("127.") })
    }

    @Test
    fun `split tunneling include - apps only configuration allows all IPs`() = runTest {
        val settings = LocalUserSettings(splitTunneling = splitTunnelingIncludeOnly1App)
        val config = getTunnelConfigInTest(settings)
        val allowedIps = config.peers.first().allowedIps.map { it.toString() }
        assertEquals(listOf("0.0.0.0/0", ipv6Include), allowedIps)
    }

    @Test
    fun `split tunneling include - apps only configuration supports allow LAN`() = runTest {
        val settings = LocalUserSettings(
            splitTunneling = splitTunnelingIncludeOnly1App,
            lanConnections = true,
        )
        val localNetworks = listOf("10.0.0.0/32")
        val config = getTunnelConfigInTest(settings, localNetworks)
        val allowedIps = config.peers.first().allowedIps.map { it.toString() }

        val expectedIpv4s = connectionParams.excludedIpsToAllowedIps(localNetworks)
            .map { it.toString() }
            .toSet()
        assertEquals(expectedIpv4s + ipv6Include, allowedIps.toSet())
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
        assertEquals(setOf("1.2.3.4/32", "10.2.0.1/32", ipv6Include), allowedIps.toSet())
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
        assertEquals(setOf("0.0.0.0/0", ipv6Include), allowedIps.toSet())
    }

    @Test
    fun `split tunneling disabled - allow LAN connections`() = runTest {
        val settings = LocalUserSettings(
            splitTunneling = splitTunnelingDisabled,
            lanConnections = true,
        )
        val localNetworks = listOf("10.0.0.0/32")
        val config = getTunnelConfigInTest(settings, localNetworks)
        val expectedIpv4s = connectionParams.excludedIpsToAllowedIps(localNetworks)
            .map { it.toString() }
            .toSet()
        val allowedIps = config.peers.first().allowedIps.map { it.toString() }
        assertEquals(expectedIpv4s + ipv6Include, allowedIps.toSet())
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
        assertEquals(setOf("0.0.0.0/0", ipv6Include), allowedIps.toSet())
    }

    @Test
    fun `split tunneling exclude - exclude only ips`() = runTest {
        val settings = LocalUserSettings(splitTunneling = splitTunnelingExcludeOnly1Ip)
        val config = getTunnelConfigInTest(settings)

        with(config.`interface`) {
            assertTrue(includedApplications.isEmpty())
            assertTrue(excludedApplications.isEmpty())
        }
        val expectedIpv4s = connectionParams.excludedIpsToAllowedIps(listOf("1.2.3.4/32"))
            .map { it.toString() }
            .toSet()
        val allowedIps = config.peers.first().allowedIps.map { it.toString() }
        assertEquals(expectedIpv4s + ipv6Include, allowedIps.toSet())
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
            assertEquals(setOf("0.0.0.0/0", ipv6Include), allowedIps.toSet())
        }

        connectionParams = createConnectionParams(AnyConnectIntent.GuestHole("dummyServerId"))
        test(splitTunnelingIncludeOnly1Ip)
        test(splitTunnelingIncludeOnly1App)
        test(splitTunnelingExcludeOnly1Ip)
        test(splitTunnelingExcludeOnly1App)
    }

    private suspend fun getTunnelConfigInTest(
        settings: LocalUserSettings,
        localNetworks: List<String> = emptyList()
    ) = connectionParams.getTunnelConfig(
        vpnAppPackage,
        localNetworksProvider = { _ -> localNetworks },
        settings,
        sessionId = null,
        certificateRepository = mockCertRepository,
    )

    private fun createConnectionParams(connectIntent: AnyConnectIntent) =
        ConnectionParamsWireguard(
            connectIntent,
            mockk(),
            51820,
            dummyConnectingDomain,
            "1.1.1.1",
            TransmissionProtocol.UDP
        )
}
