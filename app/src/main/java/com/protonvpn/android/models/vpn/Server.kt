/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.android.models.vpn

import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.DebugUtils.debugAssert
import com.protonvpn.android.utils.VpnIntToBoolSerializer
import com.protonvpn.android.utils.hasFlag
import com.protonvpn.android.utils.implies
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

const val SERVER_FEATURE_SECURE_CORE = 1
const val SERVER_FEATURE_TOR = 2
const val SERVER_FEATURE_P2P = 4
const val SERVER_FEATURE_STREAMING = 8
const val SERVER_FEATURE_IPV6 = 16
const val SERVER_FEATURE_RESTRICTED = 32
const val SERVER_FEATURE_PARTNER_SERVER = 64

@Serializable
data class Server(
    @SerialName(value = "ID") val serverId: String,
    @SerialName(value = "EntryCountry") val entryCountry: String,
    @SerialName(value = "ExitCountry") val exitCountry: String,
    @SerialName(value = "Name") val serverName: String,
    @SerialName(value = "Servers") val connectingDomains: List<ConnectingDomain>,
    @SerialName(value = "HostCountry") val hostCountry: String? = null,
    @SerialName(value = "Domain") val domain: String,
    @SerialName(value = "Load") var load: Float, // VPNAND-1865: change to 'val'
    @SerialName(value = "Tier") val tier: Int,
    @SerialName(value = "State") val state: String? = null,
    @SerialName(value = "City") val city: String?,
    @SerialName(value = "Features") val features: Int,
    @SerialName(value = "Location") private val location: Location,
    @SerialName(value = "Translations") private val translations: Map<String, String?>? = null,
    @SerialName(value = "GatewayName") val rawGatewayName: String? = null,

    @SerialName(value = "Score") var score: Double, // VPNAND-1865: change to 'val'

    @Serializable(with = VpnIntToBoolSerializer::class)
    @SerialName(value = "Status")
    private var isOnline: Boolean // VPNAND-1865: change to 'val'
) : java.io.Serializable {

    // VPNAND-1865: consider making it a @Transient member precomputed on creation
    val online: Boolean get() = isOnline && connectingDomains.any { it.isOnline }

    val isTor get() = features.hasFlag(SERVER_FEATURE_TOR)

    val isFreeServer: Boolean
        get() = tier == 0

    val flag: String
        get() = if (exitCountry == "GB") "UK" else exitCountry

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
        CountryTools.getFullName(flag)

    val gatewayName: String? get() =
        rawGatewayName ?: if (isGatewayServer) serverName.substringBefore("#") else null

    override fun toString() = "$domain $entryCountry"

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Use displayName for UI.")
    fun getLabel(): String = if (isSecureCoreServer)
        CountryTools.getFullName(entryCountry)
    else
        serverName

    // VPNAND-1865: remove
    @Deprecated("Servers should be immutable")
    fun setOnline(value: Boolean) {
        isOnline = value
        connectingDomains.forEach {
            it.isOnline = value
        }
    }

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
