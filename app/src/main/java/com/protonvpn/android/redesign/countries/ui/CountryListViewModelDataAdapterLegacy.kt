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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.EnumSet
import javax.inject.Inject
import kotlin.math.roundToInt

class CountryListViewModelDataAdapterLegacy @Inject constructor(
    private val serverManager2: ServerManager2,
) : CountryListViewModelDataAdapter {

    override suspend fun availableTypesFor(country: CountryId?): Set<ServerFilterType> {
        val servers = serverManager2.allServersFlow.first()
        val availableTypes = initAvailableTypes()
        for (server in servers) {
            if (country == null || server.exitCountry == country.countryCode)
                availableTypes.update(server)
        }
        return availableTypes
    }

    override fun countries(
        filter: ServerListFilter,
    ): Flow<List<CountryListItemData.Country>> =
        serverManager2.allServersFlow.map { servers ->
            val secureCore = filter.type == ServerFilterType.SecureCore
            val entryCountryId = if (secureCore)
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
                    .getVpnExitCountry(countryCode, secureCore)
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
            val availableTypes = initAvailableTypes()
            filteredServers
                .groupBy(groupBySelector)
                .mapNotNull { (cityOrRegion, servers) ->
                    availableTypes.update(servers)
                    toCityItem(hasRegions, cityOrRegion, servers)
                }
        }

    override fun servers(
        filter: ServerListFilter,
    ): Flow<List<CountryListItemData.Server>> =
        serverManager2.allServersFlow.map { servers ->
            val availableTypes = initAvailableTypes()
            servers
                .asFilteredSequence(filter)
                .onEach { availableTypes.update(it) }
                .map(Server::toServerItem)
                .toList()
        }

    override fun entryCountries(country: CountryId): Flow<List<CountryListItemData.Country>> =
        serverManager2.allServersFlow.map { _ ->
            val servers = serverManager2.getVpnExitCountry(country.countryCode, true)?.serverList
            if (servers == null)
                emptyList()
            else {
                val entryCountries = servers.groupBy { it.entryCountry }
                entryCountries.map { (entryCode, servers) ->
                    servers.toCountryItem(country.countryCode, CountryId(entryCode))
                }
            }
        }

    override suspend fun haveStates(filter: ServerListFilter): Boolean =
        serverManager2.allServersFlow.first().asFilteredSequence(filter).any { it.region != null }

    override fun gateways(filter: ServerListFilter): Flow<List<CountryListItemData.Gateway>> =
        serverManager2.allServersFlow.map { servers ->
            val gateways = servers.asFilteredSequence(filter, forceIncludeGateways = true).groupBy { it.gatewayName }
            gateways.mapNotNull { (gatewayName, servers) ->
                if (gatewayName == null)
                    null
                else CountryListItemData.Gateway(
                    gatewayName = gatewayName,
                    inMaintenance = servers.all { !it.online },
                    tier = servers.minOf { it.tier }
                )
            }
        }

    private fun List<Server>.asFilteredSequence(filter: ServerListFilter, forceIncludeGateways: Boolean = false) =
        asSequence().filter { filter.isMatching(it, forceIncludeGateways) }
}

private fun List<Server>.toCountryItem(countryCode: String, entryCountryId: CountryId?) = CountryListItemData.Country(
    countryId = CountryId(countryCode),
    entryCountryId = entryCountryId,
    inMaintenance = all { !it.online },
    tier = minOf { it.tier }
)

private fun Server.toServerItem() = CountryListItemData.Server(
    countryId = CountryId(exitCountry),
    serverId = ServerId(serverId),
    name = serverName,
    loadPercent = load.roundToInt(),
    serverFeatures = serverFeatures,
    isVirtualLocation = hostCountry != null && hostCountry != exitCountry,
    inMaintenance = !online,
    tier = tier,
    entryCountryId = if (isSecureCoreServer) CountryId(entryCountry) else null,
    gatewayName = gatewayName,
)

private fun toCityItem(isRegion: Boolean, cityOrRegion: String?, servers: List<Server>) : CountryListItemData.City? {
    if (cityOrRegion == null || servers.isEmpty())
        return null

    val server = servers.first()
    //TODO: what to do with servers without a region if hasRegions is true
    return CountryListItemData.City(
        countryId = CountryId(server.exitCountry),
        cityStateId = CityStateId(cityOrRegion, isRegion),
        name = (if (isRegion) server.displayRegion else server.displayCity) ?: cityOrRegion,
        inMaintenance = servers.all { !it.online },
        tier = servers.minOf { it.tier }
    )
}


private val Server.serverFeatures get() = buildSet {
    if (features.hasFlag(SERVER_FEATURE_P2P))
        add(ServerFeature.P2P)
    if (features.hasFlag(SERVER_FEATURE_TOR))
        add(ServerFeature.Tor)
}

private fun CityStateId.matches(server: Server) =
    name == if (isState) server.region else server.city

private fun ServerFilterType.isMatching(server: Server) = when (this) {
    ServerFilterType.All -> !server.isSecureCoreServer
    ServerFilterType.SecureCore -> server.isSecureCoreServer
    ServerFilterType.Tor -> server.isTor
    ServerFilterType.P2P -> server.isP2pServer
}

// if forceIncludeGateways == false gateways will be ignored if not set by the filter
private fun ServerListFilter.isMatching(server: Server, forceIncludeGateways: Boolean) =
    type.isMatching(server) &&
        (country == null || country.countryCode == server.exitCountry) &&
        (cityStateId == null || cityStateId.matches(server)) &&
        ((forceIncludeGateways && gatewayName == null) || gatewayName == server.gatewayName)


private fun initAvailableTypes() = EnumSet.of(ServerFilterType.All)

private fun EnumSet<ServerFilterType>.update(server: Server) {
    if (server.isSecureCoreServer) add(ServerFilterType.SecureCore)
    if (server.isTor) add(ServerFilterType.Tor)
    if (server.isP2pServer) add(ServerFilterType.P2P)
}

private fun EnumSet<ServerFilterType>.update(servers: List<Server>) = servers.forEach { update(it) }