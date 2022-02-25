/*
 * Copyright (c) 2021 Proton Technologies AG
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

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ProfileColor
import com.protonvpn.android.models.profiles.ServerDeliver
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.Server

object MockedServers {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val serverListType = object : TypeToken<List<Server>>() {}.type

    val serverList by lazy<List<Server>> {
        gson.fromJson(serverListJson, serverListType)
    }

    val server by lazy { serverList.first() }

    fun getProfile(protocol: VpnProtocol, server: Server, name: String = protocol.name) =
        Profile(name, null, ServerWrapper.makeWithServer(server, object : ServerDeliver {
            override fun hasAccessToServer(server: Server?) = true
            override fun getServer(wrapper: ServerWrapper?): Server = server
        }), ProfileColor.CARROT.id).apply {
            setProtocol(protocol)
        }

    private val serverListJson = """
        [
          {
            "bestScore": false,
            "city": "Toronto",
            "region": "Ontario",
            "connectingDomains": [
              {
                "entryDomain": "ca-01.protonvpn.com",
                "entryIp": "127.0.0.1",
                "exitIp": "127.0.0.1",
                "id": "1",
                "isOnline": true,
                "publicKeyX25519": "fake-key"
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
          },
          {
            "bestScore": false,
            "city": "Toronto",
            "connectingDomains": [
              {
                "entryDomain": "ca-02.protonvpn.com",
                "entryIp": "127.0.0.2",
                "exitIp": "127.0.0.2",
                "isOnline": true
              }
            ],
            "domain": "ca-02.protonvpn.com",
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
            "serverId": "2",
            "serverName": "CA#2",
            "tier": 1,
            "translatedCoordinates": {
              "positionX": 875.0,
              "positionY": 400.0
            },
            "mId": -1,
            "mSelectedAppsHandling": "SELECTED_APPS_DISABLE",
            "mUUID": "0911841d-86eb-4727-afa1-594bd0267e5f"
          },
          {
            "bestScore": false,
            "city": "New York City",
            "connectingDomains": [
              {
                "entryDomain": "us-ny-01.protonvpn.com",
                "entryIp": "127.0.0.3",
                "exitIp": "127.0.0.3",
                "isOnline": true
              }
            ],
            "domain": "us-ny-01.protonvpn.com",
            "entryCountry": "US",
            "entryCountryCoordinates": {
              "positionX": 760.0,
              "positionY": 700.0
            },
            "exitCountry": "US",
            "features": 0,
            "isOnline": true,
            "keywords": [],
            "load": "22",
            "location": {
              "latitude": "40.729999999999997",
              "longitude": "-73.935000000000002"
            },
            "score": 1.434756,
            "selectedAsFastest": false,
            "serverId": "3",
            "serverName": "US-NY#1",
            "tier": 1,
            "translatedCoordinates": {
              "positionX": 760.0,
              "positionY": 700.0
            },
            "mId": -1,
            "mSelectedAppsHandling": "SELECTED_APPS_DISABLE",
            "mUUID": "9097fd70-82de-445b-b8c6-b2579b3f4afa"
          },
          {
            "bestScore": false,
            "city": "Stockholm",
            "connectingDomains": [
              {
                "entryDomain": "se-01.protonvpn.com",
                "entryIp": "127.0.0.4",
                "exitIp": "127.0.0.4",
                "isOnline": true
              }
            ],
            "domain": "se-01.protonvpn.com",
            "entryCountry": "SE",
            "entryCountryCoordinates": {
              "positionX": 2485.0,
              "positionY": 300.0
            },
            "exitCountry": "SE",
            "features": 4,
            "isOnline": true,
            "keywords": [
              "p2p"
            ],
            "load": "10",
            "location": {
              "latitude": "59.329999999999998",
              "longitude": "18.059999999999999"
            },
            "score": 3.434756,
            "selectedAsFastest": false,
            "serverId": "4",
            "serverName": "SE#1",
            "tier": 1,
            "translatedCoordinates": {
              "positionX": 2485.0,
              "positionY": 300.0
            },
            "mId": -1,
            "mSelectedAppsHandling": "SELECTED_APPS_DISABLE",
            "mUUID": "6f0d1f35-0969-4745-8066-061f2caf539c"
          },
          {
            "bestScore": false,
            "city": "Paris",
            "translations": {
              "City": "Paryż"
            },
            "connectingDomains": [
              {
                "entryDomain": "fr-01.protonvpn.com",
                "entryIp": "127.0.0.5",
                "exitIp": "127.0.0.5",
                "isOnline": true
              }
            ],
            "domain": "fr-01.protonvpn.com",
            "entryCountry": "FR",
            "entryCountryCoordinates": {
              "positionX": 2310.0,
              "positionY": 567.0
            },
            "exitCountry": "FR",
            "features": 0,
            "isOnline": true,
            "keywords": [],
            "load": "10",
            "location": {
              "latitude": "48.859999999999999",
              "longitude": "2.3500000000000001"
            },
            "score": 3.434756,
            "selectedAsFastest": false,
            "serverId": "5",
            "serverName": "FR#1",
            "tier": 2,
            "translatedCoordinates": {
              "positionX": 2310.0,
              "positionY": 567.0
            },
            "mId": -1,
            "mSelectedAppsHandling": "SELECTED_APPS_DISABLE",
            "mUUID": "78e1617b-8b85-4772-ac97-c1a0a56ba1e6"
          },
          {
            "bestScore": false,
            "connectingDomains": [
              {
                "entryDomain": "se-fr-01.protonvpn.com",
                "entryIp": "127.0.0.6",
                "exitIp": "127.0.0.6",
                "isOnline": true
              }
            ],
            "domain": "se-fr-01.protonvpn.com",
            "entryCountry": "SE",
            "entryCountryCoordinates": {
              "positionX": 2485.0,
              "positionY": 300.0
            },
            "exitCountry": "FR",
            "features": 1,
            "isOnline": true,
            "keywords": [],
            "load": "10",
            "location": {
              "latitude": "48.859999999999999",
              "longitude": "2.3500000000000001"
            },
            "score": 4.434756,
            "selectedAsFastest": false,
            "serverId": "6",
            "serverName": "SE-FR#1",
            "tier": 2,
            "translatedCoordinates": {
              "positionX": 2310.0,
              "positionY": 567.0
            },
            "mId": -1,
            "mSelectedAppsHandling": "SELECTED_APPS_DISABLE",
            "mUUID": "3be99579-8324-45f3-bf23-d0949c0a2f27"
          },
          {
            "bestScore": false,
            "connectingDomains": [
              {
                "entryDomain": "se-fi-01.protonvpn.com",
                "entryIp": "127.0.0.7",
                "exitIp": "127.0.0.7",
                "isOnline": true
              }
            ],
            "domain": "se-fi-01.protonvpn.com",
            "entryCountry": "SE",
            "entryCountryCoordinates": {
              "positionX": 2485.0,
              "positionY": 300.0
            },
            "exitCountry": "FI",
            "features": 1,
            "isOnline": true,
            "keywords": [],
            "load": "10",
            "location": {
              "latitude": "60.174999999999997",
              "longitude": "24.940999999999999"
            },
            "score": 4.434756,
            "selectedAsFastest": false,
            "serverId": "9",
            "serverName": "SE-FI#1",
            "tier": 2,
            "translatedCoordinates": {
              "positionX": 2615.0,
              "positionY": 295.0
            },
            "mId": -1,
            "mSelectedAppsHandling": "SELECTED_APPS_DISABLE",
            "mUUID": "5356c959-c904-48c1-b112-01dd713de36c"
          },
          {
            "bestScore": false,
            "connectingDomains": [
              {
                "entryDomain": "fr-fi-01.protonvpn.com",
                "entryIp": "127.0.0.8",
                "exitIp": "127.0.0.8",
                "isOnline": true
              }
            ],
            "domain": "fr-fi-01.protonvpn.com",
            "entryCountry": "FR",
            "entryCountryCoordinates": {
              "positionX": 2485.0,
              "positionY": 300.0
            },
            "exitCountry": "FI",
            "features": 1,
            "isOnline": true,
            "keywords": [],
            "load": "10",
            "location": {
              "latitude": "60.174999999999997",
              "longitude": "24.940999999999999"
            },
            "score": 4.434756,
            "selectedAsFastest": false,
            "serverId": "10",
            "serverName": "FR-FI#1",
            "tier": 2,
            "translatedCoordinates": {
              "positionX": 2615.0,
              "positionY": 295.0
            },
            "mId": -1,
            "mSelectedAppsHandling": "SELECTED_APPS_DISABLE",
            "mUUID": "5356c959-c904-48c1-b112-01dd713de36c"
          },
          {
            "bestScore": false,
            "connectingDomains": [
              {
                "entryDomain": "fi-01.protonvpn.com",
                "entryIp": "127.0.0.9",
                "exitIp": "127.0.0.9",
                "isOnline": true
              }
            ],
            "domain": "fi-01.protonvpn.com",
            "entryCountry": "FI",
            "entryCountryCoordinates": {
              "positionX": 2615.0,
              "positionY": 295.0
            },
            "exitCountry": "FI",
            "features": 0,
            "isOnline": true,
            "keywords": [],
            "load": "10",
            "location": {
              "latitude": "60.174999999999997",
              "longitude": "24.940999999999999"
            },
            "score": 2.434756,
            "selectedAsFastest": false,
            "serverId": "12",
            "serverName": "FI#1",
            "tier": 1,
            "translatedCoordinates": {
              "positionX": 2615.0,
              "positionY": 295.0
            },
            "mId": -1,
            "mSelectedAppsHandling": "SELECTED_APPS_DISABLE",
            "mUUID": "673a3f4a-1cf9-41a9-a9ef-a55c63b15492"
          },
          {
            "bestScore": false,
            "city": "Stockholm",
            "connectingDomains": [
              {
                "entryDomain": "se-03.protonvpn.com",
                "entryIp": "127.0.0.10",
                "exitIp": "127.0.0.10",
                "isOnline": true
              }
            ],
            "domain": "se-03.protonvpn.com",
            "entryCountry": "SE",
            "entryCountryCoordinates": {
              "positionX": 2485.0,
              "positionY": 300.0
            },
            "exitCountry": "SE",
            "features": 4,
            "isOnline": false,
            "keywords": [
              "p2p"
            ],
            "load": "10",
            "location": {
              "latitude": "59.329999999999998",
              "longitude": "18.059999999999999"
            },
            "score": 3.434756,
            "selectedAsFastest": false,
            "serverId": "13",
            "serverName": "SE#3",
            "tier": 0,
            "translatedCoordinates": {
              "positionX": 2485.0,
              "positionY": 300.0
            },
            "mId": -1,
            "mSelectedAppsHandling": "SELECTED_APPS_DISABLE",
            "mUUID": "6f0d1f35-0969-4745-8066-061f2caf539c"
          }
        ]
    """.trimIndent()
}
