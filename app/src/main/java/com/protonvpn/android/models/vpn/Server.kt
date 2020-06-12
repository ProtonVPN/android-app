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

import android.content.Context
import androidx.annotation.ColorRes
import com.fasterxml.jackson.annotation.JsonProperty
import com.protonvpn.android.R
import com.protonvpn.android.components.Listable
import com.protonvpn.android.components.Markable
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.DebugUtils.debugAssert
import com.protonvpn.android.utils.implies
import java.io.Serializable
import java.util.regex.Pattern

data class Server(
    @param:JsonProperty(value = "ID", required = true) val serverId: String,
    @param:JsonProperty(value = "EntryCountry") val entryCountry: String?,
    @param:JsonProperty(value = "ExitCountry") val exitCountry: String,
    @param:JsonProperty(value = "Name", required = true) val serverName: String,
    @param:JsonProperty(value = "Servers", required = true) private val connectingDomains: List<ConnectingDomain>,
    @param:JsonProperty(value = "Domain", required = true) val domain: String,
    @param:JsonProperty(value = "Load", required = true) var load: Float,
    @param:JsonProperty(value = "Tier", required = true) val tier: Int,
    @param:JsonProperty(value = "Region", required = true) val region: String?,
    @param:JsonProperty(value = "City", required = true) val city: String?,
    @param:JsonProperty(value = "Features", required = true) private val features: Int,
    @param:JsonProperty(value = "Location", required = true) private val location: Location,
    @param:JsonProperty(value = "Score", required = true) var score: Float,
    @param:JsonProperty(value = "Status", required = true) var isOnline: Boolean
) : Markable, Serializable, Listable {

    private val translatedCoordinates: TranslatedCoordinates = TranslatedCoordinates(exitCountry)

    val keywords: List<String>

    val entryCountryCoordinates: TranslatedCoordinates? =
            if (entryCountry != null) TranslatedCoordinates(this.entryCountry) else null

    init {
        debugAssert {
            isOnline.implies(connectingDomains.any(ConnectingDomain::isOnline))
        }
    }

    val isFreeServer: Boolean
        get() = domain.contains("-free")

    val flag: String
        get() = if (exitCountry == "GB") "UK" else exitCountry

    val isBasicServer: Boolean
        get() = tier == 1

    val isPlusServer: Boolean
        get() = tier == 2

    val isPMTeamServer: Boolean
        get() = tier == 3

    val loadColor: Int
        @ColorRes
        get() = when {
            load < 50f -> R.color.colorAccent
            load < 90f -> R.color.yellow
            else -> R.color.dimmedRed
        }

    val isSecureCoreServer: Boolean
        get() = features and 1 == 1

    val serverNumber: Int
        get() {
            val name = serverName
            val pattern = Pattern.compile("#(\\d+(\\d+)?)")
            val m = pattern.matcher(name)
            return if (m.find()) {
                Integer.valueOf(m.group(1))
            } else {
                1
            }
        }

    private val secureCoreServerNaming: String
        get() = CountryTools.getFullName(entryCountry) + " >> " + CountryTools.getFullName(
                exitCountry)

    init {
        keywords = mutableListOf<String>().apply {
            if (features and 4 == 4)
                add("p2p")
            if (features and 2 == 2)
                add("tor")
        }
    }

    val displayName: String get() = if (isSecureCoreServer)
        secureCoreServerNaming
    else
        CountryTools.getFullName(flag)

    override fun getCoordinates(): TranslatedCoordinates = translatedCoordinates

    override fun isSecureCoreMarker() = false

    override fun getMarkerText(): String = secureCoreServerNaming

    override fun getConnectableServers(): List<Server> = listOf(this)

    fun getRandomConnectingDomain() =
            connectingDomains.filter(ConnectingDomain::isOnline).random()

    override fun toString() = "$domain $entryCountry"

    override fun getLabel(context: Context): String = if (isSecureCoreServer)
        CountryTools.getFullName(entryCountry) else serverName
}
