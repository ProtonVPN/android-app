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

package com.protonvpn.android.servers

import com.protonvpn.android.servers.api.ConnectingDomain
import com.protonvpn.android.servers.api.LogicalServer
import com.protonvpn.android.servers.api.LogicalServerV1
import com.protonvpn.android.servers.api.SERVER_FEATURE_IPV6
import com.protonvpn.android.servers.api.SERVER_FEATURE_P2P
import com.protonvpn.android.servers.api.SERVER_FEATURE_PARTNER_SERVER
import com.protonvpn.android.servers.api.SERVER_FEATURE_RESTRICTED
import com.protonvpn.android.servers.api.SERVER_FEATURE_SECURE_CORE
import com.protonvpn.android.servers.api.SERVER_FEATURE_STREAMING
import com.protonvpn.android.servers.api.SERVER_FEATURE_TOR
import com.protonvpn.android.servers.api.ServerLocation
import com.protonvpn.android.servers.api.ServerStatusReference
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.VpnIntToBoolSerializer
import com.protonvpn.android.utils.hasFlag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Server(
    @SerialName(value = "ID") val serverId: String,
    @SerialName(value = "EntryCountry") val entryCountry: String,
    @SerialName(value = "ExitCountry") private val rawExitCountry: String,
    @SerialName(value = "Name") val serverName: String,
    @SerialName(value = "Servers") val connectingDomains: List<ConnectingDomain>,
    @SerialName(value = "HostCountry") val hostCountry: String? = null,
    @SerialName(value = "Load") val load: Float,
    @SerialName(value = "Tier") val tier: Int,
    @SerialName(value = "State") val state: String? = null,
    @SerialName(value = "City") val city: String? = null,
    @SerialName(value = "Features") val features: Int,
    @SerialName(value = "ExitLocation") val exitLocation: ServerLocation? = null,
    @SerialName(value = "EntryLocation") val entryLocation: ServerLocation? = null,
    @SerialName(value = "Translations") private val translations: Map<String, String?>? = null,
    @SerialName(value = "GatewayName") val rawGatewayName: String? = null,
    @SerialName(value = "StatusReference") val statusReference: ServerStatusReference? = null,

    @SerialName(value = "Score") val score: Double,

    @Serializable(with = VpnIntToBoolSerializer::class)
    @SerialName(value = "Status")
    val rawIsOnline: Boolean,

    @SerialName(value = "IsVisible")
    val isVisible: Boolean = true,
) : java.io.Serializable {

    @Transient
    val online: Boolean = rawIsOnline && connectingDomains.any { it.isOnline }

    val isTor get() = features.hasFlag(SERVER_FEATURE_TOR)

    val isFreeServer: Boolean
        get() = tier == 0

    val exitCountry: String get() = if (rawExitCountry == "UK") "GB" else rawExitCountry

    val isBasicServer: Boolean
        get() = tier == 1

    val isPlusServer: Boolean
        get() = tier == 2

    val isPMTeamServer: Boolean
        get() = tier == 3

    val isGatewayServer: Boolean
        get() = features.hasFlag(SERVER_FEATURE_RESTRICTED)

    val isSecureCoreServer: Boolean
        get() = features.hasFlag(SERVER_FEATURE_SECURE_CORE)

    val isPartneshipServer: Boolean
        get() = features.hasFlag(SERVER_FEATURE_PARTNER_SERVER)

    val isP2pServer: Boolean
        get() = features.hasFlag(SERVER_FEATURE_P2P)

    val isIPv6Supported: Boolean
        get() = features.hasFlag(SERVER_FEATURE_IPV6)

    val isStreamingServer: Boolean
        get() = features.hasFlag(SERVER_FEATURE_STREAMING)

    fun getCityTranslation() = translations?.get("City")
    fun getStateTranslation() = translations?.get("State")
    val displayCity get() = getCityTranslation() ?: city
    val displayState get() = getStateTranslation() ?: state

    @Transient
    val serverNumber: Int = computeServerNumber()

    private val secureCoreServerNaming: String
        get() = CountryTools.getFullName(entryCountry) + " $SECURE_CORE_SEPARATOR " +
                CountryTools.getFullName(exitCountry)

    val displayName: String get() = if (isSecureCoreServer)
        secureCoreServerNaming
    else
        CountryTools.getFullName(exitCountry)

    val gatewayName: String? get() =
        rawGatewayName ?: if (isGatewayServer) serverName.substringBefore("#") else null

    override fun toString() = "$serverName $entryCountry"

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Use displayName for UI.")
    fun getLabel(): String = if (isSecureCoreServer)
        CountryTools.getFullName(entryCountry)
    else
        serverName

    private fun computeServerNumber(): Int {
        var result = 1
        val indexStart = serverName.indexOf("#")
        if (indexStart >= 0 && indexStart + 1 < serverName.length) {
            val section = serverName.substring(indexStart + 1)
            val indexEnd = section
                .indexOfFirst { c -> c !in '0' .. '9' }
                .let { index -> if (index >= 0) index else section.length }
            if (indexEnd > 0) {
                try {
                    result = Integer.parseInt(section.substring(0, indexEnd)).coerceAtLeast(0)
                } catch (e: NumberFormatException) {
                    result = 1
                }
            }
        }
        return result
    }

    companion object {
        const val SECURE_CORE_SEPARATOR = ">>"
    }
}

fun LogicalServerV1.toServer() = Server(
    serverId = serverId,
    entryCountry = entryCountry,
    rawExitCountry = exitCountry,
    serverName = serverName,
    connectingDomains = connectingDomains,
    hostCountry = hostCountry,
    load = load,
    tier = tier,
    state = state,
    city = city,
    features = features,
    exitLocation = with(location) {
        ServerLocation(latitude = latitude.toFloatOrNull() ?: 0f, longitude = longitude.toFloatOrNull() ?: 0f)
    },
    translations = translations,
    rawGatewayName = rawGatewayName,
    score = score,
    rawIsOnline = isOnline,
    isVisible = true,
)

fun Iterable<LogicalServerV1>.toServers() = map { it.toServer() }

fun LogicalServer.toPartialServer() = Server(
    serverId = serverId,
    entryCountry = entryCountry,
    rawExitCountry = exitCountry,
    serverName = serverName,
    connectingDomains = physicalServers,
    hostCountry = hostCountry,
    tier = tier,
    state = state,
    city = city,
    features = features,
    exitLocation = exitLocation,
    entryLocation = entryLocation,
    translations = translations,
    rawGatewayName = gatewayName,
    statusReference = statusReference,
    // The following values are fetched separately.
    load = 100f,
    score = 1_000_000.0,
    rawIsOnline = true, // Must be true, otherwise will never be set online.
    isVisible = false,
)

fun Iterable<LogicalServer>.toPartialServers() = map { it.toPartialServer() }
