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

package com.protonvpn.android.utils

import com.protonvpn.android.models.vpn.StreamingService
import com.protonvpn.android.models.vpn.StreamingServicesResponse

private const val ALL_COUNTRIES = "*"

class StreamingServicesModel(private val streamingServicesResponse: StreamingServicesResponse) {

    val resourceBaseURL = streamingServicesResponse.resourceBaseURL

    fun get(country: String): Map<String, List<StreamingService>> {
        val defaults = streamingServicesResponse.countryToServices[ALL_COUNTRIES]
        val specific = streamingServicesResponse.countryToServices[country]
        return if (defaults != null && specific != null) {
            combineStreamingServices(defaults, specific)
        } else {
            defaults ?: specific ?: emptyMap()
        }
    }

    fun getForAllTiers(country: String): List<StreamingService> =
        get(country).flatMap { it.value }.distinctBy { it.name }

    private fun <T> combineStreamingServices(a: Map<String, List<T>>, b: Map<String, List<T>>): Map<String, List<T>> {
        val allKeys = a.keys + b.keys
        return allKeys.associateWith { key ->
            a.getOrDefault(key, emptyList()) + b.getOrDefault(key, emptyList())
        }
    }
}
