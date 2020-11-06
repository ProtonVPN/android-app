/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.ui.home

import android.app.Activity
import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.tv.main.TvMapRenderer
import com.protonvpn.android.tv.models.CountryCard
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import javax.inject.Inject

class TvHomeViewModel @Inject constructor(
    val serverManager: ServerManager,
    val serverListUpdater: ServerListUpdater,
    val vpnStateMonitor: VpnStateMonitor,
    val userData: UserData
) : ViewModel() {

    val selectedCountry = MutableLiveData<VpnCountry>()
    val connectedCountryFlag = MutableLiveData<String>()
    val mapRegion = MutableLiveData<TvMapRenderer.MapRegion>()
    val vpnStatus = vpnStateMonitor.vpnStatus.map { it.state }

    init {
        vpnStateMonitor.vpnStatus.observeForever {
            if (it.state == VpnState.Connected) {
                connectedCountryFlag.value = it.server!!.flag
            } else {
                connectedCountryFlag.value = ""
            }
        }
        serverListUpdater.getServersList(null)
    }

    val haveAccessToStreaming get() = userData.isUserPlusOrAbove

    fun isConnectedToCountry(card: CountryCard) =
        vpnStateMonitor.isConnectedToAny(card.vpnCountry.connectableServers)

    fun disconnect() = vpnStateMonitor.disconnect()

    fun isConnected() = vpnStateMonitor.isConnected

    fun quickConnectBackground(context: Context): Int {
        val server =
            if (isConnected()) vpnStateMonitor.connectingToServer else serverManager.defaultConnection.server
        return CountryTools.getFlagResource(context, server?.flag)
    }

    fun onQuickConnectAction(activity: Activity) {
        if (vpnStateMonitor.isConnected) {
            vpnStateMonitor.disconnect()
        } else {
            vpnStateMonitor.connect(activity, serverManager.defaultConnection)
        }
    }

    fun connect(activity: Activity, card: CountryCard?) {
        val profile = if (card != null) Profile.getTempProfile(
            serverManager.getBestScoreServer(card.vpnCountry), serverManager
        )
        else serverManager.defaultConnection
        vpnStateMonitor.connect(activity, profile)
    }

    fun resetMap() {
        mapRegion.value = TvMapRenderer.MapRegion(0f, 0f, 1f)
    }

    fun isDefaultCountry(vpnCountry: VpnCountry) =
        userData.defaultConnection?.wrapper?.country == vpnCountry.flag

    fun setAsDefaultCountry(checked: Boolean, vpnCountry: VpnCountry) {
        userData.defaultConnection = if (checked) Profile(
            vpnCountry.countryName, "", ServerWrapper.makeFastestForCountry(vpnCountry.flag, serverManager)
        ) else null
    }
}
