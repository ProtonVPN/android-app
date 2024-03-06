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
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.home_screen.ui.ShowcaseRecents
import com.protonvpn.android.redesign.main_screen.ui.ShouldShowcaseRecents
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.isCompatibleWith
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
import kotlinx.coroutines.flow.shareIn
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
    vpnStatusProviderUI: VpnStatusProviderUI,
) : ViewModel() {

    private var mainSaveState by savedStateHandle.state<CountryScreenSavedState>(defaultMainState, CountryScreenStateKey)
    private val mainSaveStateFlow = savedStateHandle.getStateFlow(CountryScreenStateKey, mainSaveState)

    private var subScreenSaveState by savedStateHandle.state<SubScreenSaveState?>(null, CountrySubScreenStateKey)
    private val subScreenSaveStateFlow = savedStateHandle.getStateFlow(CountrySubScreenStateKey, subScreenSaveState)

    // Helper flows
    val localeFlow = MutableStateFlow<Locale?>(null)
    private val userTierFlow = currentUser.vpnUserFlow.map { it?.maxTier }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)
    private val connectedServerFlow =
        vpnStatusProviderUI.uiStatus
            .map { status -> status.server.takeIf { status.state == VpnState.Connected } }
            .distinctUntilChanged()

    // Screen states
    val stateFlow =
        combine(
            mainSaveStateFlow,
            userTierFlow,
            localeFlow.filterNotNull(),
            connectedServerFlow,
        ) { savedState, userTier, locale, connectedServer ->
            dataAdapter.countries(savedState.filter).map { countries ->
                val availableTypes = dataAdapter.availableTypesFor(savedState.filter.country)
                val mainScreenItems = buildList {
                    add(fastestCountryItem(savedState.filter))
                    addAll(countries.sortedByLabel(locale))
                }.map {
                    it.toState(userTier, savedState.filter, connectedServer)
                }
                CountryScreenState(
                    savedState = savedState,
                    items = mainScreenItems,
                    filterButtons = getFilterButtons(availableTypes, savedState.filter.type) {
                        mainSaveState = CountryScreenSavedState(ServerListFilter(type = it))
                    }
                )
            }
        }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val subScreenStateFlow =
        combine(
            subScreenSaveStateFlow,
            userTierFlow,
            localeFlow.filterNotNull(),
            connectedServerFlow,
        ) { savedState, userTier, locale, connectedServer ->
            if (savedState != null) {
                val availableTypes = dataAdapter.availableTypesFor(savedState.countryId)
                val haveStates = dataAdapter.haveStates(savedState.countryId)
                val dataFlow = when (savedState.type) {
                    SubScreenType.City -> when (savedState.filter.type) {
                        ServerFilterType.All, ServerFilterType.P2P -> dataAdapter.cities(savedState.filter)
                        ServerFilterType.SecureCore -> dataAdapter.entryCountries(savedState.countryId)
                        ServerFilterType.Tor -> dataAdapter.servers(savedState.filter)
                    }
                    SubScreenType.Server -> dataAdapter.servers(savedState.filter)
                }
                dataFlow.map { dataItems ->
                    val items = dataItems.sortedByLabel(locale).map {
                        it.toState(userTier, savedState.filter, connectedServer)
                    }
                    val filterButtons = when (savedState.type) {
                        SubScreenType.City ->
                            getSubScreenFilterButtons(availableTypes, savedState.filter.type, savedState)
                        SubScreenType.Server -> null
                    }
                    SubScreenState(
                        savedState,
                        filterButtons,
                        items,
                        if (haveStates) R.string.country_filter_states else R.string.country_filter_cities
                    )
                }
            } else {
                flowOf(null)
            }
        }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private fun getSubScreenFilterButtons(
        availableTypes: Set<ServerFilterType>,
        selectedType: ServerFilterType,
        savedState: SubScreenSaveState
    ): List<FilterButton> =
        getFilterButtons(availableTypes, selectedType) { type ->
            val newFilter = savedState.filter.copy(type = type)
            subScreenSaveState = savedState.copy(filter = newFilter)
        }

    private fun List<CountryListItemData>.sortedByLabel(locale: Locale): List<CountryListItemData> {
        val sortLabel = HashMap<CountryListItemData, String>()
        return sortedByLocaleAware { data ->
            sortLabel.getOrPut(data) { data.sortLabel(locale) }
        }
    }

    private fun CountryListItemData.toState(
        userTier: Int?,
        filter: ServerListFilter,
        connectedServer: Server?
    ) = CountryListItemState(
        data = this,
        available = userTier == null || (countryId.isFastest || (userTier > 0 && userTier >= tier)),
        connected = connectedServer != null && connectedServer.isCompatibleWith(getConnectIntent(filter)),
    )

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
                    val intent = item.data.getConnectIntent(filter)
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
        !data.countryId.isFastest &&
        (data.entryCountryId == null || data.entryCountryId == CountryId.fastest)
}

private fun fastestCountryItem(filter: ServerListFilter): CountryListItemData.Country =
    CountryListItemData.Country(
        countryId = CountryId.fastest,
        inMaintenance = false,
        tier = 0,
        entryCountryId = if (filter.type == ServerFilterType.SecureCore) CountryId.fastest else null
    )

private fun CountryListItemData.sortLabel(locale: Locale): String = when(this) {
    is CountryListItemData.Country -> CountryTools.getFullName(locale, countryId.countryCode)
    is CountryListItemData.City -> name
    is CountryListItemData.Server -> name
}

private fun CountryListItemData.getConnectIntent(filter: ServerListFilter): ConnectIntent = when(this) {
    is CountryListItemData.City -> {
        if (cityStateId.isState)
            ConnectIntent.FastestInRegion(countryId, cityStateId.name, filter.toFeatures())
        else
            ConnectIntent.FastestInCity(countryId, cityStateId.name, filter.toFeatures())
    }
    is CountryListItemData.Server ->
        entryCountryId?.let { ConnectIntent.SecureCore(countryId, it) } ?:
            ConnectIntent.Server(serverId.id, serverFeatures)
    is CountryListItemData.Country ->
        entryCountryId?.let { ConnectIntent.SecureCore(countryId, it) } ?:
            ConnectIntent.FastestInCountry(countryId, filter.toFeatures())
}

private fun ServerListFilter.toFeatures(): Set<ServerFeature> = when (type) {
    ServerFilterType.P2P -> setOf(ServerFeature.P2P)
    ServerFilterType.Tor -> setOf(ServerFeature.Tor)
    else -> emptySet()
}

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
    @StringRes val allLabelRes: Int,
)

// Adapter separating server data storage from view model.
interface CountryListViewModelDataAdapter {

    suspend fun availableTypesFor(country: CountryId?): Set<ServerFilterType>

    suspend fun haveStates(country: CountryId): Boolean

    fun countries(filter: ServerListFilter):
        Flow<List<CountryListItemData.Country>>

    fun cities(filter: ServerListFilter):
        Flow<List<CountryListItemData.City>>

    fun servers(filter: ServerListFilter):
        Flow<List<CountryListItemData.Server>>

    fun entryCountries(country: CountryId):
        Flow<List<CountryListItemData.Country>>
}

