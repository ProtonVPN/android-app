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

import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ProfileColor
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.Server
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object MockedServers {

    @Suppress("JSON_FORMAT_REDUNDANT")
    val serverList by lazy<List<Server>> {
        Json {
            isLenient = true
            ignoreUnknownKeys = true
        }.decodeFromString(serverListJson)
    }

    val server by lazy { serverList.first() }

    fun getProfile(protocol: VpnProtocol, server: Server, name: String = protocol.name) =
        Profile(name, null, ServerWrapper.makeWithServer(server), ProfileColor.CARROT.id, null).apply {
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
    "Score": 1.5,
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
  },
  {
    "Name": "HK#1",
    "EntryCountry": "HK",
    "ExitCountry": "HK",
    "Domain": "hk-01.protonvpn.net",
    "Tier": 1,
    "Features": 4,
    "Region": null,
    "City": "Hong Kong",
    "Score": 2.5216639000000001,
    "HostCountry": null,
    "ID": "j_hMLdlh76xys5eR2S3OM9vlAgYylGQBiEzDeXLw1H-huHy2jwjwVqcKAPcdd6z2cXoklLuQTegkr3gnJXCB9w==",
    "Location": {
      "Lat": 22.280000000000001,
      "Long": 114.14
    },
    "Status": 1,
    "Servers": [
      {
        "EntryIP": "209.58.185.231",
        "ExitIP": "209.58.185.231",
        "Domain": "hk-01.protonvpn.net",
        "ID": "j_hMLdlh76xys5eR2S3OM9vlAgYylGQBiEzDeXLw1H-huHy2jwjwVqcKAPcdd6z2cXoklLuQTegkr3gnJXCB9w==",
        "Label": "0",
        "X25519PublicKey": "nzkzjzhYV96YhWXVdhECFTymFb56q0VoTp9jSLuufng=",
        "Generation": 0,
        "Status": 1,
        "ServicesDown": 0,
        "ServicesDownReason": null
      }
    ],
    "Load": 43
  },
  {
    "Name": "UA#9",
    "EntryCountry": "UA",
    "ExitCountry": "UA",
    "Domain": "ua-09.protonvpn.net",
    "Tier": 1,
    "Features": 0,
    "Region": null,
    "City": "Kyiv",
    "Score": 2.2122074299999999,
    "HostCountry": null,
    "ID": "GWPFZO6TPCOsObEFVKv42FiXNxO-2WRBhzd-76Mcs7gn1IypC1-zhcf_4BkJcSIOAucz9-EUR8-tgfng32dzJA==",
    "Location": {
      "Lat": 50.450000000000003,
      "Long": 30.52
    },
    "Status": 1,
    "Servers": [
      {
        "EntryIP": "156.146.50.1",
        "ExitIP": "156.146.50.1",
        "Domain": "ua-09.protonvpn.net",
        "ID": "AoG2GRy5in0JrHq2eyb_SUDjwG_iGm8K7TrRWStg3i1BCfQ9AV4bL4SPzQVQ-2w8S3NK21bpGpI0OBwuCoOcAQ==",
        "Label": "0",
        "X25519PublicKey": "o0AixWIjxr61AwsKjrTIM+f9iHWZlWUOYZQyroX+zz4=",
        "Generation": 0,
        "Status": 1,
        "ServicesDown": 0,
        "ServicesDownReason": null
      }
    ],
    "Load": 23
  },
  {
    "Name": "UA#10",
    "EntryCountry": "UA",
    "ExitCountry": "UA",
    "Domain": "ua-09.protonvpn.net",
    "Tier": 0,
    "Features": 0,
    "Region": null,
    "City": "Kyiv",
    "Score": 2.30711003,
    "HostCountry": null,
    "ID": "XlKVIOZ-vm7XssmpRY_bLudffHRryyralicwjyMfm-REsUdH4uuFIc1LbSzgnoP307GkEyywH6Iqg_zlzZ3NBg==",
    "Location": {
      "Lat": 50.450000000000003,
      "Long": 30.52
    },
    "Status": 1,
    "Servers": [
      {
        "EntryIP": "156.146.50.1",
        "ExitIP": "156.146.50.2",
        "Domain": "ua-09.protonvpn.net",
        "ID": "9eVEHuzMUgE0XFhU_G-rUuFl223g202o9ekKHV2w_VO8-mecBb0fOtLTBDxKcXJj2RxhbZG2ZwUHuwhLv5B_aA==",
        "Label": "1",
        "X25519PublicKey": "o0AixWIjxr61AwsKjrTIM+f9iHWZlWUOYZQyroX+zz4=",
        "Generation": 0,
        "Status": 1,
        "ServicesDown": 0,
        "ServicesDownReason": null
      }
    ],
    "Load": 69
  },
  {
      "Name": "EG#1",
      "EntryCountry": "EG",
      "ExitCountry": "EG",
      "Domain": "node-eg-01.protonvpn.net",
      "Tier": 2,
      "Features": 0,
      "Region": null,
      "City": "Cairo",
      "Score": 1.28381163,
      "HostCountry": "RO",
      "ID": "HSOB9ERcsgQ4zRb7nLM7Fo-xTbeIEkrIrw-BTMcJAo-u6P9Zgfq7HhZdECRvhEFiXg4TXgAbdANRDNWFf_NHlg==",
      "Location": {
        "Lat": 30.039999999999999,
        "Long": 31.23
      },
      "Status": 1,
      "Servers": [
        {
          "EntryIP": "188.214.122.82",
          "ExitIP": "188.214.122.83",
          "Domain": "node-eg-01.protonvpn.net",
          "ID": "AhMR__eJY-NbWniGOcW3V8ihBpBjvCc59DFj2IZy4bLe3K0aRrIQXN9jlND-Wcl4Rb5fxPbqE82d06tMlIPXlw==",
          "Label": "0",
          "X25519PublicKey": "DUtOX4QuHcmlBk7bI5eoCSp8RLqV7NPIU8pywn1w0k0=",
          "Generation": 0,
          "Status": 1,
          "ServicesDown": 0,
          "ServicesDownReason": null
        }
      ],
      "Load": 36
    }
]
    """.trimIndent()
}
