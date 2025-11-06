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

package com.protonvpn.app.vpn.protun

import com.protonvpn.android.appconfig.DefaultPorts
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.servers.api.ConnectingDomain
import com.protonvpn.android.servers.api.ServerEntryInfo
import com.protonvpn.android.vpn.protun.PreparePeersForConnectionProTun
import com.protonvpn.test.shared.createServer
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.RelaxedMockK
import me.proton.vpn.sdk.api.Peer
import me.proton.vpn.sdk.api.VpnProtocol
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.util.Random
import kotlin.test.assertEquals

class PreparePeersForConnectionProTunTests {

    private lateinit var wireguardPorts: DefaultPorts
    private lateinit var prepare: PreparePeersForConnectionProTun
    @RelaxedMockK
    private lateinit var random: Random

    @Before
    fun setup() {
        MockKAnnotations.init()
        wireguardPorts = DefaultPorts(
            udpPorts = listOf(1, 2),
            tcpPorts = listOf(10),
            tlsPortsInternal = listOf(20)
        )
        prepare = PreparePeersForConnectionProTun(::wireguardPorts)
    }

    @Test
    fun `ports from ConnectingDomain get preference over default ports from config`() {
        val server = createServer(
            connectingDomains = listOf(ConnectingDomain(
                entryIp = "1.1.1.1",
                entryIpPerProtocol = mapOf("WireGuardUDP" to ServerEntryInfo("1.1.1.1", listOf(3))),
                entryDomain = "domain1",
                id = "id1",
                publicKeyX25519 = "key"
            ))
        )
        val protocols = setOf(TransmissionProtocol.UDP, TransmissionProtocol.TLS)
        val (_, peers) = prepare(server, protocols)!!
        assertEquals(
            listOf(Peer(
                address = InetAddress.getByName("1.1.1.1"),
                ports = mapOf(
                    VpnProtocol.WireGuardUdp to listOf(3),
                    VpnProtocol.Stealth to listOf(20)
                ),
                publicKeyX25519Base64 = server.connectingDomains.first().publicKeyX25519!!,
                0,
                server.serverId,
            )),
            peers
        )
    }

    @Test
    fun `multiple peers are created when multiple entry IPs are present`() {
        val server = createServer(
            connectingDomains = listOf(ConnectingDomain(
                entryIp = "1.1.1.1",
                entryIpPerProtocol = mapOf("WireGuardTLS" to ServerEntryInfo("2.2.2.2", listOf(21))),
                entryDomain = "domain1",
                id = "id1",
                publicKeyX25519 = "key"
            ))
        )
        val protocols = setOf(TransmissionProtocol.UDP, TransmissionProtocol.TCP, TransmissionProtocol.TLS)
        val (_, peers) = prepare(server, protocols)!!
        val publicKey = server.connectingDomains.first().publicKeyX25519!!
        assertEquals(
            setOf(
                Peer(
                    address = InetAddress.getByName("1.1.1.1"),
                    ports = mapOf(
                        VpnProtocol.WireGuardUdp to listOf(1, 2),
                        VpnProtocol.WireGuardTcp to listOf(10),
                    ),
                    publicKeyX25519Base64 = publicKey,
                    0,
                    server.serverId,
                ),
                Peer(
                    address = InetAddress.getByName("2.2.2.2"),
                    ports = mapOf(
                        VpnProtocol.Stealth to listOf(21)
                    ),
                    publicKeyX25519Base64 = publicKey,
                    0,
                    server.serverId,
                )
            ),
            peers.toSet()
        )
    }

    @Test
    fun `when no domain supports selected transmissions return null`() {
        val server = createServer(
            connectingDomains = listOf(ConnectingDomain(
                entryIp = null,
                entryIpPerProtocol = mapOf("WireGuardUDP" to ServerEntryInfo("1.1.1.1", listOf(3))),
                entryDomain = "domain1",
                id = "id1",
                publicKeyX25519 = "key"
            ))
        )
        val protocols = setOf(TransmissionProtocol.TCP, TransmissionProtocol.TLS)
        assertEquals(null, prepare(server, protocols))
    }
}