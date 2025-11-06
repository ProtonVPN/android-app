/*
 * Copyright (c) 2024. Proton AG
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

import com.protonvpn.android.telemetry.toTelemetry
import com.protonvpn.android.vpn.ProtocolSelection
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryExtensionsTests {

    @Test
    fun `vpn protocol names`() {
        val protocols = listOf(ProtocolSelection.SMART) + ProtocolSelection.REAL_PROTOCOLS
        val expectedNames = listOf(
            "smart", "wireguard_udp", "wireguard_tcp", "openvpn_udp", "openvpn_tcp", "wireguard_tls", "protun_udp", "protun_tcp", "protun_tls"
        )
        assertEquals(
            expectedNames, protocols.map { it.toTelemetry() }
        )
    }
}
