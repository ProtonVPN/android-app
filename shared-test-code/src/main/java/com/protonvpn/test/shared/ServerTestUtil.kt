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

package com.protonvpn.test.shared

import com.protonvpn.android.servers.api.ConnectingDomain
import com.protonvpn.android.servers.api.SERVER_FEATURE_IPV6
import com.protonvpn.android.servers.api.SERVER_FEATURE_RESTRICTED
import com.protonvpn.android.servers.api.SERVER_FEATURE_SECURE_CORE
import com.protonvpn.android.servers.Server
import com.protonvpn.android.models.vpn.usecase.GetSmartProtocols
import com.protonvpn.android.servers.api.LogicalServer
import com.protonvpn.android.servers.api.ServerLocation
import com.protonvpn.android.servers.api.ServerStatusReference
import com.protonvpn.android.vpn.ProtocolSelection
import io.mockk.every
import io.mockk.mockk

val dummyConnectingDomain =
    ConnectingDomain(
        entryIp = "1.2.3.4",
        entryIpPerProtocol = null,
        entryDomain = "dummy.protonvpn.net",
        id = "1.2.3.5",
        label = null,
        isOnline = true,
        publicKeyX25519 = "o0AixWIjxr61AwsKjrTIM+f9iHWZlWUOYZQyroX+zz4="
    )

fun createGetSmartProtocols(
    protocols: List<ProtocolSelection> = ProtocolSelection.REAL_PROTOCOLS
): GetSmartProtocols = mockk<GetSmartProtocols>().also {
    every { it.invoke() } returns protocols
}

@Suppress("LongParameterList")
fun createServer(
    serverId: String = "ID",
    serverName: String = "dummyName",
    exitCountry: String = "PL",
    entryCountry: String = exitCountry,
    city: String? = null,
    state: String? = null,
    score: Double = 0.5,
    tier: Int = 2,
    features: Int = 0,
    gatewayName: String? = null,
    translations: Map<String, String?>? = null,
    isSecureCore: Boolean = false, // For convenience, adds SERVER_FEATURE_SECURE_CORE
    isIpV6Supported: Boolean = false, // For convenience, adds SERVER_FEATURE_IPV6
    connectingDomains: List<ConnectingDomain> = listOf(dummyConnectingDomain),
    isOnline: Boolean = true,
    loadPercent: Float = 50f,
    hostCountry: String? = "",
    isVisible: Boolean = true,
) = Server(
        serverId = serverId,
        entryCountry = entryCountry.uppercase(),
        rawExitCountry = exitCountry.uppercase(),
        serverName = serverName,
        rawGatewayName = gatewayName,
        connectingDomains = connectingDomains,
        hostCountry = hostCountry,
        load = loadPercent,
        tier = tier,
        state = state,
        city = city,
        features = features
            or (if (isSecureCore || entryCountry != exitCountry) SERVER_FEATURE_SECURE_CORE else 0)
            or (if (gatewayName != null) SERVER_FEATURE_RESTRICTED else 0)
            or (if (isIpV6Supported) SERVER_FEATURE_IPV6 else 0),
        exitLocation = ServerLocation(0f, 0f),
        entryLocation = ServerLocation(0f, 0f),
        translations = translations,
        score = score,
        isOnline = isOnline,
        isVisible = isVisible,
    )

fun createLogicalServer(
    serverId: String = "ID",
    serverName: String = "dummyName",
    exitCountry: String = "PL",
    entryCountry: String = exitCountry,
    city: String? = null,
    state: String? = null,
    tier: Int = 0,
    features: Int = 0,
    gatewayName: String? = null,
    translations: Map<String, String?>? = null,
    isSecureCore: Boolean = false, // For convenience, adds SERVER_FEATURE_SECURE_CORE
    isIpV6Supported: Boolean = false, // For convenience, adds SERVER_FEATURE_IPV6
    connectingDomains: List<ConnectingDomain> = listOf(dummyConnectingDomain),
    hostCountry: String? = "",
    statusIndex: UInt = 0u,
    statusPenalty: Double = 0.1,
    statusCost: Int = 0,
    latitude: Float = 46.175811f,
    longitude: Float = 6.1009812f,
) = LogicalServer(
    serverId = serverId,
    entryCountry = entryCountry.uppercase(),
    exitCountry = exitCountry.uppercase(),
    serverName = serverName,
    gatewayName = gatewayName,
    physicalServers = connectingDomains,
    hostCountry = hostCountry,
    tier = tier,
    state = state,
    city = city,
    features = features
        or (if (isSecureCore || entryCountry != exitCountry) SERVER_FEATURE_SECURE_CORE else 0)
        or (if (gatewayName != null) SERVER_FEATURE_RESTRICTED else 0)
        or (if (isIpV6Supported) SERVER_FEATURE_IPV6 else 0),
    exitLocation = ServerLocation(latitude, longitude),
    entryLocation = ServerLocation(latitude, longitude),
    translations = translations,
    statusReference = ServerStatusReference(statusIndex, statusPenalty, statusCost)
)
