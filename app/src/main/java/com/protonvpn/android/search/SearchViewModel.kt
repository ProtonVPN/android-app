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
import com.protonvpn.android.appconfig.RestrictionsConfig
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.data.hasAccessToAnyServer
import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.Partner
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.partnerships.PartnershipsRepository
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userSettings: EffectiveCurrentUserSettings,
    private val vpnConnectionManager: VpnConnectionManager,
    vpnStatusProviderUI: VpnStatusProviderUI,
    private val serverManager: ServerManager,
    private val parntershipsRepository: PartnershipsRepository,
    private val search: Search,
    private val restrictions: RestrictionsConfig,
    currentUser: CurrentUser
) : ViewModel() {

    data class ResultItem<T>(
        val match: Search.Match<T>,
        val isConnected: Boolean,
        val hasAccess: Boolean,
        val isOnline: Boolean,
        val partnerships: List<Partner>
    )

    sealed class ViewState {
        object Empty : ViewState()
        object EmptyResult : ViewState()
        class SearchHistory(val queries: List<String>) : ViewState()
        class SearchResults(
            val query: String,
            val countries: List<ResultItem<VpnCountry>>,
            val cities: List<ResultItem<Search.CityResult>>,
            val servers: List<ResultItem<Server>>,
            val showUpgradeBanner: Boolean
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

    val query = savedStateHandle.getLiveData("search_query", "")
    val currentQuery = query.value

    private var recentsAddJob: Job? = null
    private val _queryFromRecents = MutableLiveData<String>()
    val queryFromRecents: LiveData<String> = _queryFromRecents

    val viewState: Flow<ViewState> =
        combine(query.asFlow().distinctUntilChanged(), currentUser.vpnUserFlow, vpnStatusProviderUI.status) { query, vpnUser, vpnStatus ->
            val isConnectedOrConnecting =
                vpnStatus.state.isEstablishingConnection || vpnStatus.state == VpnState.Connected
            mapState(query, vpnUser, vpnStatus.server?.takeIf { isConnectedOrConnecting })
        }
    private val eventCloseFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val eventCloseLiveData = eventCloseFlow.asLiveData() // Expose flow once HomeActivity is converted to kotlin.

    suspend fun getSecureCore() = userSettings.effectiveSettings.first().secureCore
    val countryCount get() = serverManager.getVpnCountries().size

    fun setQuery(newQuery: String) {
        query.value = newQuery
        // Without empty value recent cannot be reselected twice in a row
        _queryFromRecents.value = ""
        recentsAddJob?.cancel()
        recentsAddJob = viewModelScope.launch {
            delay(ADD_TO_RECENTS_DELAY_MS)
            saveSearchQuery(newQuery)
        }
    }

    fun setQueryFromRecents(recentQuery: String) {
        _queryFromRecents.value = recentQuery
        saveSearchQuery(recentQuery) // Move to top.
    }

    fun clearRecentHistory() {
        // Save empty set and set whitespace query to trigger state refresh
        Storage.saveStringList(PREF_SEARCH_RECENT_LIST, listOf())
        setQuery("")
    }

    private fun getSearchRecents() = Storage.getStringList(PREF_SEARCH_RECENT_LIST)

    private fun saveSearchQuery(query: String) {
        if (query.isBlank()) return
        val searchHistory = getSearchRecents()
        val updatedHistory = (listOf(query) + (searchHistory - query)).take(RECENTS_MAX_ENTRIES)
        Storage.saveStringList(PREF_SEARCH_RECENT_LIST, updatedHistory)
    }

    fun disconnect() {
        vpnConnectionManager.disconnect(DisconnectTrigger.Search("Search UI"))
    }

    fun connectCountry(vpnUiDelegate: VpnUiDelegate, item: ResultItem<VpnCountry>) =
        connect(vpnUiDelegate, ConnectIntent.FastestInCountry(CountryId(item.match.value.flag), emptySet()))


    fun connectCity(vpnUiDelegate: VpnUiDelegate, item: ResultItem<Search.CityResult>) {
        val intent = with(item.match.value) { ConnectIntent.FastestInCity(CountryId(country), nameEn, emptySet()) }
        connect(vpnUiDelegate, intent)
    }

    fun connectServer(vpnUiDelegate: VpnUiDelegate, item: ResultItem<Server>) =
        connect(vpnUiDelegate, ConnectIntent.Server(item.match.value.serverId, emptySet()))

    private fun connect(vpnUiDelegate: VpnUiDelegate, connectIntent: ConnectIntent) {
        query.value?.let { saveSearchQuery(it) }
        vpnConnectionManager.connect(vpnUiDelegate, connectIntent, ConnectTrigger.Search("Search UI"))
        eventCloseFlow.tryEmit(Unit)
    }

    private suspend fun mapState(query: String, vpnUser: VpnUser?, connectedServer: Server?): ViewState {
        val secureCore = getSecureCore()
        val result = search(query, secureCore)
        return when {
            query.isBlank() -> {
                val searchHistory = getSearchRecents()
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
                            servers
                                .sortedWith(getServerTierComparator(vpnUser!!))
                                .map { mapServer(it, vpnUser, connectedServer) },
                            vpnUser.isFreeUser
                        )
                    } else {
                        ViewState.ScSearchResults(
                            query,
                            countries.flatMap { countryMatch ->
                                countryMatch.value.serverList.map {
                                    ResultItem(
                                        Search.Match(countryMatch.textMatch, it),
                                        it == connectedServer,
                                        vpnUser.hasAccessToServer(it) && !restrictions.restrictServerList(),
                                        it.online,
                                        parntershipsRepository.getServerPartnerships(it)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private suspend fun mapCountry(match: Search.Match<VpnCountry>, vpnUser: VpnUser?, connectedServer: Server?) =
        with(match.value) {
            ResultItem(
                match,
                serverList.contains(connectedServer),
                hasAccessibleServer(vpnUser) && !restrictions.restrictServerList(),
                !isUnderMaintenance(),
                partnerships = emptyList()
            )
        }

    private fun mapCity(match: Search.Match<Search.CityResult>, vpnUser: VpnUser?, connectedServer: Server?) =
        match.value.let { city ->
            ResultItem(
                match,
                city.servers.contains(connectedServer),
                vpnUser.hasAccessToAnyServer(city.servers),
                city.servers.any { it.online },
                partnerships = emptyList()
            )
        }

    private suspend fun mapServer(match: Search.Match<Server>, vpnUser: VpnUser?, connectedServer: Server?) =
        ResultItem(
            match,
            match.value == connectedServer,
            vpnUser.hasAccessToServer(match.value) && !restrictions.restrictServerList(),
            match.value.online,
            parntershipsRepository.getServerPartnerships(match.value)
        )

    private fun getServerTierComparator(vpnUser: VpnUser): Comparator<Search.Match<Server>> {
        val tierOrder: (tier: Int) -> Int = when {
            vpnUser.isFreeUser -> { tier ->
                when (tier) { // Order: free, plus, basic
                    0 -> 0
                    1 -> 2
                    else -> 1
                }
            }
            vpnUser.isBasicUser -> { tier ->
                when (tier) { // Order: basic, plus, free
                    1 -> 0
                    0 -> 2
                    else -> 1
                }
            }
            else -> { tier ->
                when (tier) { // Order: plus, basic, free
                    0 -> 2
                    1 -> 1
                    else -> 0
                }
            }
        }
        return compareBy { tierOrder(it.value.tier) }
    }

    companion object {
        private const val ADD_TO_RECENTS_DELAY_MS = 3000L
        private const val PREF_SEARCH_RECENT_LIST = "PREF_SEARCH_RECENT_LIST"
        private const val RECENTS_MAX_ENTRIES = 5
    }
}
