/*
 * Copyright (c) 2023 Proton AG
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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.protonvpn.android.redesign.countries.ui

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.home_screen.ui.ShowcaseRecents
import com.protonvpn.android.redesign.main_screen.ui.ShouldShowcaseRecents
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.sortedByLocaleAware
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import me.proton.core.presentation.savedstate.state
import java.util.Locale
import javax.inject.Inject

private const val CountryScreenStateKey = "country_screen_state"
private const val CountrySubScreenStateKey = "country_sub_screen_state"

@HiltViewModel
class CountryListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dataAdapter: CountryListViewModelDataAdapter,
    private val vpnConnectionManager: VpnConnectionManager,
    private val shouldShowcaseRecents: ShouldShowcaseRecents,
    currentUser: CurrentUser,
    vpnStatus: VpnStatusProviderUI,
) : ViewModel() {

    private var mainSaveState by savedStateHandle.state<CountryScreenSavedState>(CountryScreenSavedState(ServerFilterType.All), CountryScreenStateKey)
    private val mainSaveStateFlow = savedStateHandle.getStateFlow<CountryScreenSavedState?>(CountryScreenStateKey, mainSaveState)

    private var subScreenSaveState by savedStateHandle.state<SubScreenSaveState?>(null, CountrySubScreenStateKey)
    private val subScreenSaveStateFlow = savedStateHandle.getStateFlow(CountrySubScreenStateKey, subScreenSaveState)

    // Helper flows
    val localeFlow = MutableStateFlow<Locale?>(null)
    private val userTierFlow = currentUser.vpnUserFlow.map { it?.maxTier }
    private val connectedServerFlow =
        vpnStatus.uiStatus
            .map { status -> status.server.takeIf { status.state == VpnState.Connected } }
            .distinctUntilChanged()

    // Screen states
    val stateFlow = mainSaveStateFlow
        .filterNotNull()
        .flatMapLatest { savedState ->
            dataAdapter
                .countries(ServerListFilter(type = savedState.serverType))
                .toItemState()
                .map { items ->
                    CountryScreenState(
                        savedState = savedState,
                        items = items,
                        filterButtons = getFilterButtons(selectedFilter = savedState.serverType) {
                            mainSaveState = CountryScreenSavedState(it)
                        }
                    )
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val subScreenStateFlow = subScreenSaveStateFlow.flatMapLatest { savedState ->
        if (savedState != null) {
            when (savedState.type) {
                SubScreenType.City -> dataAdapter.cities(savedState.filter).toItemState().map { items ->
                    SubScreenState(savedState, items)
                }
                SubScreenType.Server -> dataAdapter.servers(savedState.filter).toItemState().map { items ->
                    SubScreenState(savedState, items)
                }
            }
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private fun Flow<List<CountryListItemData>>.toItemState(): Flow<List<CountryListItemState>> =
        combine(
            this,
            userTierFlow,
            connectedServerFlow,
            localeFlow.filterNotNull(),
        ) { items, userTier, connectedServer, locale ->
            items.map { data ->
                CountryListItemState(
                    data = data,
                    available = userTier == null || userTier >= data.tier,
                    connected = connectedServer != null && dataAdapter.includesServer(data, connectedServer),
                    label = data.displayLabel(locale)
                )
            }.sortedByLocaleAware { it.label }
        }

    private fun onOpenCountry(countryId: CountryId) {
        subScreenSaveState = SubScreenSaveState(
            countryId = countryId,
            type = SubScreenType.City,
            filter = ServerListFilter(country = countryId),
            previousScreen = null,
            rememberStateKey = "country_view"
        )
    }

    private fun onOpenCity(countryId: CountryId, cityStateId: CityStateId) {
        subScreenSaveState = SubScreenSaveState(
            countryId = countryId,
            type = SubScreenType.Server,
            filter = ServerListFilter(
                country = countryId,
                cityStateId = cityStateId
            ),
            previousScreen = subScreenSaveState,
            rememberStateKey = "servers_view"
        )
    }

    fun onItemOpen(item: CountryListItemState) {
        when (item.data) {
            is CountryListItemData.Country -> onOpenCountry(item.data.countryId)
            is CountryListItemData.City -> onOpenCity(item.data.countryId, item.data.cityStateId)
            is CountryListItemData.Server -> {} // shouldn't happen
        }
    }

    fun onItemConnect(
        vpnUiDelegate: VpnUiDelegate,
        item: CountryListItemState,
        navigateToHome: (ShowcaseRecents) -> Unit,
        navigateToUpsell: () -> Unit,
    ) {
        viewModelScope.launch {
            subScreenSaveState = null
            if (!item.data.inMaintenance) {
                if (item.available) {
                    val features = emptySet<ServerFeature>() //TODO:
                    val intent = item.getConnectionIntent(features)
                    val trigger = ConnectTrigger.Server("")
                    if (!item.connected)
                        vpnConnectionManager.connect(vpnUiDelegate, intent, trigger)
                    navigateToHome(shouldShowcaseRecents(intent))
                } else {
                    navigateToUpsell()
                }
            }
        }
    }

    suspend fun onNavigateBack(onHide: suspend () -> Unit) {
        subScreenSaveState?.let { current ->
            subScreenSaveState = if (current.previousScreen != null) {
                current.previousScreen
            } else {
                onHide()
                null
            }
        }
    }

    fun onClose() {
        subScreenSaveState = null
    }

    private fun getFilterButtons(selectedFilter: ServerFilterType, onItemSelect: (ServerFilterType) -> Unit): List<FilterButton> =
        ServerFilterType.entries.map { filter ->
            FilterButton(
                filter = filter,
                isSelected = filter == selectedFilter,
                onClick = { onItemSelect(filter) }
            )
        }
}

@Parcelize
data class FilterButton(
    val filter: ServerFilterType,
    val isSelected: Boolean = false,
    val onClick: () -> Unit
) : Parcelable

val CountryListItemState.canOpen: Boolean get() = when(data) {
    is CountryListItemData.Server -> false
    is CountryListItemData.City,
    is CountryListItemData.Country -> !data.inMaintenance
}

private fun CountryListItemData.displayLabel(locale: Locale): String = when(this) {
    is CountryListItemData.Country -> countryId.toDisplayName(locale)
    is CountryListItemData.City -> name
    is CountryListItemData.Server -> name
}

private fun CountryListItemState.getConnectionIntent(features: Set<ServerFeature>): ConnectIntent = when(data) {
    is CountryListItemData.Server ->
        ConnectIntent.Server(data.serverId.id, features)
    is CountryListItemData.City ->
        ConnectIntent.FastestInCity(data.countryId, data.cityStateId.name, features)
    is CountryListItemData.Country ->
        ConnectIntent.FastestInCountry(data.countryId, features)
}

private fun CountryId.toDisplayName(locale: Locale) =
    CountryTools.getFullName(locale, countryCode)

enum class ServerFilterType {
    All, SecureCore, P2P, Tor;
}

@Parcelize
data class ServerListFilter(
    val country: CountryId? = null,
    val cityStateId: CityStateId? = null,
    val type: ServerFilterType = ServerFilterType.All
): Parcelable

enum class SubScreenType { City, Server }

@Parcelize
data class CountryScreenSavedState(
    val serverType: ServerFilterType,
): Parcelable

data class CountryScreenState(
    val savedState: CountryScreenSavedState,
    val filterButtons: List<FilterButton>,
    val items: List<CountryListItemState>,
)

@Parcelize
data class SubScreenSaveState(
    val countryId: CountryId,
    val type: SubScreenType,
    val filter: ServerListFilter,
    val previousScreen: SubScreenSaveState?,
    val rememberStateKey: String
): Parcelable

data class SubScreenState(
    val savedState: SubScreenSaveState,
    val items: List<CountryListItemState>,
)

// Adapter separating server data storage from view model.
interface CountryListViewModelDataAdapter {

    fun countries(filter: ServerListFilter):
        Flow<List<CountryListItemData.Country>>

    fun cities(filter: ServerListFilter):
        Flow<List<CountryListItemData.City>>

    fun servers(filter: ServerListFilter):
        Flow<List<CountryListItemData.Server>>

    fun includesServer(data: CountryListItemData, server: Server): Boolean
}

