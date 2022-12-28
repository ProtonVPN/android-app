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

package com.protonvpn.app.telemetry

import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.SERVER_FEATURE_P2P
import com.protonvpn.android.models.vpn.SERVER_FEATURE_PARTNER_SERVER
import com.protonvpn.android.models.vpn.SERVER_FEATURE_SECURE_CORE
import com.protonvpn.android.models.vpn.SERVER_FEATURE_STREAMING
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.telemetry.Telemetry
import com.protonvpn.android.telemetry.VpnConnectionTelemetry
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.ConnectivityMonitor
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestVpnUser
import com.protonvpn.test.shared.createServer
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.EnumSet

@OptIn(ExperimentalCoroutinesApi::class)
class VpnConnectionTelemetryTests {

    @MockK
    private lateinit var mockTelemetry: Telemetry

    @MockK
    private lateinit var mockConnectivityMonitor: ConnectivityMonitor

    @MockK
    private lateinit var mockCurrentUser: CurrentUser

    private lateinit var prefs: ServerListUpdaterPrefs
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var vpnUserFlow: MutableStateFlow<VpnUser>

    private lateinit var vpnConnectionTelemetry: VpnConnectionTelemetry

    private val plusServer = createServer(
        serverName = "PLUS#1",
        features = SERVER_FEATURE_STREAMING or SERVER_FEATURE_PARTNER_SERVER,
        tier = 2,
        exitCountry = "CH"
    )
    private val secureCoreServer = createServer(
        serverName = "SC#1",
        features = SERVER_FEATURE_SECURE_CORE or SERVER_FEATURE_P2P,
        tier = 2,
        entryCountry = "CH",
        exitCountry = "PL"
    )
    private val freeServer = createServer(
        serverName = "FREE#1",
        features = SERVER_FEATURE_PARTNER_SERVER,
        tier = 0,
        exitCountry = "CH"
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        prefs = ServerListUpdaterPrefs(MockSharedPreferencesProvider())
        vpnStateMonitor = VpnStateMonitor()
        testScheduler = TestCoroutineScheduler()
        vpnUserFlow = MutableStateFlow(TestVpnUser.create())

        every { mockCurrentUser.vpnUserFlow } returns vpnUserFlow
        every {
            mockConnectivityMonitor.defaultNetworkTransports
        } returns EnumSet.of(ConnectivityMonitor.Transport.WIFI)

        val telemetryScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        vpnConnectionTelemetry = VpnConnectionTelemetry(
            telemetryScope,
            { testScheduler.currentTime },
            mockTelemetry,
            vpnStateMonitor,
            mockConnectivityMonitor,
            mockCurrentUser,
            prefs
        )
        vpnConnectionTelemetry.start()
    }

    @Test
    fun `when connection is established then vpn_connection event is sent`() {
        prefs.lastKnownCountry = "PL"
        prefs.lastKnownIsp = "Test ISP"
        val connectionParams = createConnectionParams(
            plusServer,
            ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP),
            443
        )

        connectSequence(connectionParams)

        val expectedValues = mapOf("time_to_connection" to 10L)
        val expectedDimensions = mapOf(
            "outcome" to "success",
            "user_tier" to "paid",
            "vpn_status" to "off",
            "vpn_trigger" to "auto",
            "server" to "PLUS#1",
            "server_features" to "partnership,streaming",
            "vpn_country" to "CH", // TODO: 3-letter country codes?
            "user_country" to "PL", // TODO: 3-letter country codes?
            "isp" to "Test ISP",
            "protocol" to "wireguard_udp",
            "port" to "443",
            "network_type" to "wifi"
        )
        verify {
            mockTelemetry.event(
                "vpn.any.connection", "vpn_connection", expectedValues, expectedDimensions
            )
        }
    }

    @Test
    fun `when connecting to free server then free is added to features`() {
        val connectionParams = createConnectionParams(
            freeServer,
            ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP),
            443
        )
        val dimensions = slot<Map<String, String>>()
        every { mockTelemetry.event(any(), any(), any(), capture(dimensions)) }

        connectSequence(connectionParams)

        verify { mockTelemetry.event("vpn.any.connection", "vpn_connection", any(), any()) }
        assertEquals("free,partnership", dimensions.captured["server_features"])
    }

    @Test
    fun `when connecting is aborted an aborted event is sent`() {
        prefs.lastKnownCountry = "PL"
        prefs.lastKnownIsp = "Test ISP"
        val dimensions = slot<Map<String, String>>()
        every { mockTelemetry.event(any(), any(), any(), capture(dimensions)) } returns Unit

        vpnConnectionTelemetry.onConnectionStart(ConnectTrigger.QuickConnect("Test"))
        testScheduler.advanceTimeBy(10)
        vpnConnectionTelemetry.onConnectionAbort()

        val expectedValues = mapOf("time_to_connection" to 10L)
        val expectedDimensions = mapOf(
            "outcome" to "aborted",
            "user_tier" to "paid",
            "vpn_status" to "off",
            "vpn_trigger" to "quick",
            "server" to "n/a",
            "server_features" to "n/a",
            "vpn_country" to "n/a",
            "user_country" to "PL",
            "isp" to "Test ISP",
            "protocol" to "n/a",
            "port" to "n/a",
            "network_type" to "wifi"
        )
        verify {
            mockTelemetry.event(
                "vpn.any.connection", "vpn_connection", expectedValues, expectedDimensions
            )
        }

        // Make another connection to test that no state from the previous one is carried over.
        testScheduler.advanceTimeBy(1000)
        connectSequence(createConnectionParams(plusServer, ProtocolSelection.REAL_PROTOCOLS[0], 123))

        verify {
            mockTelemetry.event(
                "vpn.any.connection", "vpn_connection", mapOf("time_to_connection" to 10L), any()
            )
        }
        assertEquals("auto", dimensions.captured["vpn_trigger"])
    }

    @Test
    fun `when new connection is started while connecting then an aborted outcome is reported for old connection`() {
        val dimensions = slot<Map<String, String>>()
        every { mockTelemetry.event(any(), any(), any(), capture(dimensions)) } returns Unit

        vpnConnectionTelemetry.onConnectionStart(ConnectTrigger.QuickConnect("Test"))
        testScheduler.advanceTimeBy(10)

        vpnConnectionTelemetry.onConnectionStart(ConnectTrigger.Auto("Test"))
        verify(exactly = 1) {
            mockTelemetry.event("vpn.any.connection", "vpn_connection", any(), any())
        }
        assertEquals("aborted", dimensions.captured["outcome"])
        assertEquals("quick", dimensions.captured["vpn_trigger"])

        // Finish the new connection.
        val connectionParams = createConnectionParams(plusServer, ProtocolSelection.REAL_PROTOCOLS[0], 123)
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))

        verify(exactly = 2) {
            mockTelemetry.event("vpn.any.connection", "vpn_connection", any(), any())
        }
        assertEquals("success", dimensions.captured["outcome"])
        assertEquals("auto", dimensions.captured["vpn_trigger"])
    }

    @Test
    fun `when disconnecting while connecting then aborted outcome is reported`() {
        val dimensions = slot<Map<String, String>>()
        every { mockTelemetry.event(any(), any(), any(), capture(dimensions)) } returns Unit
        vpnConnectionTelemetry.onConnectionStart(ConnectTrigger.QuickConnect("Test"))
        vpnConnectionTelemetry.onDisconnectionTrigger(DisconnectTrigger.QuickConnect("test"), null)

        verify(exactly = 1) {
            mockTelemetry.event("vpn.any.connection", "vpn_connection", any(), capture(dimensions))
        }
        assertEquals("aborted", dimensions.captured["outcome"])
    }

    @Test
    fun `when disconnecting due to error while connecting then failure outcome is reported`() {
        val dimensions = slot<Map<String, String>>()
        every { mockTelemetry.event(any(), any(), any(), capture(dimensions)) } returns Unit
        vpnConnectionTelemetry.onConnectionStart(ConnectTrigger.QuickConnect("Test"))
        vpnConnectionTelemetry.onDisconnectionTrigger(DisconnectTrigger.Error("error"), null)

        verify(exactly = 1) {
            mockTelemetry.event("vpn.any.connection", "vpn_connection", any(), capture(dimensions))
        }
        assertEquals("failure", dimensions.captured["outcome"])
    }

    @Test
    fun `when connecting requires disconnecting first then no failure is reported`() {
        val dimensions = slot<Map<String, String>>()
        every { mockTelemetry.event(any(), any(), any(), capture(dimensions)) } returns Unit

        val connectionParams = createConnectionParams(plusServer, ProtocolSelection.REAL_PROTOCOLS[0], 123)
        connectSequence(connectionParams) // Put VpnConnectionTelemetry in "connected" state.
        verify(exactly = 1) {
            mockTelemetry.event("vpn.any.connection", "vpn_connection", any(), any())
        }

        vpnConnectionTelemetry.onConnectionStart(ConnectTrigger.QuickConnect("Test"))
        vpnConnectionTelemetry.onDisconnectionTrigger(DisconnectTrigger.NewConnection, null)
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Disabled, null))
        verify { mockTelemetry.event("vpn.any.connection", "vpn_disconnection", any(), any()) }
        verify(exactly = 1) {
            mockTelemetry.event("vpn.any.connection", "vpn_connection", any(), any())
        }

        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))

        verify(exactly = 2) { mockTelemetry.event("vpn.any.connection", "vpn_connection", any(), any()) }
        assertEquals("success", dimensions.captured["outcome"])
        assertEquals("quick", dimensions.captured["vpn_trigger"])
    }

    @Test
    fun `when connected and local agent connection is reestablished then no events are sent`() {
        val dimensions = slot<Map<String, String>>()
        every { mockTelemetry.event(any(), any(), any(), capture(dimensions)) } returns Unit

        val connectionParams = createConnectionParams(plusServer, ProtocolSelection.REAL_PROTOCOLS[0], 123)
        connectSequence(connectionParams)
        verify(exactly = 1) {
            mockTelemetry.event("vpn.any.connection", "vpn_connection", any(), any())
        }
        testScheduler.advanceTimeBy(100)
        // Simulate local agent reconnection.
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connecting, connectionParams))
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        // No new events.
        verify(exactly = 1) {
            mockTelemetry.event("vpn.any.connection", "vpn_connection", any(), any())
        }
        testScheduler.advanceTimeBy(100)

        // Total duration is reported on disconnect.
        vpnConnectionTelemetry.onDisconnectionTrigger(DisconnectTrigger.QuickConnect("test"), null)
        val expectedValues = mapOf("session_length" to 200L)
        verify(exactly = 1) {
            mockTelemetry.event("vpn.any.connection", "vpn_disconnection", expectedValues, any())
        }
    }

    @Test
    fun `when connecting to SC country the exit country is reported as vpn_country`() {
        val connectionParams = createConnectionParams(secureCoreServer, ProtocolSelection.REAL_PROTOCOLS[0], 123)
        connectSequence(connectionParams)

        val dimensions = slot<Map<String, String>>()
        verify(exactly = 1) {
            mockTelemetry.event("vpn.any.connection", "vpn_connection", any(), capture(dimensions))
        }
        assertEquals("PL", dimensions.captured["vpn_country"])
    }

    private fun createConnectionParams(server: Server, protocolSelection: ProtocolSelection, port: Int) =
        ConnectionParams(
            Profile.getTempProfile(ServerWrapper.makePreBakedFastest()),
            server,
            ConnectingDomain(entryDomain = "dummy", id = null),
            protocolSelection.vpn,
            transmissionProtocol = protocolSelection.transmission,
            port = port
        )

    private fun connectSequence(connectionParams: ConnectionParams) {
        vpnConnectionTelemetry.onConnectionStart(ConnectTrigger.Auto("Test"))
        testScheduler.advanceTimeBy(10)
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        testScheduler.runCurrent()
    }
}
