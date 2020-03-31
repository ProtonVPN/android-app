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
import com.protonvpn.android.components.Listable
import com.protonvpn.android.components.Markable
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.ServerDeliver
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.Log
import java.io.Serializable
import java.util.*
import kotlin.collections.ArrayList
import org.apache.commons.lang3.SerializationUtils

class VpnCountry(
    val flag: String,
    serverList: List<Server>,
    deliverer: ServerDeliver,
    private var bestConnection: Server?
) : Markable, Serializable, Listable {
    val serverList: List<Server>
    val translatedCoordinates: TranslatedCoordinates
    private val keywords: MutableList<String>
    private var isExpanded: Boolean = false
    private var addBestConnection = true

    @Transient var deliverer: ServerDeliver

    private val expandedCountryString: String
        get() = flag + countryName + serverList.size

    val countryName: String
        get() = CountryTools.getFullName(flag)

    val wrapperServers: List<ServerWrapper>
        get() {
            val wrapperList = ArrayList<ServerWrapper>()
            if (connectableServers.size > 1) {
                wrapperList.add(ServerWrapper.makeFastestForCountry(flag, deliverer))
                wrapperList.add(ServerWrapper.makeRandomForCountry(flag, deliverer))
            }
            connectableServers.mapTo(wrapperList) { ServerWrapper.makeWithServer(it, deliverer) }
            return wrapperList
        }

    init {
        this.serverList = sortServers(serverList)
        this.deliverer = deliverer
        this.translatedCoordinates = TranslatedCoordinates(flag)
        this.keywords = ArrayList()
        initKeywords()
        this.isExpanded = false
    }

    private fun initKeywords() {
        for (server in serverList) {
            server.keywords
                    .filterNot { keywords.contains(it) }
                    .forEach { keywords.add(it) }
        }
    }

    fun hasAccessibleServer(userData: UserData): Boolean {
        return serverList.any { userData.hasAccessToServer(it) && it.isOnline }
    }

    fun isUnderMaintenance(): Boolean {
        return !serverList.any { it.isOnline }
    }

    private fun sortServers(serverList: List<Server>): List<Server> {
        Collections.sort(serverList,
                compareBy<Server> { !it.isFreeServer }
                        .thenBy { it.serverNumber >= 100 }
                        .thenBy { it.domain }
                        .thenBy { it.tier })
        return serverList
    }

    fun getKeywords(): List<String> {
        return keywords
    }

    fun hasConnectedServer(server: Server?): Boolean {
        if (server == null) {
            return false
        }
        return serverList.any { it.domain == server.domain }
    }

    fun getServersForListView(userData: UserData): List<Server> {
        val list = serverList.toMutableList()
        list.sortByDescending { userData.userTier == it.tier }
        if (addBestConnection && bestConnection == null) {
            Log.exception(Exception("AddBest true, while bestConnection is null. Flag: " + flag + " size: " + serverList.size))
        }
        if (addBestConnection && bestConnection != null) {
            val fastestServer = SerializationUtils.clone(bestConnection)
            fastestServer!!.hasBestScore = true
            fastestServer.selectedAsFastest = true
            list.add(0, fastestServer)
        }

        return list
    }

    fun isExpanded(): Boolean {
        return isExpanded
    }

    override fun getCoordinates(): TranslatedCoordinates {
        return translatedCoordinates
    }

    override fun isSecureCoreMarker(): Boolean {
        return isSecureCoreCountry()
    }

    override fun getMarkerText(): String {
        return countryName
    }

    override fun getConnectableServers(): List<Server> {
        return serverList
    }

    fun addBestConnectionToList(addBestConnection: Boolean) {
        this.addBestConnection = addBestConnection
    }

    override fun getLabel(context: Context): String {
        return countryName // + if (hasAccessibleServer()) "" else " (Upgrade)"
    }

    fun isSecureCoreCountry(): Boolean {
        return flag == "IS" || flag == "SE" || flag == "CH"
    }
}
