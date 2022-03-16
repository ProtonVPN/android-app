/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.android.ui.home.countries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.protonvpn.android.R
import com.protonvpn.android.api.NetworkLoader
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.AndroidUtils.whenNotNullNorEmpty
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CountryListViewModel @Inject constructor(
    val serverManager: ServerManager,
    val serverListUpdater: ServerListUpdater,
    val vpnStateMonitor: VpnStateMonitor,
    val userData: UserData,
    val currentUser: CurrentUser,
    val api: ProtonApiRetroFit
) : ViewModel() {

    val vpnStatus = vpnStateMonitor.status.asLiveData()
    val isFreeUser get() = currentUser.vpnUserCached()?.isFreeUser == true

    fun refreshServerList(networkLoader: NetworkLoader) {
        serverListUpdater.getServersList(networkLoader)
    }

    data class ServersGroup(val titleRes: Int?, val servers: List<Server>, val infoKey: String? = null)
    fun getMappedServersForCountry(country: VpnCountry): List<ServersGroup> {
        return if (userData.secureCoreEnabled) {
            listOf(ServersGroup(null, country.connectableServers))
        } else {
            getMappedServersForClassicView(country)
        }
    }

    private fun getMappedServersForClassicView(country: VpnCountry): List<ServersGroup> {
        val countryServers = country.connectableServers
            .sortedBy { it.displayCity }
            .sortedBy { it.displayCity == null } // null cities go to the end of the list
        val freeServers = countryServers.filter { it.isFreeServer }
        val basicServers = countryServers.filter { it.isBasicServer }
        val plusServers = countryServers.filter { it.isPlusServer }
        val internalServers = countryServers.filter { it.isPMTeamServer }
        val fastestServer = serverManager.getBestScoreServer(countryServers)?.copy()

        val groups: MutableList<ServersGroup> = mutableListOf()
        if (internalServers.isNotEmpty()) {
            groups.add(ServersGroup(R.string.listInternalServers, internalServers))
        }
        fastestServer?.let {
            groups.add(ServersGroup(R.string.listFastestServer, listOf(fastestServer)))
        }
        val infoKey = if (serverManager.streamingServicesModel?.getForAllTiers(country.flag)?.isNotEmpty() == true)
            country.flag else null
        if (currentUser.vpnUserCached()?.isFreeUser == true) {
            freeServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listFreeServers, freeServers)) }
            basicServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listBasicServers, basicServers)) }
            plusServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listPlusServers, plusServers, infoKey)) }
        }
        if (currentUser.vpnUserCached()?.isBasicUser == true) {
            basicServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listBasicServers, basicServers)) }
            freeServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listFreeServers, freeServers)) }
            plusServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listPlusServers, plusServers, infoKey)) }
        }
        if (currentUser.vpnUserCached()?.isUserPlusOrAbove == true) {
            plusServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listPlusServers, plusServers, infoKey)) }
            basicServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listBasicServers, basicServers)) }
            freeServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listFreeServers, freeServers)) }
        }
        return groups
    }

    fun getCountriesForList(): List<VpnCountry> =
        if (userData.secureCoreEnabled)
            serverManager.getSecureCoreExitCountries()
        else
            serverManager.getVpnCountries()

    fun getFreeAndPremiumCountries(): Pair<List<VpnCountry>, List<VpnCountry>> =
        getCountriesForList().partition { it.hasAccessibleServer(currentUser.vpnUserCached()) }

    fun hasAccessToServer(server: Server) =
        currentUser.vpnUserCached().hasAccessToServer(server)

    fun hasAccessibleOnlineServer(country: VpnCountry) =
        country.hasAccessibleOnlineServer(currentUser.vpnUserCached())
}
