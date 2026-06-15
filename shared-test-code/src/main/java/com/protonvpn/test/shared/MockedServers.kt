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

import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.api.LogicalServer
import kotlinx.serialization.json.Json

object MockedServers {

    @Suppress("JSON_FORMAT_REDUNDANT")
    val logicalsList by lazy<List<LogicalServer>> {
        Json {
            isLenient = true
            ignoreUnknownKeys = true
        }.decodeFromString(serverListJson)
    }

    val serverList: List<Server> by lazy {
        logicalsList.map { logicalServer ->
            Server(
                serverId = logicalServer.serverId,
                entryCountry = logicalServer.entryCountry,
                rawExitCountry = logicalServer.exitCountry,
                serverName = logicalServer.serverName,
                connectingDomains = logicalServer.physicalServers,
                hostCountry = logicalServer.hostCountry,
                tier = logicalServer.tier,
                state = logicalServer.state,
                city = logicalServer.city,
                features = logicalServer.features,
                exitLocation = logicalServer.exitLocation,
                entryLocation = logicalServer.entryLocation,
                translations = logicalServer.translations,
                rawGatewayName = logicalServer.gatewayName,
                statusReference = logicalServer.statusReference,
                score = logicalServer.statusReference.penalty,
                load = 10f,
                rawIsOnline = true,
                isVisible = true,
            )
        }
    }

    val server by lazy { serverList.first() }

    @Suppress("ClassOrdering")
    val serverListJson = """
    [
      {
        "ID": "1",
        "EntryCountry": "CA",
        "ExitCountry": "CA",
        "Name": "CA#1",
        "Servers": [
          {
            "EntryIP": "127.0.0.1",
            "EntryPerProtocol": null,
            "Domain": "ca-01.protonvpn.com",
            "ID": "1",
            "Label": null,
            "Status": 1,
            "X25519PublicKey": "fake-key"
          }
        ],
        "StatusReference": {
          "Index": 1,
          "Penalty": 1.434756,
          "Cost": 0
        },
        "Tier": 1,
        "HostCountry": null,
        "State": null,
        "City": "Toronto",
        "Features": 0,
        "ExitLocation": {
          "Latitude": 43.63,
          "Longitude": -79.36
        },
        "EntryLocation": {
          "Latitude": 43.63,
          "Longitude": -79.36
        },
        "Translations": null,
        "GatewayName": null
      },
      {
        "ID": "2",
        "EntryCountry": "CA",
        "ExitCountry": "CA",
        "Name": "CA#2",
        "Servers": [
          {
            "EntryIP": "127.0.0.2",
            "EntryPerProtocol": null,
            "Domain": "ca-02.protonvpn.com",
            "ID": "2",
            "Label": "",
            "Status": 1,
            "X25519PublicKey": "fake-key"
          }
        ],
        "StatusReference": {
          "Index": 2,
          "Penalty": 1.5,
          "Cost": 0
        },
        "Tier": 1,
        "HostCountry": null,
        "State": null,
        "City": "Toronto",
        "Features": 0,
        "ExitLocation": {
          "Latitude": 43.63,
          "Longitude": -79.36
        },
        "EntryLocation": {
          "Latitude": 43.63,
          "Longitude": -79.36
        },
        "Translations": null,
        "GatewayName": null
      },
      {
        "ID": "3",
        "EntryCountry": "US",
        "ExitCountry": "US",
        "Name": "US-NY#1",
        "Servers": [
          {
            "EntryIP": "127.0.0.3",
            "EntryPerProtocol": null,
            "Domain": "us-ny-01.protonvpn.com",
            "ID": null,
            "Label": "",
            "Status": 1,
            "X25519PublicKey": "fake-key"
          }
        ],
        "StatusReference": {
          "Index": 3,
          "Penalty": 1.434756,
          "Cost": 0
        },
        "Tier": 1,
        "HostCountry": null,
        "State": null,
        "City": "New York City",
        "Features": 0,
        "ExitLocation": {
          "Latitude": 40.73,
          "Longitude": -73.93
        },
        "EntryLocation": {
          "Latitude": 40.73,
          "Longitude": -73.93
        },
        "Translations": null,
        "GatewayName": null
      },
      {
        "ID": "4",
        "EntryCountry": "SE",
        "ExitCountry": "SE",
        "Name": "SE#1",
        "Servers": [
          {
            "EntryIP": "127.0.0.4",
            "EntryPerProtocol": null,
            "Domain": "se-01.protonvpn.com",
            "ID": null,
            "Label": "",
            "Status": 1,
            "X25519PublicKey": "fake-key"
          }
        ],
        "StatusReference": {
          "Index": 4,
          "Penalty": 3.434756,
          "Cost": 0
        },
        "Tier": 1,
        "HostCountry": null,
        "State": null,
        "City": "Stockholm",
        "Features": 4,
        "ExitLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "EntryLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "Translations": null,
        "GatewayName": null
      },
      {
        "ID": "5",
        "EntryCountry": "FR",
        "ExitCountry": "FR",
        "Name": "FR#1",
        "Servers": [
          {
            "EntryIP": "127.0.0.5",
            "EntryPerProtocol": null,
            "Domain": "fr-01.protonvpn.com",
            "ID": null,
            "Label": "",
            "Status": 1,
            "X25519PublicKey": "fake-key"
          }
        ],
        "StatusReference": {
          "Index": 5,
          "Penalty": 3.434756,
          "Cost": 0
        },
        "Tier": 2,
        "HostCountry": null,
        "State": null,
        "City": "Paris",
        "Features": 0,
        "ExitLocation": {
          "Latitude": 48.86,
          "Longitude": 2.35
        },
        "EntryLocation": {
          "Latitude": 48.86,
          "Longitude": 2.35
        },
        "Translations": {
          "City": "Paryż"
        },
        "GatewayName": null
      },
      {
        "ID": "6",
        "EntryCountry": "SE",
        "ExitCountry": "FR",
        "Name": "SE-FR#1",
        "Servers": [
          {
            "EntryIP": "127.0.0.6",
            "EntryPerProtocol": null,
            "Domain": "se-fr-01.protonvpn.com",
            "ID": null,
            "Label": "",
            "Status": 1,
            "X25519PublicKey": "fake-key"
          }
        ],
        "StatusReference": {
          "Index": 6,
          "Penalty": 4.434756,
          "Cost": 0
        },
        "Tier": 2,
        "HostCountry": null,
        "State": null,
        "City": null,
        "Features": 1,
        "ExitLocation": {
          "Latitude": 48.86,
          "Longitude": 2.35
        },
        "EntryLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "Translations": null,
        "GatewayName": null
      },
      {
        "ID": "7",
        "EntryCountry": "SE",
        "ExitCountry": "FI",
        "Name": "SE-FI#1",
        "Servers": [
          {
            "EntryIP": "127.0.0.7",
            "EntryPerProtocol": null,
            "Domain": "se-fi-01.protonvpn.com",
            "ID": null,
            "Label": "",
            "Status": 1,
            "X25519PublicKey": "fake-key"
          }
        ],
        "StatusReference": {
          "Index": 7,
          "Penalty": 4.434756,
          "Cost": 0
        },
        "Tier": 2,
        "HostCountry": null,
        "State": null,
        "City": null,
        "Features": 1,
        "ExitLocation": {
          "Latitude": 60.17,
          "Longitude": 24.94
        },
        "EntryLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "Translations": null,
        "GatewayName": null
      },
      {
        "ID": "8",
        "EntryCountry": "CH",
        "ExitCountry": "FI",
        "Name": "CH-FI#1",
        "Servers": [
          {
            "EntryIP": "127.0.0.8",
            "EntryPerProtocol": null,
            "Domain": "ch-fi-01.protonvpn.com",
            "ID": null,
            "Label": "",
            "Status": 1,
            "X25519PublicKey": "fake-key"
          }
        ],
        "StatusReference": {
          "Index": 8,
          "Penalty": 4.434756,
          "Cost": 0
        },
        "Tier": 2,
        "HostCountry": null,
        "State": null,
        "City": null,
        "Features": 1,
        "ExitLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "EntryLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "Translations": null,
        "GatewayName": null
      },
      {
        "ID": "9",
        "EntryCountry": "FI",
        "ExitCountry": "FI",
        "Name": "FI#1",
        "Servers": [
          {
            "EntryIP": "127.0.0.9",
            "EntryPerProtocol": null,
            "Domain": "fi-01.protonvpn.com",
            "ID": null,
            "Label": "",
            "Status": 1,
            "X25519PublicKey": "fake-key"
          }
        ],
        "StatusReference": {
          "Index": 9,
          "Penalty": 2.434756,
          "Cost": 0
        },
        "Tier": 1,
        "HostCountry": null,
        "State": null,
        "City": null,
        "Features": 0,
        "ExitLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "EntryLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "Translations": null,
        "GatewayName": null
      },
      {
        "ID": "10",
        "EntryCountry": "SE",
        "ExitCountry": "SE",
        "Name": "SE#3",
        "Servers": [
          {
            "EntryIP": "127.0.0.10",
            "EntryPerProtocol": null,
            "Domain": "se-03.protonvpn.com",
            "ID": null,
            "Label": "",
            "Status": 0,
            "X25519PublicKey": "fake-key"
          }
        ],
        "StatusReference": {
          "Index": 10,
          "Penalty": 3.434756,
          "Cost": 0
        },
        "Tier": 0,
        "HostCountry": null,
        "State": null,
        "City": null,
        "Features": 4,
        "ExitLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "EntryLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "Translations": null,
        "GatewayName": null
      },
      {
        "ID": "11",
        "EntryCountry": "HK",
        "ExitCountry": "HK",
        "Name": "HK#1",
        "Servers": [
          {
            "EntryIP": "127.0.0.11",
            "EntryPerProtocol": null,
            "Domain": "se-03.protonvpn.com",
            "ID": "11",
            "Label": "",
            "Status": 1,
            "X25519PublicKey": "fake-key"
          }
        ],
        "StatusReference": {
          "Index": 11,
          "Penalty": 2.5216639000000001,
          "Cost": 0
        },
        "Tier": 1,
        "HostCountry": null,
        "State": null,
        "City": "Hong Kong",
        "Features": 4,
        "ExitLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "EntryLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "Translations": null,
        "GatewayName": null
      },
      {
        "ID": "12",
        "EntryCountry": "UA",
        "ExitCountry": "UA",
        "Name": "UA#9",
        "Servers": [
          {
            "EntryIP": "127.0.0.12",
            "EntryPerProtocol": null,
            "Domain": "ua-09.protonvpn.com",
            "ID": "12",
            "Label": "",
            "Status": 1,
            "X25519PublicKey": "fake-key"
          }
        ],
        "StatusReference": {
          "Index": 12,
          "Penalty": 2.2122074299999999,
          "Cost": 0
        },
        "Tier": 1,
        "HostCountry": null,
        "State": null,
        "City": "Kyiv",
        "Features": 0,
        "ExitLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "EntryLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "Translations": null,
        "GatewayName": null
      },
      {
        "ID": "13",
        "EntryCountry": "UA",
        "ExitCountry": "UA",
        "Name": "UA#10",
        "Servers": [
          {
            "EntryIP": "127.0.0.13",
            "EntryPerProtocol": null,
            "Domain": "ua-10.protonvpn.com",
            "ID": "13",
            "Label": "",
            "Status": 1,
            "X25519PublicKey": "fake-key"
          }
        ],
        "StatusReference": {
          "Index": 13,
          "Penalty": 2.30711003,
          "Cost": 0
        },
        "Tier": 0,
        "HostCountry": null,
        "State": null,
        "City": "Kyiv",
        "Features": 0,
        "ExitLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "EntryLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "Translations": null,
        "GatewayName": null
      },
      {
        "ID": "14",
        "EntryCountry": "EG",
        "ExitCountry": "EG",
        "Name": "EG#11",
        "Servers": [
          {
            "EntryIP": "127.0.0.14",
            "EntryPerProtocol": null,
            "Domain": "node-eg-01.protonvpn.net",
            "ID": "14",
            "Label": "",
            "Status": 1,
            "X25519PublicKey": "fake-key"
          }
        ],
        "StatusReference": {
          "Index": 14,
          "Penalty": 1.28381163,
          "Cost": 0
        },
        "Tier": 2,
        "HostCountry": "RO",
        "State": null,
        "City": "Cairo",
        "Features": 0,
        "ExitLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "EntryLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "Translations": null,
        "GatewayName": null
      },
      {
        "ID": "15",
        "EntryCountry": "CH",
        "ExitCountry": "CH",
        "Name": "CH#301#PARTNER",
        "Servers": [
          {
            "EntryIP": "127.0.0.15",
            "EntryPerProtocol": null,
            "Domain": "ch-301.protonvpn.net",
            "ID": "15",
            "Label": "partner",
            "Status": 1,
            "X25519PublicKey": "fake-key",
            "Signature": "fake-signature"
          }
        ],
        "StatusReference": {
          "Index": 15,
          "Penalty": 99.0,
          "Cost": 0
        },
        "Tier": 0,
        "HostCountry": null,
        "State": null,
        "City": null,
        "Features": 64,
        "ExitLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "EntryLocation": {
          "Latitude": 59.33,
          "Longitude": 18.05
        },
        "Translations": null,
        "GatewayName": null
      }
    ]
        """.trimIndent()
    }
