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

package com.protonvpn.app.vpn

import com.protonvpn.android.models.vpn.SERVER_FEATURE_P2P
import com.protonvpn.android.models.vpn.SERVER_FEATURE_TOR
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.isCompatibleWith
import com.protonvpn.test.shared.createServer
import org.junit.Assert.assertEquals
import java.util.EnumSet
import kotlin.test.Test

class ConnectIntentExtensionsTests {

    @Test
    fun `non-SC server matches fastest in country`() {
        val intent = ConnectIntent.FastestInCountry(CountryId.switzerland, emptySet())
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "CH"), true
        )
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "DE"), false
        )
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "CH", isSecureCore = true), false
        )
    }

    @Test
    fun `city server matches fastest in city`() {
        val intent = ConnectIntent.FastestInCity(CountryId("PL"), cityEn = "Warsaw", emptySet())
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "PL", city = "Warsaw"), true
        )
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "US", city = "Warsaw"), false
        )
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "PL"), false
        )
    }

    @Test
    fun `gateway server matches fastest in gateway`() {
        val intent = ConnectIntent.Gateway(gatewayName = "gateway", serverId = null)
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "US", gatewayName = "gateway"), true
        )
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("B", exitCountry = "PL", gatewayName = "gateway"), true
        )
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "US", gatewayName = "other gateway"), false
        )
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "US"), false
        )
    }

    @Test
    fun `gateway server matches specific gateway server`() {
        val intent = ConnectIntent.Gateway(gatewayName = "gateway", serverId = "A")
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "US", gatewayName = "gateway"), true
        )
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("B", exitCountry = "PL", gatewayName = "gateway"), false
        )
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "US", gatewayName = "other gateway"), false
        )
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "US"), false
        )
    }

    @Test
    fun `server matches specific server intent`() {
        val intent = ConnectIntent.Server(serverId = "A", CountryId("US"), emptySet())
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "US"), true
        )
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("B", exitCountry = "US"), false
        )
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "US", gatewayName = "gateway"), false
        )
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "US", isSecureCore = true), false
        )
    }

    @Test
    fun `server must satisfy intent with feature`() {
        val p2pFeature = EnumSet.of(ServerFeature.P2P)
        val intent = ConnectIntent.FastestInCountry(CountryId.iceland, p2pFeature)
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "IS", features = SERVER_FEATURE_P2P), true
        )
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "IS", features = SERVER_FEATURE_P2P or SERVER_FEATURE_TOR), true
        )
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "IS", features = SERVER_FEATURE_TOR), false
        )
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "IS"), false
        )
    }

    @Test
    fun `servers with feature satisfy intent without features`() {
        val intent = ConnectIntent.FastestInCountry(CountryId.iceland, emptySet())
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "IS", features = SERVER_FEATURE_P2P), true
        )
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "IS", features = SERVER_FEATURE_P2P or SERVER_FEATURE_TOR), true
        )
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "IS"), true
        )
    }

    @Test
    fun `no server matches fastest country intent`() {
        val intent = ConnectIntent.Fastest
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "CH"), false
        )

        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "PL", entryCountry = "CH"), false
        )
    }

    @Test
    fun `no server matches fastest SC country intent`() {
        val intent = ConnectIntent.SecureCore(CountryId.fastest, CountryId.fastest)
        testIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "PL", entryCountry = "CH", isSecureCore = true), false
        )
    }

    @Test
    fun `match fastest - every non-SC server matches fastest in the world`() {
        val intent = ConnectIntent.Fastest
        testMatchFastestIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "DE"), true
        )
        testMatchFastestIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "UK"), true
        )
        testMatchFastestIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "UK", isSecureCore = true), false
        )
    }

    @Test
    fun `match fastest - SC server matches fastest SC to country`() {
        val intent = ConnectIntent.SecureCore(entryCountry = CountryId.fastest, exitCountry = CountryId("US"))
        testMatchFastestIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "US", entryCountry = "CH", isSecureCore = true), true
        )
        testMatchFastestIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "US", entryCountry = "IS", isSecureCore = true), true
        )
        testMatchFastestIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "LT", entryCountry = "IS", isSecureCore = true), false
        )
        testMatchFastestIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "US", isSecureCore = false), false
        )
    }

    @Test
    fun `match fastest - SC server matches fastest SC`() {
        val intent = ConnectIntent.SecureCore(CountryId.fastest, CountryId.fastest)
        testMatchFastestIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "PL", entryCountry = "CH", isSecureCore = true), true
        )
        testMatchFastestIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "US", entryCountry = "IS", isSecureCore = true), true
        )
        testMatchFastestIsServerCompatibleWithConnectIntent(
            intent, createServer("A", exitCountry = "US", isSecureCore = false), false
        )
    }

    private fun testIsServerCompatibleWithConnectIntent(
        intent: ConnectIntent,
        server: Server?,
        expectedResult: Boolean
    ) {
        assertEquals(expectedResult, server.isCompatibleWith(intent, matchFastest = false))
    }

    private fun testMatchFastestIsServerCompatibleWithConnectIntent(
        intent: ConnectIntent,
        server: Server?,
        expectedResult: Boolean
    ) {
        assertEquals(expectedResult, server.isCompatibleWith(intent, matchFastest = true))
    }
}
