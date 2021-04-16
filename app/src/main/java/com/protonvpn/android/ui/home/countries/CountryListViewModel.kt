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
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.AndroidUtils.whenNotNullNorEmpty
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnStateMonitor
import org.apache.commons.lang3.SerializationUtils
import javax.inject.Inject

class CountryListViewModel @Inject constructor(
    val serverManager: ServerManager,
    val serverListUpdater: ServerListUpdater,
    val vpnStateMonitor: VpnStateMonitor,
    val userData: UserData,
    val api: ProtonApiRetroFit
) : ViewModel() {

    val vpnStatus = vpnStateMonitor.status.asLiveData()

    fun refreshServerList(networkLoader: NetworkLoader) {
        serverListUpdater.getServersList(networkLoader)
    }

    data class ServersGroup(val titleRes: Int?, val servers: List<Server>, val infoKey: String? = null)
    fun getMappedServersForCountry(country: VpnCountry): List<ServersGroup> {
        return if (userData.isSecureCoreEnabled) {
            listOf(ServersGroup(null, country.connectableServers))
        } else {
            getMappedServersForClassicView(country)
        }
    }

    private fun getMappedServersForClassicView(country: VpnCountry): List<ServersGroup> {
        val freeServers = country.connectableServers.filter { it.isFreeServer }
        val basicServers = country.connectableServers.filter { it.isBasicServer }
        val plusServers = country.connectableServers.filter { it.isPlusServer }
        val internalServers = country.connectableServers.filter { it.isPMTeamServer }
        val fastestServer =
            SerializationUtils.clone(serverManager.getBestScoreServer(country.connectableServers))

        val groups: MutableList<ServersGroup> = mutableListOf()
        if (internalServers.isNotEmpty()) {
            groups.add(ServersGroup(R.string.listInternalServers, internalServers))
        }
        fastestServer?.let {
            groups.add(ServersGroup(R.string.listFastestServer, listOf(fastestServer)))
        }
        val infoKey = if (serverManager.streamingServices?.countryToServices?.get(country.flag)?.isNotEmpty() == true)
            country.flag else null
        if (userData.isFreeUser) {
            freeServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listFreeServers, freeServers)) }
            basicServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listBasicServers, basicServers)) }
            plusServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listPlusServers, plusServers, infoKey)) }
        }
        if (userData.isBasicUser) {
            basicServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listBasicServers, basicServers)) }
            freeServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listFreeServers, freeServers)) }
            plusServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listPlusServers, plusServers, infoKey)) }
        }
        if (userData.isUserPlusOrAbove) {
            plusServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listPlusServers, plusServers, infoKey)) }
            basicServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listBasicServers, basicServers)) }
            freeServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listFreeServers, freeServers)) }
        }
        return groups
    }

    fun getCountriesForList(): List<VpnCountry> =
        (if (userData.isSecureCoreEnabled)
            serverManager.getSecureCoreExitCountries() else serverManager.getVpnCountries())
                .sortedBy(VpnCountry::countryName)

    fun getFreeAndPremiumCountries(): Pair<List<VpnCountry>, List<VpnCountry>> =
        getCountriesForList().partition { it.hasAccessibleServer(userData) }

    fun shouldShowSmartRouting(vpnCountry: VpnCountry) =
        vpnCountry.serverList.all { !it.hostCountry.isNullOrBlank() && it.hostCountry != it.entryCountry }
}
