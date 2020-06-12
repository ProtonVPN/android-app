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

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.protonvpn.android.R
import com.protonvpn.android.api.NetworkLoader
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.AndroidUtils.whenNotNullNorEmpty
import com.protonvpn.android.utils.LiveEvent
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

    val selectedServer = MutableLiveData<Server>()
    val selectedCountryFlag = MutableLiveData<String>()
    val onUpgradeTriggered = LiveEvent()

    fun refreshServerList(networkLoader: NetworkLoader) {
        serverListUpdater.getServersList(networkLoader)
    }

    fun getMappedServersForCountry(country: VpnCountry): MutableMap<Int?, List<Server>> {
        return if (userData.isSecureCoreEnabled) {
            mutableMapOf(null to country.connectableServers)
        } else {
            getMappedServersForClassicView(country)
        }
    }

    private fun getMappedServersForClassicView(country: VpnCountry): MutableMap<Int?, List<Server>> {
        val freeServers = country.connectableServers.filter { it.isFreeServer }
        val basicServers = country.connectableServers.filter { it.isBasicServer }
        val plusServers = country.connectableServers.filter { it.isPlusServer }
        val internalServers = country.connectableServers.filter { it.isPMTeamServer }
        val fastestServer =
            SerializationUtils.clone(serverManager.getBestScoreServer(country.connectableServers))

        val map: MutableMap<Int?, List<Server>> = mutableMapOf()
        if (internalServers.isNotEmpty()) {
            map[R.string.listInternalServers] = internalServers
        }
        fastestServer?.let {
            map[R.string.listFastestServer] = listOf(fastestServer)
        }
        if (userData.isFreeUser) {
            freeServers.whenNotNullNorEmpty { map[R.string.listFreeServers] = freeServers }
            basicServers.whenNotNullNorEmpty { map[R.string.listBasicServers] = basicServers }
            plusServers.whenNotNullNorEmpty { map[R.string.listPlusServers] = plusServers }
        }
        if (userData.isBasicUser) {
            basicServers.whenNotNullNorEmpty { map[R.string.listBasicServers] = basicServers }
            freeServers.whenNotNullNorEmpty { map[R.string.listFreeServers] = freeServers }
            plusServers.whenNotNullNorEmpty { map[R.string.listPlusServers] = plusServers }
        }
        if (userData.isUserPlusOrAbove) {
            plusServers.whenNotNullNorEmpty { map[R.string.listPlusServers] = plusServers }
            basicServers.whenNotNullNorEmpty { map[R.string.listBasicServers] = basicServers }
            freeServers.whenNotNullNorEmpty { map[R.string.listFreeServers] = freeServers }
        }
        return map
    }

    fun getCountriesForList(): List<VpnCountry> =
        if (userData.isSecureCoreEnabled)
            serverManager.getSecureCoreExitCountries()
        else
            serverManager.getVpnCountries()
}
