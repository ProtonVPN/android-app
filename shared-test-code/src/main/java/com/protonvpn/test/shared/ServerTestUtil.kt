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

import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.Location
import com.protonvpn.android.models.vpn.SERVER_FEATURE_IPV6
import com.protonvpn.android.models.vpn.SERVER_FEATURE_RESTRICTED
import com.protonvpn.android.models.vpn.SERVER_FEATURE_SECURE_CORE
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.usecase.GetSmartProtocols
import com.protonvpn.android.vpn.ProtocolSelection
import io.mockk.every
import io.mockk.mockk

val dummyConnectingDomain =
    ConnectingDomain("1.2.3.4", null, "dummy.protonvpn.net", "1.2.3.5", null, true, "o0AixWIjxr61AwsKjrTIM+f9iHWZlWUOYZQyroX+zz4=")

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
    tier: Int = 0,
    features: Int = 0,
    gatewayName: String? = null,
    translations: Map<String, String?>? = null,
    isSecureCore: Boolean = false, // For convenience, adds SERVER_FEATURE_SECURE_CORE
    isIpV6Supported: Boolean = false, // For convenience, adds SERVER_FEATURE_SECURE_CORE
    connectingDomains: List<ConnectingDomain> = listOf(dummyConnectingDomain),
    isOnline: Boolean = true,
    loadPercent: Float = 50f,
    hostCountry: String? = ""
) = Server(
        serverId = serverId,
        entryCountry = entryCountry.uppercase(),
        exitCountry = exitCountry.uppercase(),
        serverName = serverName,
        rawGatewayName = gatewayName,
        connectingDomains = connectingDomains,
        hostCountry = hostCountry,
        domain = "dummy.protonvpn.net",
        load = loadPercent,
        tier = tier,
        state = state,
        city = city,
        features = features
            or (if (isSecureCore || entryCountry != exitCountry) SERVER_FEATURE_SECURE_CORE else 0)
            or (if (gatewayName != null) SERVER_FEATURE_RESTRICTED else 0)
            or (if (isIpV6Supported) SERVER_FEATURE_IPV6 else 0),
        location = Location("", ""),
        translations = translations,
        score = score,
        isOnline = isOnline,
    )
