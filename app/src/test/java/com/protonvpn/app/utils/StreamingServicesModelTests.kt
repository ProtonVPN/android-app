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

package com.protonvpn.app.utils

import com.protonvpn.android.models.vpn.StreamingService
import com.protonvpn.android.models.vpn.StreamingServicesResponse
import com.protonvpn.android.utils.StreamingServicesModel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private const val streamingServicesWithWildcardJson =
    """
{
  "ResourceBaseURL": "https://localhost",
  "StreamingServices": {
    "*": {
      "1": [
        {
          "Name": "Service * 1",
          "Icon": "1.jpg",
          "ColoredIcon": "colored1.jpg"
        },
        {
          "Name": "Service common",
          "Icon": "common.jpg",
          "ColoredIcon": "coloredCommon.jpg"
        }
      ],
      "2": [
        {
          "Name": "Service * 2",
          "Icon": "2.jpg",
          "ColoredIcon": "colored2.jpg"
        }
      ]
    },
    "PL": {
      "1": [
        {
          "Name": "Service PL 1",
          "Icon": "1.jpg",
          "ColoredIcon": "colored1.jpg"
        }
      ],
      "2": [
        {
          "Name": "Service common",
          "Icon": "common.jpg",
          "ColoredIcon": "coloredCommon.jpg"
        }
      ]
    },
    "LT": {
      "1": [
        {
          "Name": "Service LT 1",
          "Icon": "1.jpg",
          "ColoredIcon": "colored1.jpg"
        }
      ]
    }
  }
}
    """

class StreamingServicesModelTests {

    private val streamingServicesWithWildcardResponse: StreamingServicesResponse =
        Json.decodeFromString(streamingServicesWithWildcardJson)

    private lateinit var streamingServicesModel: StreamingServicesModel
    private lateinit var streamingServicesWithWildcardModel: StreamingServicesModel

    @Before
    fun setup() {
        val streamingServicesResponse = StreamingServicesResponse(
            streamingServicesWithWildcardResponse.resourceBaseURL,
            streamingServicesWithWildcardResponse.countryToServices.filterNot { it.key == "*" }
        )
        streamingServicesModel = StreamingServicesModel(streamingServicesResponse)
        streamingServicesWithWildcardModel = StreamingServicesModel(streamingServicesWithWildcardResponse)
    }

    @Test
    fun getServicesForCountry() {
        val servicesMap = streamingServicesModel.get("PL")

        assertEquals(
            mapOf(
                "1" to listOf(StreamingService("Service PL 1", "1.jpg", "colored1.jpg")),
                "2" to listOf(StreamingService("Service common", "common.jpg", "coloredCommon.jpg"))
            ),
            servicesMap
        )
    }

    @Test
    fun getServicesForCountryWithWildcard() {
        val servicesMap = streamingServicesWithWildcardModel.get("PL")

        assertEquals(
            mapOf(
                "1" to listOf(
                    StreamingService("Service * 1", "1.jpg", "colored1.jpg"),
                    StreamingService("Service common", "common.jpg", "coloredCommon.jpg"),
                    StreamingService("Service PL 1", "1.jpg", "colored1.jpg"),
                ),
                "2" to listOf(
                    StreamingService("Service * 2", "2.jpg", "colored2.jpg"),
                    StreamingService("Service common", "common.jpg", "coloredCommon.jpg")
                ),
            ),
            servicesMap
        )
    }

    @Test
    fun getServicesForUnknownCountry() {
        val servicesMap = streamingServicesModel.get("Unknown")

        assertEquals(emptyMap<String, List<StreamingService>>(), servicesMap)
    }

    @Test
    fun getServicesForUnknownCountryWithWildcard() {
        val servicesMap = streamingServicesWithWildcardModel.get("Unknown")

        assertEquals(
            mapOf(
                "1" to listOf(
                    StreamingService("Service * 1", "1.jpg", "colored1.jpg"),
                    StreamingService("Service common", "common.jpg","coloredCommon.jpg"),
                ),
                "2" to listOf(
                    StreamingService("Service * 2", "2.jpg", "colored2.jpg"),
                )
            ),
            servicesMap
        )
    }

    @Test
    fun getServicesForAllTiersForCountry() {
        val services = streamingServicesModel.getForAllTiers("PL")

        assertEquals(
            listOf(
                StreamingService("Service PL 1", "1.jpg", "colored1.jpg"),
                StreamingService("Service common", "common.jpg", "coloredCommon.jpg"),
            ),
            services
        )
    }

    @Test
    fun getServicesForAllTiersForCountryWithWildcard() {
        val services = streamingServicesWithWildcardModel.getForAllTiers("PL")

        assertEquals(
            setOf(
                StreamingService("Service PL 1", "1.jpg", "colored1.jpg"),
                StreamingService("Service common", "common.jpg", "coloredCommon.jpg"),
                StreamingService("Service * 1", "1.jpg", "colored1.jpg"),
                StreamingService("Service * 2", "2.jpg", "colored2.jpg")
            ),
            services.toSet()
        )
    }
}
