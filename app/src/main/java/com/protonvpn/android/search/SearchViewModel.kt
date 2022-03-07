/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.android.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.data.hasAccessToAnyServer
import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userData: UserData,
    private val vpnConnectionManager: VpnConnectionManager,
    vpnStateMonitor: VpnStateMonitor,
    private val serverManager: ServerManager,
    private val search: Search,
    currentUser: CurrentUser
) : ViewModel() {

    data class ResultItem<T>(
        val match: Search.Match<T>,
        val isConnected: Boolean,
        val hasAccess: Boolean,
        val isOnline: Boolean
    )
    sealed class ViewState {
        object Empty : ViewState()
        class SearchHistory(val queries: List<String>) : ViewState()
        class SearchResults(
            val query: String,
            val countries: List<ResultItem<VpnCountry>>,
            val cities: List<ResultItem<List<Server>>>,
            val servers: List<ResultItem<Server>>
        ) : ViewState()
        class ScSearchResults(
            val query: String,
            val servers: List<ResultItem<Server>>
        ) : ViewState()
    }

    private val collator = Collator.getInstance(Locale.getDefault())
    private val comparator: Comparator<Search.Match<*>> =
        compareBy<Search.Match<*>> { it.index != 0 }
            .thenBy(collator, Search.Match<*>::text)

    private val query = savedStateHandle.getLiveData("search_query", "")

    val viewState: Flow<ViewState> =
        combine(query.asFlow(), currentUser.vpnUserFlow, vpnStateMonitor.status) { query, vpnUser, vpnStatus ->
            val isConnectedOrConnecting =
                vpnStatus.state.isEstablishingConnection || vpnStatus.state == VpnState.Connected
            mapState(query, vpnUser, vpnStatus.server?.takeIf { isConnectedOrConnecting })
        }
    private val eventCloseFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val eventCloseLiveData = eventCloseFlow.asLiveData() // Expose flow once HomeActivity is converted to kotlin.

    fun setQuery(newQuery: String) {
        query.value = newQuery
    }

    fun disconnect() {
        vpnConnectionManager.disconnect("Search UI")
    }

    fun connectCountry(vpnUiDelegate: VpnUiDelegate, item: ResultItem<VpnCountry>) =
        connectServer(vpnUiDelegate, serverManager.getBestScoreServer(item.match.value))

    fun connectCity(vpnUiDelegate: VpnUiDelegate, item: ResultItem<List<Server>>) =
        connectServer(vpnUiDelegate, serverManager.getBestScoreServer(item.match.value))

    fun connectServer(vpnUiDelegate: VpnUiDelegate, item: ResultItem<Server>) =
        connectServer(vpnUiDelegate, item.match.value)

    private fun connectServer(vpnUiDelegate: VpnUiDelegate, server: Server?) {
        if (server != null) {
            vpnConnectionManager.connect(vpnUiDelegate, Profile.getTempProfile(server, serverManager), "Search UI")
            eventCloseFlow.tryEmit(Unit)
        } else {
            DebugUtils.debugAssert("No server found, should never happen") { true }
        }
    }

    private fun mapState(query: String, vpnUser: VpnUser?, connectedServer: Server?): ViewState {
        val secureCore = userData.secureCoreEnabled
        val result = search(query, secureCore)
        return if (result.isEmpty) {
            // TODO: add search history
            ViewState.Empty
        } else {
            with(result) {
                if (!secureCore) {
                    ViewState.SearchResults(
                        query,
                        countries.sortedWith(comparator).map { mapCountry(it, vpnUser, connectedServer) },
                        cities.sortedWith(comparator).map { mapCity(it, vpnUser, connectedServer) },
                        servers.map {
                            ResultItem(it, it.value == connectedServer, vpnUser.hasAccessToServer(it.value), it.value.online)
                        }
                    )
                } else {
                    ViewState.ScSearchResults(
                        query,
                        countries.flatMap { countryMatch ->
                            countryMatch.value.serverList.map {
                                ResultItem(
                                    Search.Match(countryMatch.textMatch, it),
                                    it == connectedServer,
                                    vpnUser.hasAccessToServer(it),
                                    it.online
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    private fun mapCountry(match: Search.Match<VpnCountry>, vpnUser: VpnUser?, connectedServer: Server?) =
        with(match.value) {
            ResultItem(match, serverList.contains(connectedServer), hasAccessibleServer(vpnUser), !isUnderMaintenance())
        }

    private fun mapCity(match: Search.Match<List<Server>>, vpnUser: VpnUser?, connectedServer: Server?) =
        match.value.let { servers ->
            ResultItem(
                match,
                servers.contains(connectedServer),
                vpnUser.hasAccessToAnyServer(servers),
                servers.any { it.online }
            )
        }
}
