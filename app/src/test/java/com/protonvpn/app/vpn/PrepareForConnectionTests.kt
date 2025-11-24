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

import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.DefaultPorts
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.usecase.GetConnectingDomain
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.api.ConnectingDomain
import com.protonvpn.android.servers.api.ServerEntryInfo
import com.protonvpn.android.servers.api.ServerLocation
import com.protonvpn.android.vpn.PrepareForConnection
import com.protonvpn.android.vpn.ServerAvailabilityCheck
import com.protonvpn.test.shared.createGetSmartProtocols
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals

private val dummyLocation = ServerLocation(0f, 0f)

private val connectingDomainTlsOnly = ConnectingDomain(
    null, mapOf("WireGuardTLS" to ServerEntryInfo("TLS")),
    "", id = "", publicKeyX25519 = "key2")
private val connectingDomainDedicatedTcp = ConnectingDomain(
    "Other", mapOf("WireGuardTCP" to ServerEntryInfo("TCP", ports = listOf(2))),
    "", id = "", publicKeyX25519 = "key1")
private val connectingDomainNoDedicatedEntry =
    ConnectingDomain("", null, "", id = "", publicKeyX25519 = "key")
private val testServer = Server(
    "id", "DE", "DE", "DE#1",
    listOf(connectingDomainTlsOnly, connectingDomainDedicatedTcp), load = 1f, tier = 3, city = "", features = 0,
    exitLocation = dummyLocation, entryLocation = dummyLocation, score = 1.0, rawIsOnline = true
)
private val tlsOnlyServer = Server(
    "id", "DE", "DE", "DE#1", listOf(connectingDomainTlsOnly),
    load = 1f, tier = 3, city = "", features = 0, exitLocation = dummyLocation, entryLocation = dummyLocation, score = 1.0, rawIsOnline = true
)
private val testServerNoDedicatedEntry = Server(
    "id", "DE", "DE", "DE#3", listOf(connectingDomainNoDedicatedEntry),
    load = 1f, tier = 3, city = "", features = 0, exitLocation = dummyLocation, entryLocation = dummyLocation, score = 1.0, rawIsOnline = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class PrepareForConnetionTests {

    @MockK
    private lateinit var appConfig: AppConfig

    @RelaxedMockK
    private lateinit var serverAvailabilityCheck: ServerAvailabilityCheck

    private lateinit var prepareForConnetion: PrepareForConnection

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkObject(Random)
        every { Random.nextInt(any()) } returns 0 // nosemgrep

        coEvery {
            serverAvailabilityCheck.pingInParallel(any(), true)
        } answers { firstArg() }

        every {
            appConfig.getWireguardPorts()
        } returns DefaultPorts(udpPorts = listOf(10), tcpPorts = listOf(0), tlsPortsInternal = listOf(1))
        val supportsProtocol = SupportsProtocol(createGetSmartProtocols())
        val getConnectingDomain = GetConnectingDomain(supportsProtocol)
        prepareForConnetion = PrepareForConnection(appConfig, serverAvailabilityCheck, getConnectingDomain)
    }

    @Test
    fun `dedicated IP and ports are used for protocol`() = runTest {
        val result = prepareForConnetion.prepare(
            testServer, VpnProtocol.WireGuard, setOf(
                TransmissionProtocol.UDP, TransmissionProtocol.TCP, TransmissionProtocol.TLS),
            scan = true, Int.MAX_VALUE, true, 0, true)
        assertEquals(listOf(
            PrepareForConnection.ProtocolInfo(connectingDomainDedicatedTcp, TransmissionProtocol.UDP, "Other", 10),
            PrepareForConnection.ProtocolInfo(connectingDomainDedicatedTcp, TransmissionProtocol.TCP, "TCP", 2),
            PrepareForConnection.ProtocolInfo(connectingDomainTlsOnly, TransmissionProtocol.TLS, "TLS", 1),
        ), result)
    }

    @Test
    fun `TLS uses dedicated port`() = runTest {
        val result = prepareForConnetion.prepare(
            testServerNoDedicatedEntry,
            VpnProtocol.WireGuard,
            setOf(TransmissionProtocol.TCP, TransmissionProtocol.TLS),
            scan = true,
            numberOfPorts = Int.MAX_VALUE,
            waitForAll = true,
            primaryTcpPort = 0,
            includeTls = true
        )
        assertEquals(listOf(
            PrepareForConnection.ProtocolInfo(connectingDomainNoDedicatedEntry, TransmissionProtocol.TCP, "", 0),
            PrepareForConnection.ProtocolInfo(connectingDomainNoDedicatedEntry, TransmissionProtocol.TLS, "", 1),
        ), result)
    }

    @Test
    fun `TLS falls back to TCP port`() = runTest {
        every {
            appConfig.getWireguardPorts()
        } returns DefaultPorts(udpPorts = listOf(10), tcpPorts = listOf(0), tlsPortsInternal = null)
        val result = prepareForConnetion.prepare(
            testServerNoDedicatedEntry,
            VpnProtocol.WireGuard,
            setOf(TransmissionProtocol.TCP, TransmissionProtocol.TLS),
            scan = true,
            numberOfPorts = Int.MAX_VALUE,
            waitForAll = true,
            primaryTcpPort = 0,
            includeTls = true
        )
        assertEquals(listOf(
            PrepareForConnection.ProtocolInfo(connectingDomainNoDedicatedEntry, TransmissionProtocol.TCP, "", 0),
            PrepareForConnection.ProtocolInfo(connectingDomainNoDedicatedEntry, TransmissionProtocol.TLS, "", 0),
        ), result)
    }

    @Test
    fun `without scanning random compatible domain is selected`() = runTest {
        val result = prepareForConnetion.prepare(
            testServer, VpnProtocol.WireGuard, setOf(TransmissionProtocol.UDP),
            scan = false, Int.MAX_VALUE, true, 0, true)
        assertEquals(listOf(
            PrepareForConnection.ProtocolInfo(connectingDomainDedicatedTcp, TransmissionProtocol.UDP, "Other", 10),
        ), result)
        coVerify(exactly = 0) { serverAvailabilityCheck.pingInParallel(any(), any()) }
    }

    @Test
    fun `when no domain available for given protocol returns empty result`() = runTest {
        val result = prepareForConnetion.prepare(
            tlsOnlyServer, VpnProtocol.WireGuard, setOf(TransmissionProtocol.TCP),
            scan = true, Int.MAX_VALUE, true, 0, true)
        assertEquals(emptyList(), result)
    }
}
