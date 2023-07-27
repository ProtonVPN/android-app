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
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.vpn.GatewayGroup
import com.protonvpn.android.models.vpn.Partner
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.ServerGroup
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.partnerships.PartnershipsRepository
import com.protonvpn.android.ui.home.InformationActivity
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.AndroidUtils.whenNotNullNorEmpty
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CountryListViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val partnershipsRepository: PartnershipsRepository,
    private val serverListUpdater: ServerListUpdater,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val userData: UserData,
    private val currentUser: CurrentUser
) : ViewModel() {

    val userDataUpdateEvent = userData.updateEvent
    val serverListVersion = serverManager.serverListVersion
    val vpnStatus = vpnStatusProviderUI.status.asLiveData()
    val isFreeUser get() = currentUser.vpnUserCached()?.isFreeUser == true
    val isSecureCoreEnabled get() = userData.secureCoreEnabled

    fun refreshServerList(networkLoader: NetworkLoader) {
        serverListUpdater.getServersList(networkLoader)
    }

    fun isConnectedToServer(server: Server): Boolean = vpnStatusProviderUI.isConnectedTo(server)

    fun getServerPartnerships(server: Server): List<Partner> = partnershipsRepository.getServerPartnerships(server)

    data class ServersGroup(val groupTitle: ServerGroupTitle?, val servers: List<Server>) {
        constructor(titleRes: Int, servers: List<Server>, infoType: InformationActivity.InfoType? = null) : this(
            ServerGroupTitle(titleRes, infoType), servers
        )
    }

    data class ServerGroupTitle(val titleRes: Int, val infoType: InformationActivity.InfoType?)

    fun getMappedServersForGroup(group: ServerGroup): List<ServersGroup> {
        return if (userData.secureCoreEnabled) {
            listOf(ServersGroup(null, group.serverList))
        } else {
            getMappedServersForClassicView(group)
        }
    }

    private fun getMappedServersForClassicView(group: ServerGroup): List<ServersGroup> {
        val countryServers = group.serverList.sortedForUi()
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

        val freeServersInfo =
            if (group is VpnCountry && partnershipsRepository.hasAnyPartnership(group))
                InformationActivity.InfoType.Partners.Country(group.flag, userData.secureCoreEnabled)
            else
                null

        val plusServersInfo =
            if (group is VpnCountry && serverManager.streamingServicesModel?.getForAllTiers(group.flag)?.isNotEmpty() == true)
                InformationActivity.InfoType.Streaming(group.flag)
            else
                null

        if (currentUser.vpnUserCached()?.isFreeUser == true) {
            freeServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listFreeServers, freeServers, freeServersInfo)) }
            plusServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listPlusServers, plusServers, plusServersInfo)) }
            basicServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listBasicServers, basicServers)) }
        }
        if (currentUser.vpnUserCached()?.isBasicUser == true) {
            basicServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listBasicServers, basicServers)) }
            freeServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listFreeServers, freeServers, freeServersInfo)) }
            plusServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listPlusServers, plusServers, plusServersInfo)) }
        }
        if (currentUser.vpnUserCached()?.isUserPlusOrAbove == true) {
            plusServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listPlusServers, plusServers, plusServersInfo)) }
            basicServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listBasicServers, basicServers)) }
            freeServers.whenNotNullNorEmpty { groups.add(ServersGroup(R.string.listFreeServers, freeServers, freeServersInfo)) }
        }
        return groups
    }

    fun getCountriesForList(): List<VpnCountry> =
        if (userData.secureCoreEnabled)
            serverManager.getSecureCoreExitCountries()
        else
            serverManager.getVpnCountries()

    fun getGatewayGroupsForList(): List<GatewayGroup> =
        if (userData.secureCoreEnabled)
            emptyList()
        else
            serverManager.getGateways()

    fun getFreeAndPremiumCountries(): Pair<List<VpnCountry>, List<VpnCountry>> =
        getCountriesForList().partition { it.hasAccessibleServer(currentUser.vpnUserCached()) }

    fun hasAccessToServer(server: Server) =
        currentUser.vpnUserCached().hasAccessToServer(server)

    fun hasAccessibleServer(group: ServerGroup) =
        group.hasAccessibleServer(currentUser.vpnUserCached())

    fun hasAccessibleOnlineServer(group: ServerGroup) =
        group.hasAccessibleOnlineServer(currentUser.vpnUserCached())

    private fun List<Server>.sortedForUi() =
        this.sortedBy { it.displayCity }
            .sortedBy { it.displayCity == null } // null cities go to the end of the list
            .sortedBy { it.isPartneshipServer } // partnership servers go to the end of the list


}
