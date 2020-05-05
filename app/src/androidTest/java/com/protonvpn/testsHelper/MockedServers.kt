/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.testsHelper

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerDeliver
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.FileUtils

object MockedServers {

    private var json: String? = null
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val listType = object : TypeToken<List<Server>>() {}.type
    private val serverType = object : TypeToken<Server>() {}.type

    val server by lazy<Server> { gson.fromJson(serverJson, serverType) }

    val serverList by lazy {
        FileUtils.getObjectFromAssets<List<Server>>("MockedServers/Servers.json")
    }

    fun getProfile(protocol: VpnProtocol, server: Server) =
        Profile(protocol.name, "", ServerWrapper.makeWithServer(server, object : ServerDeliver {
            override fun hasAccessToServer(server: Server?) = true
            override fun getServer(wrapper: ServerWrapper?): Server = server
        })).apply {
            setProtocol(protocol)
        }

    private val serverJson = """
      {
        "bestScore": false,
        "city": "Toronto",
        "connectingDomains": [
          {
            "entryDomain": "ca-01.protonvpn.com",
            "entryIp": "127.0.0.1",
            "exitIp": "127.0.0.1"
          }
        ],
        "domain": "ca-01.protonvpn.com",
        "entryCountry": "CA",
        "entryCountryCoordinates": {
          "positionX": 875.0,
          "positionY": 400.0
        },
        "exitCountry": "CA",
        "features": 0,
        "isOnline": true,
        "keywords": [],
        "load": "22",
        "location": {
          "latitude": "43.6328999999999999999",
          "longitude": "-79.36109999999999993"
        },
        "score": 1.434756,
        "selectedAsFastest": false,
        "serverId": "1",
        "serverName": "CA#1",
        "tier": 1,
        "translatedCoordinates": {
          "positionX": 875.0,
          "positionY": 400.0
        },
        "mId": -1,
        "mSelectedAppsHandling": "SELECTED_APPS_DISABLE",
        "mUUID": "2a764ed2-bd32-431a-9a35-162332e3957c"
      }""".trimIndent()
}
