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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
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
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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
        object EmptyResult : ViewState()
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
    val currentQuery = query.value

    private var recentsAddJob: Job? = null
    private val _queryFromRecents = MutableLiveData<String>()
    val queryFromRecents: LiveData<String> = _queryFromRecents

    val viewState: Flow<ViewState> =
        combine(query.asFlow(), currentUser.vpnUserFlow, vpnStateMonitor.status) { query, vpnUser, vpnStatus ->
            val isConnectedOrConnecting =
                vpnStatus.state.isEstablishingConnection || vpnStatus.state == VpnState.Connected
            mapState(query, vpnUser, vpnStatus.server?.takeIf { isConnectedOrConnecting })
        }
    private val eventCloseFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val eventCloseLiveData = eventCloseFlow.asLiveData() // Expose flow once HomeActivity is converted to kotlin.

    val secureCore get() = userData.secureCoreEnabled

    fun setQuery(newQuery: String) {
        query.value = newQuery
        recentsAddJob?.cancel()
        recentsAddJob = viewModelScope.launch {
            delay(ADD_TO_RECENTS_DELAY_MS)
            saveSearchQuery(newQuery)
        }
    }

    fun setQueryFromRecents(recentQuery: String) {
        _queryFromRecents.value = recentQuery
    }

    fun clearRecentHistory() {
        // Save empty set and set whitespace query to trigger state refresh
        Storage.saveStringSet(PREF_SEARCH_RECENT_SET, setOf())
        setQuery("")
    }

    private fun getSearchRecents() = Storage.getStringSet(PREF_SEARCH_RECENT_SET).toSortedSet()

    private fun saveSearchQuery(query: String) {
        if (query.isBlank()) return
        val searchHistory = getSearchRecents()
        searchHistory.add(query)
        Storage.saveStringSet(PREF_SEARCH_RECENT_SET, searchHistory)
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
        val result = search(query, secureCore)
        return when {
            query.isBlank() -> {
                val searchHistory = getSearchRecents().toList()
                if (searchHistory.isEmpty())
                    ViewState.Empty
                else
                    ViewState.SearchHistory(searchHistory)
            }
            result.isEmpty -> {
                ViewState.EmptyResult
            }
            else -> {
                with(result) {
                    if (!secureCore) {
                        ViewState.SearchResults(
                            query,
                            countries.sortedWith(comparator).map { mapCountry(it, vpnUser, connectedServer) },
                            cities.sortedWith(comparator).map { mapCity(it, vpnUser, connectedServer) },
                            servers.map {
                                ResultItem(it, it.value == connectedServer, vpnUser.hasAccessToServer(it.value), it.value.online)
                            }.sortedByDescending { it.hasAccess }
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

    companion object {
        private const val ADD_TO_RECENTS_DELAY_MS = 3000L
        private const val PREF_SEARCH_RECENT_SET = "PREF_DONT_SHOW_CLEAR_HISTORY"
    }
}
