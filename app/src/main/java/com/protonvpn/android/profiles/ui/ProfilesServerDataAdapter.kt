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

import com.protonvpn.android.models.vpn.GatewayGroup
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.satisfiesFeatures
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

    suspend fun countries(feature: ServerFeature?) : List<TypeAndLocationScreenState.CountryItem> {
        val countries = serverManager.getVpnCountries().asSequence()
        return if (feature != null) {
            countries.filter { country -> country.serverList.any { it.features.hasFlag(feature.flag) } }
        } else {
            countries
        }.map { country ->
            TypeAndLocationScreenState.CountryItem(
                CountryId(country.id()),
                country.serverList.any { it.online }
            )
        }.toList()
    }

    suspend fun citiesOrStates(
        countryId: CountryId,
        secureCore: Boolean,
        feature: ServerFeature?,
    ) : List<TypeAndLocationScreenState.CityOrStateItem> {
        val country = serverManager.getVpnExitCountry(countryId.countryCode, secureCore)
            ?: return emptyList()
        val servers = country.serverList
            .filter { feature == null || it.features.hasFlag(feature.flag) }
            .asSequence()

        val allStates = servers
            .mapNotNull { server -> server.state.takeIf { !it.isNullOrBlank() } }
            .distinct()
            .map { CityStateId(it, true) to servers.any { it.online } }
            .toList()

        return allStates.ifEmpty {
            servers
                .mapNotNull { server -> server.city.takeIf { !it.isNullOrBlank() } }
                .distinct()
                .map { CityStateId(it, false) to servers.any { it.online } }
                .toList()
        }
        .map { (id, online) -> getCityStateViewModel(id, online) }
    }

    suspend fun servers(
        exitCountry: CountryId,
        cityOrState: CityStateId,
        secureCore: Boolean,
        feature: ServerFeature?,
    ) : List<TypeAndLocationScreenState.ServerItem> {
        val features = setOfNotNull(feature)
        return serverManager.getVpnExitCountry(exitCountry.countryCode, secureCore)
            ?.serverList
            ?.asSequence()
            ?.filter { !it.isFreeServer }
            ?.filter { it.isInCityOrState(cityOrState) && it.satisfiesFeatures(features) }
            ?.map { it.toViewState() }
            ?.toList()
            ?: emptyList()
    }

    suspend fun gateways() : List<TypeAndLocationScreenState.GatewayItem> =
        serverManager.getGateways().map { it.toViewState() }

    suspend fun gatewayServers(name: String) =
        serverManager.getGateways()
            .find { it.name() == name }
            ?.serverList
            ?.map { it.toViewState() }
            ?: emptyList()

    suspend fun secureCoreExits() : List<TypeAndLocationScreenState.CountryItem> =
        serverManager.getSecureCoreExitCountries().map { country ->
            TypeAndLocationScreenState.CountryItem(
                CountryId(country.id()),
                country.serverList.any { it.online }
            )
        }

    suspend fun secureCoreEntries(countryId: CountryId) : List<TypeAndLocationScreenState.CountryItem> =
        serverManager.getVpnExitCountry(countryId.countryCode, true)
            ?.serverList
            ?.map { it.entryCountry to it.online }
            ?.distinct()
            ?.map { (entryId, online) ->
                TypeAndLocationScreenState.CountryItem(CountryId(entryId), online)
            } ?: emptyList()

    suspend fun getCityOrStateForServerId(serverId: String?): CityStateId? {
        if (serverId == null) return null
        val server = serverManager.getServerById(serverId) ?: return null
        return when {
            server.state != null -> CityStateId(server.state, true)
            server.city != null -> CityStateId(server.city, false)
            else -> null
        }
    }

    private fun getCityStateViewModel(cityOrState: CityStateId, online: Boolean) = with(cityOrState) {
        TypeAndLocationScreenState.CityOrStateItem(
            when {
                isFastest -> null
                isState -> translator.getState(name)
                else -> translator.getCity(name)
            },
            this,
            online,
        )
    }

    suspend fun getServerViewModel(serverId: String?) =
        serverId?.let { serverManager.getServerById(it) }.toViewState()
}

private fun Server?.toViewState() =
    // Will be null/null (fastest) if serverId is not found.
    TypeAndLocationScreenState.ServerItem(
        this?.serverName,
        this?.serverId,
        if (this?.gatewayName != null) CountryId(exitCountry) else null,
        online = this?.online != false
    )

private fun GatewayGroup.toViewState() =
    TypeAndLocationScreenState.GatewayItem(
        name(),
        online = serverList.any { it.online }
    )

private fun Server.isInCityOrState(cityOrState: CityStateId) =
    (cityOrState.isState && state == cityOrState.name)
        || (!cityOrState.isState && city == cityOrState.name)