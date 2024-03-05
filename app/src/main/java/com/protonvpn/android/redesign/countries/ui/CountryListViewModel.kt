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
private val defaultMainState = CountryScreenSavedState(ServerListFilter(type = ServerFilterType.All))

@HiltViewModel
class CountryListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dataAdapter: CountryListViewModelDataAdapter,
    private val vpnConnectionManager: VpnConnectionManager,
    private val shouldShowcaseRecents: ShouldShowcaseRecents,
    currentUser: CurrentUser,
    vpnStatus: VpnStatusProviderUI,
) : ViewModel() {

    private var mainSaveState by savedStateHandle.state<CountryScreenSavedState>(defaultMainState, CountryScreenStateKey)
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
            val availableTypes = dataAdapter.availableTypesFor(savedState.filter.country)
            dataAdapter
                .countries(savedState.filter)
                .toItemState()
                .map { items ->
                    CountryScreenState(
                        savedState = savedState,
                        items = items,
                        filterButtons = getFilterButtons(availableTypes, savedState.filter.type) {
                            mainSaveState = CountryScreenSavedState(ServerListFilter(type = it))
                        }
                    )
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val subScreenStateFlow = subScreenSaveStateFlow.flatMapLatest { savedState ->
        if (savedState != null) {
            val availableTypes = dataAdapter.availableTypesFor(savedState.filter.country)
            when (savedState.type) {
                SubScreenType.City -> when(savedState.filter.type) {
                    ServerFilterType.All, ServerFilterType.P2P -> dataAdapter.cities(savedState.filter)
                    ServerFilterType.SecureCore -> dataAdapter.entryCountries(savedState.countryId)
                    ServerFilterType.Tor -> dataAdapter.servers(savedState.filter)
                }
                SubScreenType.Server -> dataAdapter.servers(savedState.filter)
            }.toItemState().map { items ->
                val filterButtons = when (savedState.type) {
                    SubScreenType.City ->
                        getSubScreenFilterButtons(availableTypes, savedState.filter.type, savedState)
                    else -> null
                }
                SubScreenState(savedState, filterButtons, items)
            }
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private fun getSubScreenFilterButtons(
        availableTypes: Set<ServerFilterType>,
        selectedType: ServerFilterType,
        savedState: SubScreenSaveState
    ): List<FilterButton> =
        getFilterButtons(availableTypes, selectedType) { type ->
            val newFilter = savedState.filter.copy(type = type)
            subScreenSaveState = savedState.copy(filter = newFilter)
        }

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

    private fun onOpenCountry(type: ServerFilterType, countryId: CountryId) {
        subScreenSaveState = SubScreenSaveState(
            countryId = countryId,
            type = SubScreenType.City,
            filter = ServerListFilter(country = countryId, type = type),
            previousScreen = null,
            rememberStateKey = "country_view"
        )
    }

    private fun onOpenCity(type: ServerFilterType, countryId: CountryId, cityStateId: CityStateId) {
        subScreenSaveState = SubScreenSaveState(
            countryId = countryId,
            type = SubScreenType.Server,
            filter = ServerListFilter(
                country = countryId,
                cityStateId = cityStateId,
                type = type
            ),
            previousScreen = subScreenSaveState,
            rememberStateKey = "servers_view"
        )
    }

    fun onItemOpen(item: CountryListItemState, type: ServerFilterType) {
        when (item.data) {
            is CountryListItemData.Country -> onOpenCountry(type, item.data.countryId)
            is CountryListItemData.City -> onOpenCity(type, item.data.countryId, item.data.cityStateId)
            is CountryListItemData.Server -> {} // shouldn't happen
        }
    }

    fun onItemConnect(
        vpnUiDelegate: VpnUiDelegate,
        item: CountryListItemState,
        filter: ServerListFilter,
        navigateToHome: (ShowcaseRecents) -> Unit,
        navigateToUpsell: () -> Unit,
    ) {
        viewModelScope.launch {
            subScreenSaveState = null
            if (!item.data.inMaintenance) {
                if (item.available) {
                    val intent = item.getConnectionIntent(filter)
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

    private fun getFilterButtons(
        availableTypes: Set<ServerFilterType>,
        selectedType: ServerFilterType,
        onItemSelect: (ServerFilterType) -> Unit
    ): List<FilterButton> =
        ServerFilterType.entries
            // If selected type is not available (no servers), show it anyway, this can happen if
            // list was updated and some servers are removed from the list.
            .filter { it in availableTypes || it == selectedType }
            .map { filter -> FilterButton(
                filter = filter,
                isSelected = filter == selectedType,
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
    is CountryListItemData.City -> !data.inMaintenance
    is CountryListItemData.Country -> !data.inMaintenance &&
        (data.entryCountryId == null || data.entryCountryId == CountryId.fastest)
}

private fun CountryListItemData.displayLabel(locale: Locale): String = when(this) {
    is CountryListItemData.Country -> countryId.toDisplayName(locale)
    is CountryListItemData.City -> name
    is CountryListItemData.Server -> name
}

private fun CountryListItemState.getConnectionIntent(filter: ServerListFilter): ConnectIntent = when(data) {
    is CountryListItemData.City ->
        ConnectIntent.FastestInCity(data.countryId, data.cityStateId.name, filter.toFeatures())
    is CountryListItemData.Server ->
        data.entryCountryId?.let { ConnectIntent.SecureCore(data.countryId, it) } ?:
            ConnectIntent.Server(data.serverId.id, data.serverFeatures)
    is CountryListItemData.Country ->
        data.entryCountryId?.let { ConnectIntent.SecureCore(data.countryId, it) } ?:
            ConnectIntent.FastestInCountry(data.countryId, filter.toFeatures())
}

private fun ServerListFilter.toFeatures(): Set<ServerFeature> = when (type) {
    ServerFilterType.P2P -> setOf(ServerFeature.P2P)
    ServerFilterType.Tor -> setOf(ServerFeature.Tor)
    else -> emptySet()
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
    val filter: ServerListFilter,
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
    val filterButtons: List<FilterButton>?,
    val items: List<CountryListItemState>,
)

// Adapter separating server data storage from view model.
interface CountryListViewModelDataAdapter {

    suspend fun availableTypesFor(countryId: CountryId?): Set<ServerFilterType>

    fun countries(filter: ServerListFilter):
        Flow<List<CountryListItemData.Country>>

    fun cities(filter: ServerListFilter):
        Flow<List<CountryListItemData.City>>

    fun servers(filter: ServerListFilter):
        Flow<List<CountryListItemData.Server>>

    fun entryCountries(country: CountryId):
        Flow<List<CountryListItemData.Country>>

    fun includesServer(data: CountryListItemData, server: Server): Boolean
}

