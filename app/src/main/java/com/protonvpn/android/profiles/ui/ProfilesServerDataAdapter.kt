/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.android.profiles.ui

import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.utils.hasFlag
import dagger.Reusable
import javax.inject.Inject

@Reusable
class ProfilesServerDataAdapter @Inject constructor(
    private val serverManager: ServerManager2,
    private val translator: Translator,
) {
    val hasAnyGatewaysFlow get() = serverManager.hasAnyGatewaysFlow
    val serverListVersion get() = serverManager.serverListVersion

    suspend fun countries(feature: ServerFeature?) : List<CountryId> {
        val countries = serverManager.getVpnCountries()
        return if (feature != null) {
            countries.filter { country -> country.serverList.any { it.features.hasFlag(feature.flag) } }
        } else {
            countries
        }.map { CountryId(it.id()) }
    }

    suspend fun citiesOrStates(
        countryId: CountryId,
        secureCore: Boolean,
        feature: ServerFeature?,
    ) : List<TypeAndLocationScreenState.CityOrState> {
        val country = serverManager.getVpnExitCountry(countryId.countryCode, secureCore)
            ?: return emptyList()
        val servers = country.serverList
            .filter { feature == null || it.features.hasFlag(feature.flag) }
            .asSequence()

        val allStates = servers
            .mapNotNull { server -> server.state.takeIf { !it.isNullOrBlank() } }
            .distinct()
            .map { CityStateId(it, true) }
            .toList()

        return allStates.ifEmpty {
            servers
                .mapNotNull { server -> server.city.takeIf { !it.isNullOrBlank() } }
                .distinct()
                .map { CityStateId(it, false) }
                .toList()
        }
        .map { getCityStateViewModel(it) }
    }

    suspend fun servers(
        exitCountry: CountryId,
        cityOrState: CityStateId,
        secureCore: Boolean,
        feature: ServerFeature?,
    ) : List<TypeAndLocationScreenState.Server> =
        serverManager.getVpnExitCountry(exitCountry.countryCode, secureCore)
            ?.serverList
            ?.filter { it.isInCityOrState(cityOrState) && (feature == null || it.features.hasFlag(feature.flag)) }
            ?.map { it.toViewModel() }
            ?: emptyList()

    suspend fun gatewaysNames() =
        serverManager.getGateways().map { it.name() }

    suspend fun gatewayServers(name: String) =
        serverManager.getGateways()
            .find { it.name() == name }
            ?.serverList
            ?.map { it.toViewModel() }
            ?: emptyList()

    suspend fun secureCoreExits() =
        serverManager.getSecureCoreExitCountries().map { CountryId(it.id()) }

    suspend fun secureCoreEntries(countryId: CountryId) =
        serverManager.getVpnExitCountry(countryId.countryCode, true)
            ?.serverList
            ?.map { it.entryCountry }
            ?.distinct()
            ?.map { CountryId(it) }
            ?: emptyList()

    suspend fun getCityOrStateForServerId(serverId: String?): CityStateId? {
        if (serverId == null) return null
        val server = serverManager.getServerById(serverId) ?: return null
        return when {
            server.state != null -> CityStateId(server.state, true)
            server.city != null -> CityStateId(server.city, false)
            else -> null
        }
    }

    fun getCityStateViewModel(cityOrState: CityStateId) = with(cityOrState) {
        TypeAndLocationScreenState.CityOrState(
            when {
                isFastest -> null
                isState -> translator.getState(name)
                else -> translator.getCity(name)
            },
            this
        )
    }

    suspend fun getServerViewModel(serverId: String?) =
        serverId?.let { serverManager.getServerById(it) }.toViewModel()
}

private fun Server?.toViewModel() =
    // Will be null/null (fastest) if serverId is not found.
    TypeAndLocationScreenState.Server(
        this?.serverName,
        this?.serverId,
        if (this?.gatewayName != null) CountryId(exitCountry) else null,
    )

private fun Server.isInCityOrState(cityOrState: CityStateId) =
    (cityOrState.isState && state == cityOrState.name)
        || (!cityOrState.isState && city == cityOrState.name)