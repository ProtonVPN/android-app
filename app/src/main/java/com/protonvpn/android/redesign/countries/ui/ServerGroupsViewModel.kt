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

private const val MainScreenStateKey = "main_screen_state"
private const val SubScreenStateKey = "sub_screen_state"

private val defaultMainState = CountryScreenSavedState(ServerListFilter(type = ServerFilterType.All))

abstract class ServerGroupsViewModel(
    screenId: String,
    savedStateHandle: SavedStateHandle,
    protected val dataAdapter: ServerListViewModelDataAdapter,
    private val vpnConnectionManager: VpnConnectionManager,
    private val shouldShowcaseRecents: ShouldShowcaseRecents,
    currentUser: CurrentUser,
    vpnStatusProviderUI: VpnStatusProviderUI,
    private val showFilters: Boolean,
) : ViewModel() {

    private val mainStateKey = "$screenId:$MainScreenStateKey"
    private var mainSaveState by savedStateHandle.state<CountryScreenSavedState>(defaultMainState, mainStateKey)
    private val mainSaveStateFlow = savedStateHandle.getStateFlow(mainStateKey, mainSaveState)

    private val subStateKey = "$screenId:$SubScreenStateKey"
    private var subScreenSaveState by savedStateHandle.state<SubScreenSaveState?>(null, subStateKey)
    private val subScreenSaveStateFlow = savedStateHandle.getStateFlow(subStateKey, subScreenSaveState)

    private data class ActiveConnection(
        val intent: ConnectIntent?,
        val server: Server?,
    )

    // Helper flows
    val localeFlow = MutableStateFlow<Locale?>(null)
    private val userTierFlow = currentUser.vpnUserFlow.map { it?.maxTier }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)
    private val currentConnectionFlow =
        vpnStatusProviderUI.uiStatus
            .map { status -> ActiveConnection(status.connectIntent, status.server).takeIf { status.state == VpnState.Connected } }
            .distinctUntilChanged()

    abstract fun getMainDataItems(
        savedState: CountryScreenSavedState,
        userTier: Int?,
        locale: Locale,
    ) : Flow<List<ServerGroupItemData>>

    // Screen states
    val stateFlow =
        combine(
            mainSaveStateFlow,
            userTierFlow,
            localeFlow.filterNotNull(),
            currentConnectionFlow,
        ) { savedState, userTier, locale, currentConnection ->
            getMainDataItems(savedState, userTier, locale).map { dataItems ->
                val mainScreenItems = dataItems.map {
                    it.toState(userTier, savedState.filter, currentConnection)
                }
                mainScreenState(savedState, mainScreenItems)
            }
        }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val subScreenStateFlow =
        combine(
            subScreenSaveStateFlow,
            userTierFlow,
            localeFlow.filterNotNull(),
            currentConnectionFlow,
        ) { savedState, userTier, locale, currentConnection ->
            if (savedState != null) {
                getSubScreenDataItems(savedState, locale).map { dataItems ->
                    val items = dataItems.map {
                        it.toState(userTier, savedState.filter, currentConnection)
                    }
                    subScreenState(savedState, items)
                }
            } else {
                flowOf(null)
            }
        }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private fun getSubScreenDataItems(
        savedState: SubScreenSaveState,
        locale: Locale
    ): Flow<List<ServerGroupItemData>> =
        when (savedState.type) {
            SubScreenType.Cities -> when (savedState.filter.type) {
                ServerFilterType.All, ServerFilterType.P2P ->
                    dataAdapter.cities(savedState.filter)
                ServerFilterType.SecureCore -> {
                    val country = savedState.filter.country
                    if (country == null)
                        flowOf(emptyList())
                    else
                        dataAdapter.entryCountries(country)
                }
                ServerFilterType.Tor ->
                    dataAdapter.servers(savedState.filter)
            }
            SubScreenType.Servers -> dataAdapter.servers(savedState.filter)
            SubScreenType.GatewayServers -> dataAdapter.servers(savedState.filter)
        }.map { it.sortedByLabel(locale) }

    private suspend fun mainScreenState(
        savedState: CountryScreenSavedState,
        items: List<ServerGroupItemState>,
    ) = CountryScreenState(
        savedState = savedState,
        items = items,
        filterButtons =
        if (!showFilters)
            null
        else getFilterButtons(
            dataAdapter.availableTypesFor(savedState.filter.country),
            savedState.filter.type
        ) {
            mainSaveState = CountryScreenSavedState(ServerListFilter(type = it))
        }
    )

    private fun getSubScreenFilterButtons(
        availableTypes: Set<ServerFilterType>,
        selectedType: ServerFilterType,
        savedState: SubScreenSaveState
    ): List<FilterButton> =
        getFilterButtons(availableTypes, selectedType) { type ->
            val newFilter = savedState.filter.copy(type = type)
            subScreenSaveState = savedState.copy(filter = newFilter)
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
            .map { filter ->
                FilterButton(
                    filter = filter,
                    isSelected = filter == selectedType,
                    onClick = { onItemSelect(filter) }
                )
            }

    private suspend fun subScreenState(
        savedState: SubScreenSaveState,
        items: List<ServerGroupItemState>
    ): SubScreenState {
        val filterButtons = if (showFilters) {
            when (savedState.type) {
                SubScreenType.Cities -> getSubScreenFilterButtons(
                    dataAdapter.availableTypesFor(savedState.filter.country),
                    savedState.filter.type,
                    savedState
                )
                SubScreenType.Servers,
                SubScreenType.GatewayServers -> null
            }
        } else {
            null
        }
        return SubScreenState(
            savedState,
            filterButtons,
            items,
            when {
                filterButtons == null -> null
                dataAdapter.haveStates(savedState.filter) -> R.string.country_filter_states
                else -> R.string.country_filter_cities
            }
        )
    }

    private fun ServerGroupItemData.toState(
        userTier: Int?,
        filter: ServerListFilter,
        connection: ActiveConnection?,
    ): ServerGroupItemState {
        val itemConnectIntent = getConnectIntent(filter)
        val isItemFastest = itemConnectIntent.matchesFastestItem()
        val isConnectIntentFastest = connection?.intent?.matchesFastestItem() ?: false
        return ServerGroupItemState(
            data = this,
            available = userTier == null || (countryId?.isFastest == true || (userTier > 0 && userTier >= tier)),
            connected = connection?.server.isCompatibleWith(
                itemConnectIntent,
                matchFastest = !isItemFastest || isConnectIntentFastest
            ),
        )
    }

    private fun onOpenCountry(type: ServerFilterType, countryId: CountryId) {
        subScreenSaveState = SubScreenSaveState(
            type = SubScreenType.Cities,
            filter = ServerListFilter(country = countryId, type = type),
            previousScreen = null,
            rememberStateKey = "country_view"
        )
    }

    private fun onOpenGateway(gatewayName: String) {
        subScreenSaveState = SubScreenSaveState(
            type = SubScreenType.GatewayServers,
            filter = ServerListFilter(gatewayName = gatewayName),
            previousScreen = null,
            rememberStateKey = "gateway_view"
        )
    }

    private fun onOpenCity(type: ServerFilterType, countryId: CountryId, cityStateId: CityStateId) {
        subScreenSaveState = SubScreenSaveState(
            type = SubScreenType.Servers,
            filter = ServerListFilter(
                country = countryId,
                cityStateId = cityStateId,
                type = type
            ),
            previousScreen = subScreenSaveState,
            rememberStateKey = "servers_view"
        )
    }

    fun onItemOpen(item: ServerGroupItemState, type: ServerFilterType) {
        when (item.data) {
            is ServerGroupItemData.Country -> onOpenCountry(type, item.data.countryId)
            is ServerGroupItemData.City -> onOpenCity(type, item.data.countryId, item.data.cityStateId)
            is ServerGroupItemData.Gateway -> onOpenGateway(item.data.gatewayName)
            is ServerGroupItemData.Server -> {} // shouldn't happen
        }
    }

    fun onItemConnect(
        vpnUiDelegate: VpnUiDelegate,
        item: ServerGroupItemState,
        filter: ServerListFilter,
        navigateToHome: (ShowcaseRecents) -> Unit,
        navigateToUpsell: () -> Unit,
    ) {
        viewModelScope.launch {
            subScreenSaveState = null
            if (!item.data.inMaintenance) {
                if (item.available) {
                    val connectIntent = item.data.getConnectIntent(filter).takeIf { !item.connected }
                    val trigger = ConnectTrigger.Server("")
                    if (connectIntent != null)
                        vpnConnectionManager.connect(vpnUiDelegate, connectIntent, trigger)
                    navigateToHome(connectIntent != null && shouldShowcaseRecents(connectIntent))
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

    private fun ConnectIntent.matchesFastestItem() = when(this) {
        is ConnectIntent.FastestInCountry -> country == CountryId.fastest
        is ConnectIntent.SecureCore -> exitCountry == CountryId.fastest && entryCountry == CountryId.fastest
        else -> false
    }
}

@Parcelize
data class FilterButton(
    val filter: ServerFilterType,
    val isSelected: Boolean = false,
    val onClick: () -> Unit
) : Parcelable

val ServerGroupItemState.canOpen: Boolean get() = when(data) {
    is ServerGroupItemData.Server -> false
    is ServerGroupItemData.Gateway -> !data.inMaintenance
    is ServerGroupItemData.City -> !data.inMaintenance
    is ServerGroupItemData.Country -> !data.inMaintenance &&
        !data.countryId.isFastest &&
        (data.entryCountryId == null || data.entryCountryId == CountryId.fastest)
}

internal fun fastestCountryItem(filter: ServerListFilter): ServerGroupItemData.Country =
    ServerGroupItemData.Country(
        countryId = CountryId.fastest,
        inMaintenance = false,
        tier = 0,
        entryCountryId = if (filter.type == ServerFilterType.SecureCore) CountryId.fastest else null
    )

internal fun List<ServerGroupItemData>.sortedByLabel(locale: Locale): List<ServerGroupItemData> {
    val sortLabel = associateWith { data -> data.sortLabel(locale) }
    return sortedByLocaleAware { data -> sortLabel[data]!! }
}

private fun ServerGroupItemData.sortLabel(locale: Locale): String = when(this) {
    is ServerGroupItemData.Country -> CountryTools.getFullName(locale, countryId.countryCode)
    is ServerGroupItemData.City -> name
    is ServerGroupItemData.Server -> name
    is ServerGroupItemData.Gateway -> gatewayName
}

private fun ServerGroupItemData.getConnectIntent(filter: ServerListFilter): ConnectIntent = when(this) {
    is ServerGroupItemData.City -> {
        if (cityStateId.isState)
            ConnectIntent.FastestInRegion(countryId, cityStateId.name, filter.toFeatures())
        else
            ConnectIntent.FastestInCity(countryId, cityStateId.name, filter.toFeatures())
    }
    is ServerGroupItemData.Server ->
        when {
            entryCountryId != null -> ConnectIntent.SecureCore(countryId, entryCountryId)
            gatewayName != null -> ConnectIntent.Gateway(gatewayName, serverId.id)
            else -> ConnectIntent.Server(serverId.id, serverFeatures)
        }
    is ServerGroupItemData.Country ->
        entryCountryId?.let { ConnectIntent.SecureCore(countryId, it) } ?:
            ConnectIntent.FastestInCountry(countryId, filter.toFeatures())

    is ServerGroupItemData.Gateway -> ConnectIntent.Gateway(gatewayName, null)
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
    val gatewayName: String? = null,
    val type: ServerFilterType = ServerFilterType.All
): Parcelable

enum class SubScreenType { Cities, Servers, GatewayServers }

@Parcelize
data class CountryScreenSavedState(
    val filter: ServerListFilter,
): Parcelable

data class CountryScreenState(
    val savedState: CountryScreenSavedState,
    val filterButtons: List<FilterButton>?,
    val items: List<ServerGroupItemState>,
)

@Parcelize
data class SubScreenSaveState(
    val type: SubScreenType,
    val filter: ServerListFilter,
    val previousScreen: SubScreenSaveState?,
    val rememberStateKey: String
): Parcelable

data class SubScreenState(
    val savedState: SubScreenSaveState,
    val filterButtons: List<FilterButton>?,
    val items: List<ServerGroupItemState>,
    @StringRes val allLabelRes: Int?,
)

// Adapter separating server data storage from view model.
interface ServerListViewModelDataAdapter {

    suspend fun availableTypesFor(country: CountryId?): Set<ServerFilterType>

    suspend fun haveStates(filter: ServerListFilter): Boolean

    fun countries(filter: ServerListFilter):
        Flow<List<ServerGroupItemData.Country>>

    fun cities(filter: ServerListFilter):
        Flow<List<ServerGroupItemData.City>>

    fun servers(filter: ServerListFilter):
        Flow<List<ServerGroupItemData.Server>>

    fun entryCountries(country: CountryId):
        Flow<List<ServerGroupItemData.Country>>

    fun gateways(filter: ServerListFilter): Flow<List<ServerGroupItemData.Gateway>>
}
