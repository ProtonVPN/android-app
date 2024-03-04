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

package com.protonvpn.android.redesign.countries.ui

import com.protonvpn.android.models.vpn.SERVER_FEATURE_P2P
import com.protonvpn.android.models.vpn.SERVER_FEATURE_TOR
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.ServerId
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.utils.hasFlag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.math.roundToInt

class CountryListViewModelDataAdapterLegacy @Inject constructor(
    private val serverManager2: ServerManager2,
) : CountryListViewModelDataAdapter {

    override fun countries(
        filter: ServerListFilter,
    ): Flow<List<CountryListItemData.Country>> =
        serverManager2.allServersFlow.map { servers ->
            val entryCountryId = if (filter.type == ServerFilterType.SecureCore)
                CountryId.fastest
            else
                null
            val countries = servers
                .asFilteredSequence(filter)
                .map { it.exitCountry }
                .distinct()
                .toList()
            countries.mapNotNull { countryCode ->
                serverManager2
                    .getVpnExitCountry(countryCode, false)
                    ?.serverList
                    ?.takeIf { it.isNotEmpty() }
                    ?.toCountryItem(countryCode, entryCountryId)
            }
        }

    override fun cities(
        filter: ServerListFilter,
    ): Flow<List<CountryListItemData.City>> =
        serverManager2.allServersFlow.map { servers ->
            val filteredServers = servers.asFilteredSequence(filter)
            val hasRegions = filteredServers.any { it.region != null }
            val groupBySelector = if (hasRegions) Server::region else Server::city
            filteredServers
                .groupBy(groupBySelector)
                .mapNotNull { (cityOrRegion, servers) -> toCityItem(hasRegions, cityOrRegion, servers) }
        }

    override fun servers(
        filter: ServerListFilter,
    ): Flow<List<CountryListItemData.Server>> =
        serverManager2.allServersFlow.map { servers ->
            servers
                .asFilteredSequence(filter)
                .map(Server::toServerItem)
                .toList()
        }

    private fun List<Server>.asFilteredSequence(filter: ServerListFilter) =
        asSequence().filter { filter.isMatching(it) }

    override fun includesServer(data: CountryListItemData, server: Server): Boolean {
        return when (data) {
            is CountryListItemData.Country -> data.countryId.countryCode == server.exitCountry
            is CountryListItemData.City -> data.countryId.countryCode == server.exitCountry && data.cityStateId.matches(server)
            is CountryListItemData.Server -> data.serverId.id == server.serverId
        }
    }
}

fun List<Server>.toCountryItem(countryCode: String, entryCountryId: CountryId?) = CountryListItemData.Country(
    countryId = CountryId(countryCode),
    entryCountryId = entryCountryId,
    inMaintenance = all { !it.online },
    tier = minOf { it.tier }
)

fun Server.toServerItem() = CountryListItemData.Server(
    countryId = CountryId(exitCountry),
    serverId = ServerId(serverId),
    name = serverName,
    loadPercent = load.roundToInt(),
    serverFeatures = serverFeatures,
    isVirtualLocation = hostCountry != null && hostCountry != exitCountry,
    inMaintenance = !online,
    tier = tier
)

fun toCityItem(isRegion: Boolean, cityOrRegion: String?, servers: List<Server>) : CountryListItemData.City? {
    if (cityOrRegion == null || servers.isEmpty())
        return null

    val server = servers.first()
    //TODO: what to do with servers without a region if hasRegions is true
    return CountryListItemData.City(
        countryId = CountryId(server.exitCountry),
        cityStateId = CityStateId(cityOrRegion, isRegion),
        name = (if (isRegion) server.region else server.displayCity) ?: cityOrRegion,
        inMaintenance = servers.all { !it.online },
        tier = servers.minOf { it.tier }
    )
}


val Server.serverFeatures get() = buildSet {
    if (features.hasFlag(SERVER_FEATURE_P2P))
        add(ServerFeature.P2P)
    if (features.hasFlag(SERVER_FEATURE_TOR))
        add(ServerFeature.Tor)
}

fun CityStateId.matches(server: Server) =
    name == if (isState) server.region else server.city

fun ServerFilterType.isMatching(server: Server) = when (this) {
    ServerFilterType.All -> !server.isSecureCoreServer
    ServerFilterType.SecureCore -> server.isSecureCoreServer
    ServerFilterType.Tor -> server.isTor
    ServerFilterType.P2P -> server.isP2pServer
}

fun ServerListFilter.isMatching(server: Server) =
    type.isMatching(server) &&
        (country == null || country.countryCode == server.exitCountry) &&
        (cityStateId == null || cityStateId.matches(server))