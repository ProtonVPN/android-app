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
import com.protonvpn.android.api.NetworkLoader
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.LiveEvent
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnStateMonitor
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

    fun getCountriesForList(): List<VpnCountry> {
        return if (userData.isSecureCoreEnabled) serverManager.secureCoreExitCountries else serverManager.vpnCountries
    }
}
