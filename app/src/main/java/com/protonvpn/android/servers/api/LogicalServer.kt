/*
 * Copyright (c) 2025. Proton AG
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
package com.protonvpn.android.servers.api

import com.protonvpn.android.models.vpn.Location
import com.protonvpn.android.utils.VpnIntToBoolSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val SERVER_FEATURE_SECURE_CORE = 1
const val SERVER_FEATURE_TOR = 2
const val SERVER_FEATURE_P2P = 4
const val SERVER_FEATURE_STREAMING = 8
const val SERVER_FEATURE_IPV6 = 16
const val SERVER_FEATURE_RESTRICTED = 32
const val SERVER_FEATURE_PARTNER_SERVER = 64

@Serializable
data class LogicalServerV1(
    @SerialName(value = "ID") val serverId: String,
    @SerialName(value = "EntryCountry") val entryCountry: String,
    @SerialName(value = "ExitCountry") val exitCountry: String,
    @SerialName(value = "Name") val serverName: String,
    @SerialName(value = "Servers") val connectingDomains: List<ConnectingDomain>,
    @SerialName(value = "HostCountry") val hostCountry: String? = null,
    @SerialName(value = "Load") val load: Float,
    @SerialName(value = "Tier") val tier: Int,
    @SerialName(value = "State") val state: String? = null,
    @SerialName(value = "City") val city: String? = null,
    @SerialName(value = "Features") val features: Int,
    @SerialName(value = "Location") val location: Location,
    @SerialName(value = "Translations") val translations: Map<String, String?>? = null,
    @SerialName(value = "GatewayName") val rawGatewayName: String? = null,

    @SerialName(value = "Score") val score: Double,

    @Serializable(with = VpnIntToBoolSerializer::class)
    @SerialName(value = "Status")
    val isOnline: Boolean
)

@Serializable
data class ServerStatusReference(
    @SerialName("Index") val index: UInt,
    @SerialName("Penalty") val penalty: Double,
    @SerialName("Cost") val cost: Int,
)

typealias LogicalsStatusId = String

@Serializable
data class ServerLocation(
    @SerialName(value = "Latitude") val latitude: Float,
    @SerialName(value = "Longitude") val longitude: Float,
)

@Serializable
data class LogicalServer(
    @SerialName(value = "ID") val serverId: LogicalsStatusId,
    @SerialName(value = "EntryCountry") val entryCountry: String,
    @SerialName(value = "ExitCountry") val exitCountry: String,
    @SerialName(value = "Name") val serverName: String,
    @SerialName(value = "Servers") val physicalServers: List<ConnectingDomain>,
    @SerialName(value = "StatusReference") val statusReference: ServerStatusReference,
    @SerialName(value = "Tier") val tier: Int,
    @SerialName(value = "HostCountry") val hostCountry: String? = null,
    @SerialName(value = "State") val state: String? = null,
    @SerialName(value = "City") val city: String? = null,
    @SerialName(value = "Features") val features: Int,
    @SerialName(value = "ExitLocation") val exitLocation: ServerLocation,
    @SerialName(value = "EntryLocation") val entryLocation: ServerLocation,
    @SerialName(value = "Translations") val translations: Map<String, String?>? = null,
    @SerialName(value = "GatewayName") val gatewayName: String? = null,
)
