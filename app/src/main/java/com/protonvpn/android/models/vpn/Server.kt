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

import com.protonvpn.android.components.Markable
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.DebugUtils.debugAssert
import com.protonvpn.android.utils.implies
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.proton.core.network.data.protonApi.IntToBoolSerializer
import java.util.regex.Pattern

@Serializable
data class Server(
    @SerialName(value = "ID") val serverId: String,
    @SerialName(value = "EntryCountry") val entryCountry: String,
    @SerialName(value = "ExitCountry") val exitCountry: String,
    @SerialName(value = "Name") val serverName: String,
    @SerialName(value = "Servers") val connectingDomains: List<ConnectingDomain>,
    @SerialName(value = "HostCountry") val hostCountry: String? = null,
    @SerialName(value = "Domain") val domain: String,
    @SerialName(value = "Load") var load: Float,
    @SerialName(value = "Tier") val tier: Int,
    @SerialName(value = "Region") val region: String?,
    @SerialName(value = "City") val city: String?,
    @SerialName(value = "Features") val features: Int,
    @SerialName(value = "Location") private val location: Location,
    @SerialName(value = "Translations") private val translations: Map<String, String?>? = null,

    @SerialName(value = "Score") var score: Float,

    @Serializable(with = IntToBoolSerializer::class)
    @SerialName(value = "Status")
    private var isOnline: Boolean
) : Markable, java.io.Serializable {

    val online: Boolean get() = isOnline && connectingDomains.any { it.isOnline }

    @Transient
    private val translatedCoordinates: TranslatedCoordinates = TranslatedCoordinates(exitCountry)

    enum class Keyword {
        P2P, TOR, STREAMING, SMART_ROUTING
    }

    val keywords: List<Keyword> get() = mutableListOf<Keyword>().apply {
        if (features and 4 == 4)
            add(Keyword.P2P)
        if (features and 2 == 2)
            add(Keyword.TOR)
        if (features and 8 == 8)
            add(Keyword.STREAMING)
        if (features and 32 == 32)
            add(Keyword.SMART_ROUTING)
    }

    @Transient
    val entryCountryCoordinates: TranslatedCoordinates? =
        TranslatedCoordinates(this.entryCountry)

    init {
        debugAssert {
            isOnline.implies(connectingDomains.any(ConnectingDomain::isOnline))
        }
    }

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

    val isSecureCoreServer: Boolean
        get() = features and 1 == 1

    fun getCityTranslation() = translations?.get("City")
    val displayCity get() = getCityTranslation() ?: city

    val serverNumber: Int
        get() {
            val name = serverName
            val m = SERVER_NUMBER_PATTERN.matcher(name)
            return if (m.find()) {
                Integer.valueOf(m.group(1))
            } else {
                1
            }
        }

    fun supportsProtocol(protocol: VpnProtocol) =
        connectingDomains.any { it.supportsProtocol(protocol) }

    private val secureCoreServerNaming: String
        get() = CountryTools.getFullName(entryCountry) + " $SECURE_CORE_SEPARATOR " +
                CountryTools.getFullName(exitCountry)

    val displayName: String get() = if (isSecureCoreServer)
        secureCoreServerNaming
    else
        CountryTools.getFullName(flag)

    override fun getCoordinates(): TranslatedCoordinates = translatedCoordinates

    override fun isSecureCoreMarker() = false

    override fun getMarkerEntryCountryCode(): String? = if (isSecureCoreServer) entryCountry else null

    override fun getMarkerCountryCode(): String = flag

    override fun getConnectableServers(): List<Server> = listOf(this)

    val onlineConnectingDomains get() = connectingDomains.filter(ConnectingDomain::isOnline)

    fun getRandomConnectingDomain() = onlineConnectingDomains.randomOrNull() ?: connectingDomains.random()

    override fun toString() = "$domain $entryCountry"

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Use displayName for UI.")
    fun getLabel(): String = if (isSecureCoreServer)
        CountryTools.getFullName(entryCountry)
    else
        serverName

    fun setOnline(value: Boolean) {
        isOnline = value
        connectingDomains.forEach {
            it.isOnline = value
        }
    }

    companion object {
        val SERVER_NUMBER_PATTERN: Pattern = Pattern.compile("#(\\d+(\\d+)?)")
        const val SECURE_CORE_SEPARATOR = ">>"
    }
}
