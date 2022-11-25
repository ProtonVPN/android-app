/*
 * Copyright (c) 2021 Proton Technologies AG
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

package com.protonvpn.android.tv.detailed

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UiConnect
import com.protonvpn.android.logging.UiDisconnect
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.RecentsManager
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class TvServerListViewModel @Inject constructor(
    private val planManager: UserPlanManager,
    val serverManager: ServerManager,
    val vpnStatusProviderUI: VpnStatusProviderUI,
    val vpnConnectionManager: VpnConnectionManager,
    val currentUser: CurrentUser,
    private val recentsManager: RecentsManager
) : ViewModel() {

    val servers = MutableLiveData<ServersViewModel>()
    val recents = MutableLiveData<List<ServerViewModel>>()

    fun init(country: String) {
        viewModelScope.launch {
            populateServerList(country)
            planManager.planChangeFlow.collect {
                populateServerList(country)
            }
        }
        viewModelScope.launch {
            recentsManager.update.collect {
                updateRecents(country)
            }
        }
    }

    private fun updateRecents(country: String) {
        recents.value = getRecents(country) ?: emptyList()
    }

    private fun getRecents(country: String) =
        recentsManager.getRecentServers(country)?.map(::ServerViewModel)

    private suspend fun populateServerList(country: String) {
        val vpnCountry = serverManager.getVpnExitCountry(country, false) ?: return

        val vpnUser = currentUser.vpnUser()
        val serversVM = linkedMapOf<ServerGroup, List<ServerViewModel>>()
        if (vpnUser?.isUserPlusOrAbove == true) {
            val cities = vpnCountry.serverList.groupBy { it.displayCity }
                .toSortedMap(Comparator { o1, o2 ->
                    // Put servers without city at the end
                    if (o1 == null || o2 == null)
                        compareValues(o2, o1) else compareValues(o1, o2)
                })
            cities.forEach { (city, servers) ->
                val group = city?.let { ServerGroup.City(it) } ?: if (cities.size > 1)
                    ServerGroup.Other else ServerGroup.Available
                serversVM[group] = servers.sortedByDescending { it.isPlusServer }.map { ServerViewModel(it) }
            }
        } else {
            val groups = vpnCountry.serverList.groupBy {
                vpnUser.hasAccessToServer(it)
            }.mapKeys { (available, _) ->
                if (available) ServerGroup.Available else ServerGroup.Locked
            }.mapValues { (_, list) ->
                list.map { ServerViewModel(it) }
            }
            serversVM.putAll(groups)
        }
        updateRecents(country)
        servers.value = ServersViewModel(country, serversVM)
    }

    sealed class ServerGroup {
        object Recents : ServerGroup()
        object Available : ServerGroup()
        object Locked : ServerGroup()
        object Other : ServerGroup()
        data class City(val name: String) : ServerGroup()
    }

    class ServersViewModel(
        val country: String,
        val servers: Map<ServerGroup, List<ServerViewModel>>
    )

    inner class ServerViewModel(
        private val server: Server
    ) {
        val name get() = server.serverName
        val locked get() = !serverManager.hasAccessToServer(server)
        val load get() = server.load
        val online get() = server.online

        val actionStateObservable = vpnStatusProviderUI.status.map {
            actionState
        }.asLiveData()

        private val actionState get() = when {
            locked ->
                ServerActionState.UPGRADE
            !server.online ->
                ServerActionState.UNAVAILABLE
            vpnStatusProviderUI.connectingToServer?.serverName != server.serverName ->
                ServerActionState.DISCONNECTED
            vpnStatusProviderUI.isConnected ->
                ServerActionState.CONNECTED
            else ->
                ServerActionState.CONNECTING
        }

        fun planDrawable(context: Context) = if (server.isPlusServer)
            ContextCompat.getDrawable(context, R.drawable.ic_plus_label) else null

        fun stateText(context: Context) = if (!online)
            context.getString(R.string.listItemMaintenance)
        else
            context.getString(R.string.tv_server_list_load, server.load.roundToInt().toString())

        fun click(vpnUiDelegate: VpnUiDelegate, onUpgrade: () -> Unit) = when (actionState) {
            ServerActionState.DISCONNECTED -> {
                val profile = Profile.getTempProfile(server)
                ProtonLogger.log(UiConnect, "server tile (TV)")
                vpnConnectionManager.connect(vpnUiDelegate, profile, "user via server tile (TV)")
            }
            ServerActionState.CONNECTING, ServerActionState.CONNECTED -> {
                ProtonLogger.log(UiDisconnect, "server tile (TV)")
                vpnConnectionManager.disconnect("user via server tile (TV)")
            }
            ServerActionState.UPGRADE ->
                onUpgrade()
            ServerActionState.UNAVAILABLE -> {}
        }
    }

    enum class ServerActionState {
        DISCONNECTED, CONNECTING, CONNECTED, UPGRADE, UNAVAILABLE
    }
}
