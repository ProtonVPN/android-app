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

import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ProfileColor
import com.protonvpn.android.models.profiles.ServerDeliver
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.Server
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object MockedServers {

    @Suppress("JSON_FORMAT_REDUNDANT")
    val serverList by lazy<List<Server>> {
        Json {
            isLenient = true
        }.decodeFromString(serverListJson)
    }

    val server by lazy { serverList.first() }

    fun getProfile(protocol: VpnProtocol, server: Server, name: String = protocol.name) =
        Profile(name, null, ServerWrapper.makeWithServer(server, object : ServerDeliver {
            override fun hasAccessToServer(server: Server?) = true
            override fun getServer(wrapper: ServerWrapper?): Server = server
        }), ProfileColor.CARROT.id).apply {
            setProtocol(protocol)
        }

    @Suppress("ClassOrdering")
    private val serverListJson = """
[
  {
    "ID": "1",
    "EntryCountry": "CA",
    "ExitCountry": "CA",
    "Name": "CA#1",
    "Servers": [
      {
        "EntryIP": "127.0.0.1",
        "Domain": "ca-01.protonvpn.com",
        "ExitIP": "127.0.0.1",
        "ID": "1",
        "X25519PublicKey": "fake-key"
      }
    ],
    "Domain": "ca-01.protonvpn.com",
    "Load": 22.0,
    "Tier": 1,
    "Region": "Ontario",
    "City": "Toronto",
    "Features": 0,
    "Location": {
      "Lat": "43.6328999999999999999",
      "Long": "-79.36109999999999993"
    },
    "Score": 1.434756,
    "Status": 1
  },
  {
    "ID": "2",
    "EntryCountry": "CA",
    "ExitCountry": "CA",
    "Name": "CA#2",
    "Servers": [
      {
        "EntryIP": "127.0.0.2",
        "Domain": "ca-02.protonvpn.com",
        "ExitIP": "127.0.0.2",
        "ID": null
      }
    ],
    "Domain": "ca-02.protonvpn.com",
    "Load": 22.0,
    "Tier": 1,
    "Region": null,
    "City": "Toronto",
    "Features": 0,
    "Location": {
      "Lat": "43.6328999999999999999",
      "Long": "-79.36109999999999993"
    },
    "Score": 1.434756,
    "Status": 1
  },
  {
    "ID": "3",
    "EntryCountry": "US",
    "ExitCountry": "US",
    "Name": "US-NY#1",
    "Servers": [
      {
        "EntryIP": "127.0.0.3",
        "Domain": "us-ny-01.protonvpn.com",
        "ExitIP": "127.0.0.3",
        "ID": null
      }
    ],
    "Domain": "us-ny-01.protonvpn.com",
    "Load": 22.0,
    "Tier": 1,
    "Region": null,
    "City": "New York City",
    "Features": 0,
    "Location": {
      "Lat": "40.729999999999997",
      "Long": "-73.935000000000002"
    },
    "Score": 1.434756,
    "Status": 1
  },
  {
    "ID": "4",
    "EntryCountry": "SE",
    "ExitCountry": "SE",
    "Name": "SE#1",
    "Servers": [
      {
        "EntryIP": "127.0.0.4",
        "Domain": "se-01.protonvpn.com",
        "ExitIP": "127.0.0.4",
        "ID": null
      }
    ],
    "Domain": "se-01.protonvpn.com",
    "Load": 10.0,
    "Tier": 1,
    "Region": null,
    "City": "Stockholm",
    "Features": 4,
    "Location": {
      "Lat": "59.329999999999998",
      "Long": "18.059999999999999"
    },
    "Score": 3.434756,
    "Status": 1
  },
  {
    "ID": "5",
    "EntryCountry": "FR",
    "ExitCountry": "FR",
    "Name": "FR#1",
    "Servers": [
      {
        "EntryIP": "127.0.0.5",
        "Domain": "fr-01.protonvpn.com",
        "ExitIP": "127.0.0.5",
        "ID": null
      }
    ],
    "Domain": "fr-01.protonvpn.com",
    "Load": 10.0,
    "Tier": 2,
    "Region": null,
    "City": "Paris",
    "Features": 0,
    "Location": {
      "Lat": "48.859999999999999",
      "Long": "2.3500000000000001"
    },
    "Translations": {
      "City": "Paryż"
    },
    "Score": 3.434756,
    "Status": 1
  },
  {
    "ID": "6",
    "EntryCountry": "SE",
    "ExitCountry": "FR",
    "Name": "SE-FR#1",
    "Servers": [
      {
        "EntryIP": "127.0.0.6",
        "Domain": "se-fr-01.protonvpn.com",
        "ExitIP": "127.0.0.6",
        "ID": null
      }
    ],
    "Domain": "se-fr-01.protonvpn.com",
    "Load": 10.0,
    "Tier": 2,
    "Region": null,
    "City": null,
    "Features": 1,
    "Location": {
      "Lat": "48.859999999999999",
      "Long": "2.3500000000000001"
    },
    "Score": 4.434756,
    "Status": 1
  },
  {
    "ID": "9",
    "EntryCountry": "SE",
    "ExitCountry": "FI",
    "Name": "SE-FI#1",
    "Servers": [
      {
        "EntryIP": "127.0.0.7",
        "Domain": "se-fi-01.protonvpn.com",
        "ExitIP": "127.0.0.7",
        "ID": null
      }
    ],
    "Domain": "se-fi-01.protonvpn.com",
    "Load": 10.0,
    "Tier": 2,
    "Region": null,
    "City": null,
    "Features": 1,
    "Location": {
      "Lat": "60.174999999999997",
      "Long": "24.940999999999999"
    },
    "Score": 4.434756,
    "Status": 1
  },
  {
    "ID": "10",
    "EntryCountry": "FR",
    "ExitCountry": "FI",
    "Name": "FR-FI#1",
    "Servers": [
      {
        "EntryIP": "127.0.0.8",
        "Domain": "fr-fi-01.protonvpn.com",
        "ExitIP": "127.0.0.8",
        "ID": null
      }
    ],
    "Domain": "fr-fi-01.protonvpn.com",
    "Load": 10.0,
    "Tier": 2,
    "Region": null,
    "City": null,
    "Features": 1,
    "Location": {
      "Lat": "60.174999999999997",
      "Long": "24.940999999999999"
    },
    "Score": 4.434756,
    "Status": 1
  },
  {
    "ID": "12",
    "EntryCountry": "FI",
    "ExitCountry": "FI",
    "Name": "FI#1",
    "Servers": [
      {
        "EntryIP": "127.0.0.9",
        "Domain": "fi-01.protonvpn.com",
        "ExitIP": "127.0.0.9",
        "ID": null
      }
    ],
    "Domain": "fi-01.protonvpn.com",
    "Load": 10.0,
    "Tier": 1,
    "Region": null,
    "City": null,
    "Features": 0,
    "Location": {
      "Lat": "60.174999999999997",
      "Long": "24.940999999999999"
    },
    "Score": 2.434756,
    "Status": 1
  },
  {
    "ID": "13",
    "EntryCountry": "SE",
    "ExitCountry": "SE",
    "Name": "SE#3",
    "Servers": [
      {
        "EntryIP": "127.0.0.10",
        "Domain": "se-03.protonvpn.com",
        "ExitIP": "127.0.0.10",
        "ID": null
      }
    ],
    "Domain": "se-03.protonvpn.com",
    "Load": 10.0,
    "Tier": 0,
    "Region": null,
    "City": "Stockholm",
    "Features": 4,
    "Location": {
      "Lat": "59.329999999999998",
      "Long": "18.059999999999999"
    },
    "Score": 3.434756,
    "Status": 0
  }
]
    """.trimIndent()
}
