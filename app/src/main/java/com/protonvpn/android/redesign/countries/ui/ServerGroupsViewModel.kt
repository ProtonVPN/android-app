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
import com.protonvpn.android.redesign.base.ui.InfoType
import com.protonvpn.android.redesign.countries.Translator
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
    private val translator: Translator,
    private val showFilters: Boolean,
) : ViewModel() {

    private val mainStateKey = "$screenId:$MainScreenStateKey"
    private var mainSaveState by savedStateHandle.state<CountryScreenSavedState>(defaultMainState, mainStateKey)
    private val mainSaveStateFlow = savedStateHandle.getStateFlow(mainStateKey, mainSaveState)

    private val subStateKey = "$screenId:$SubScreenStateKey"
    private var subScreenSaveState by savedStateHandle.state<SubScreenSaveState?>(null, subStateKey)
    private val subScreenSaveStateFlow = savedStateHandle.getStateFlow(subStateKey, subScreenSaveState)

    protected data class ActiveConnection(
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

    protected abstract fun getUiItems(
        savedState: CountryScreenSavedState,
        userTier: Int?,
        locale: Locale,
        currentConnection: ActiveConnection?
    ) : Flow<List<ServerGroupUiItem>>

    // Screen states
    val stateFlow =
        combine(
            mainSaveStateFlow,
            userTierFlow,
            localeFlow.filterNotNull(),
            currentConnectionFlow,
        ) { savedState, userTier, locale, currentConnection ->
            getUiItems(savedState, userTier, locale, currentConnection).map { items ->
                mainScreenState(savedState, items)
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
                getSubScreenUiItems(savedState, userTier, locale, currentConnection).map { items ->
                    subScreenState(savedState, items)
                }
            } else {
                flowOf(null)
            }
        }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private fun getSubScreenUiItems(
        savedState: SubScreenSaveState,
        userTier: Int?,
        locale: Locale,
        currentConnection: ActiveConnection?
    ): Flow<List<ServerGroupUiItem>> =
        when (savedState) {
            is CitiesScreenSaveState -> when (savedState.filter.type) {
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
            is GatewayServersScreenSaveState -> dataAdapter.servers(savedState.filter)
            is ServersScreenSaveState -> dataAdapter.servers(savedState.filter)
        }.map { dataItems ->
            buildList {
                val filterType = savedState.filter.type
                val hasStates = if (filterType == ServerFilterType.All)
                    dataItems.any { (it as? ServerGroupItemData.City)?.cityStateId?.isState == true }
                else
                    false
                val items = dataItems.sortedByLabel(locale).map { data ->
                    data.toState(userTier, savedState.filter, currentConnection)
                }

                add(
                    ServerGroupUiItem.Header(
                        subScreenHeaderLabel(hasStates, savedState, filterType),
                        items.size,
                        subScreenHeaderInfo(savedState, filterType),
                    )
                )
                addAll(items)
            }
        }

    private suspend fun mainScreenState(
        savedState: CountryScreenSavedState,
        items: List<ServerGroupUiItem>,
    ) = CountryScreenState(
        savedState = savedState,
        filterButtons =
            if (!showFilters)
                null
            else getFilterButtons(
                dataAdapter.availableTypesFor(savedState.filter.country),
                savedState.filter.type,
                allLabel = R.string.country_filter_all,
            ) {
                mainSaveState = CountryScreenSavedState(ServerListFilter(type = it))
            },
        items = items
    )

    private suspend fun getSubScreenFilterButtons(
        availableTypes: Set<ServerFilterType>,
        selectedType: ServerListFilter,
        savedState: CitiesScreenSaveState
    ): List<FilterButton> {
        val allLabel = if (dataAdapter.haveStates(savedState.filter))
            R.string.country_filter_states
        else
            R.string.country_filter_cities
        return getFilterButtons(availableTypes, selectedType.type, allLabel) { type ->
            val newFilter = savedState.filter.copy(type = type)
            subScreenSaveState = savedState.copy(filter = newFilter)
        }
    }

    private fun getFilterButtons(
        availableTypes: Set<ServerFilterType>,
        selectedType: ServerFilterType,
        @StringRes allLabel: Int,
        onItemSelect: (ServerFilterType) -> Unit,
    ): List<FilterButton> =
        ServerFilterType.entries
            // If selected type is not available (no servers), show it anyway, this can happen if
            // list was updated and some servers are removed from the list.
            .filter { it in availableTypes || it == selectedType }
            .map { filter ->
                FilterButton(
                    filter = filter,
                    isSelected = filter == selectedType,
                    onClick = { onItemSelect(filter) },
                    label = when (filter) {
                        ServerFilterType.All -> allLabel
                        ServerFilterType.SecureCore -> R.string.country_filter_secure_core
                        ServerFilterType.P2P -> R.string.country_filter_p2p
                        ServerFilterType.Tor -> R.string.country_filter_tor
                    },
                )
            }

    private suspend fun subScreenState(
        savedState: SubScreenSaveState,
        items: List<ServerGroupUiItem>,
    ): SubScreenState {
        return when (savedState) {
            is CitiesScreenSaveState -> CitiesScreenState(
                savedState = savedState,
                filterButtons = getSubScreenFilterButtons(
                    dataAdapter.availableTypesFor(savedState.countryId),
                    savedState.filter,
                    savedState
                ),
                items = items,
            )
            is GatewayServersScreenSaveState -> GatewayServersScreenState(
                savedState = savedState,
                items = items)
            is ServersScreenSaveState -> ServersScreenState(
                savedState = savedState,
                items = items)
        }
    }

    protected fun ServerGroupItemData.toState(
        userTier: Int?,
        filter: ServerListFilter,
        connection: ActiveConnection?,
    ): ServerGroupUiItem.ServerGroup {
        val itemConnectIntent = getConnectIntent(filter)
        val isItemFastest = itemConnectIntent.matchesFastestItem()
        val isConnectIntentFastest = connection?.intent?.matchesFastestItem() ?: false
        return ServerGroupUiItem.ServerGroup(
            data = this,
            available = userTier == null || (countryId?.isFastest == true || (userTier > 0 && userTier >= tier)),
            connected = connection?.server.isCompatibleWith(
                itemConnectIntent,
                matchFastest = !isItemFastest || isConnectIntentFastest
            ),
        )
    }

    private fun onOpenCountry(type: ServerFilterType, countryId: CountryId) {
        subScreenSaveState = CitiesScreenSaveState(
            previousScreen = null,
            filter = ServerListFilter(country = countryId, type = type),
            countryId = countryId,
            rememberStateKey = "country_view"
        )
    }

    private fun onOpenGateway(gatewayName: String) {
        subScreenSaveState = GatewayServersScreenSaveState(
            previousScreen = null,
            filter = ServerListFilter(gatewayName = gatewayName),
            gatewayName = gatewayName,
            rememberStateKey = "gateway_view"
        )
    }

    private fun onOpenCity(type: ServerFilterType, countryId: CountryId, cityStateId: CityStateId) {
        subScreenSaveState = ServersScreenSaveState(
            countryId = countryId,
            city = translator.getCity(cityStateId.name),
            filter = ServerListFilter(country = countryId, cityStateId = cityStateId, type = type),
            previousScreen = subScreenSaveState,
            rememberStateKey = "servers_view"
        )
    }

    fun onItemOpen(item: ServerGroupUiItem.ServerGroup, type: ServerFilterType) {
        when (item.data) {
            is ServerGroupItemData.Country -> onOpenCountry(type, item.data.countryId)
            is ServerGroupItemData.City -> onOpenCity(type, item.data.countryId, item.data.cityStateId)
            is ServerGroupItemData.Gateway -> onOpenGateway(item.data.gatewayName)
            is ServerGroupItemData.Server -> {} // shouldn't happen
        }
    }

    fun onItemConnect(
        vpnUiDelegate: VpnUiDelegate,
        item: ServerGroupUiItem.ServerGroup,
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
    @StringRes val label: Int,
    val isSelected: Boolean = false,
    val onClick: () -> Unit
) : Parcelable

val ServerGroupUiItem.ServerGroup.canOpen: Boolean get() = when(data) {
    is ServerGroupItemData.Server -> false
    is ServerGroupItemData.Gateway -> !data.inMaintenance
    is ServerGroupItemData.City -> !data.inMaintenance
    is ServerGroupItemData.Country -> !data.inMaintenance &&
        !data.countryId.isFastest &&
        (data.entryCountryId == null || data.entryCountryId == CountryId.fastest)
}

internal fun fastestCountryItem(filter: ServerFilterType): ServerGroupItemData.Country =
    ServerGroupItemData.Country(
        countryId = CountryId.fastest,
        inMaintenance = false,
        tier = 0,
        entryCountryId = if (filter == ServerFilterType.SecureCore) CountryId.fastest else null
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
            ConnectIntent.FastestInState(countryId, cityStateId.name, filter.toFeatures())
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

val ServerFilterType.info: InfoType? get() = when(this) {
    ServerFilterType.All -> null
    ServerFilterType.SecureCore -> InfoType.SecureCore
    ServerFilterType.P2P -> InfoType.P2P
    ServerFilterType.Tor -> InfoType.Tor
}

fun ServerFilterType.headerLabel(isFreeUser: Boolean): Int = when(this) {
    ServerFilterType.All ->
        if (isFreeUser)
            R.string.country_filter_all_list_header_free
        else
            R.string.country_filter_all_list_header
    ServerFilterType.SecureCore -> R.string.country_filter_secure_core_list_header
    ServerFilterType.P2P -> R.string.country_filter_p2p_list_header
    ServerFilterType.Tor -> R.string.country_filter_tor_list_header
}

val ServerFilterType.bannerType: ServerGroupUiItem.BannerType get() = when(this) {
    ServerFilterType.All -> ServerGroupUiItem.BannerType.Countries
    ServerFilterType.SecureCore -> ServerGroupUiItem.BannerType.SecureCore
    ServerFilterType.P2P -> ServerGroupUiItem.BannerType.P2P
    ServerFilterType.Tor -> ServerGroupUiItem.BannerType.Tor
}

private fun subScreenHeaderLabel(hasStates: Boolean, type: SubScreenSaveState, filterType: ServerFilterType): Int = when(filterType) {
    ServerFilterType.All ->
        if (type is CitiesScreenSaveState) {
            if (hasStates)
                R.string.country_filter_states_list_header
            else
                R.string.country_filter_cities_list_header
        } else
            R.string.country_filter_servers_list_header
    ServerFilterType.SecureCore -> R.string.country_filter_secure_core
    ServerFilterType.P2P ->
        if (hasStates)
            R.string.country_filter_p2p_list_header_states
        else
            R.string.country_filter_p2p_list_header_cities
    ServerFilterType.Tor -> R.string.country_filter_tor_servers_list_header
}

private fun subScreenHeaderInfo(subScreen: SubScreenSaveState, filter: ServerFilterType) = when(filter) {
    ServerFilterType.All -> when (subScreen) {
        is CitiesScreenSaveState -> null
        is GatewayServersScreenSaveState, is ServersScreenSaveState -> InfoType.ServerLoad
    }
    ServerFilterType.SecureCore -> InfoType.SecureCore
    ServerFilterType.P2P -> InfoType.P2P
    ServerFilterType.Tor -> InfoType.Tor
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

@Parcelize
data class CountryScreenSavedState(
    val filter: ServerListFilter,
): Parcelable

data class CountryScreenState(
    val savedState: CountryScreenSavedState,
    val filterButtons: List<FilterButton>?,
    val items: List<ServerGroupUiItem>,
)

@Parcelize
sealed class SubScreenSaveState: Parcelable {
    abstract val previousScreen: SubScreenSaveState?
    abstract val filter: ServerListFilter
    abstract val rememberStateKey: String
}

@Parcelize
data class CitiesScreenSaveState(
    val countryId: CountryId,
    override val filter: ServerListFilter,
    override val rememberStateKey: String,
    override val previousScreen: SubScreenSaveState? = null
) : SubScreenSaveState()

@Parcelize
data class ServersScreenSaveState(
    val countryId: CountryId,
    val city: String,
    override val filter: ServerListFilter,
    override val rememberStateKey: String,
    override val previousScreen: SubScreenSaveState?,
) : SubScreenSaveState()

@Parcelize
data class GatewayServersScreenSaveState(
    val gatewayName: String,
    override val filter: ServerListFilter,
    override val rememberStateKey: String,
    override val previousScreen: SubScreenSaveState? = null
) : SubScreenSaveState()

sealed class SubScreenState {
    abstract val savedState: SubScreenSaveState
    abstract val items: List<ServerGroupUiItem>
}

data class CitiesScreenState(
    override val savedState: CitiesScreenSaveState,
    override val items: List<ServerGroupUiItem>,
    val filterButtons: List<FilterButton>,
) : SubScreenState() {
    val countryId: CountryId get() = savedState.countryId
}

data class ServersScreenState(
    override val savedState: ServersScreenSaveState,
    override val items: List<ServerGroupUiItem>,
) : SubScreenState() {
    val countryId: CountryId get() = savedState.countryId
    val city: String get() = savedState.city
}

data class GatewayServersScreenState(
    override val savedState: GatewayServersScreenSaveState,
    override val items: List<ServerGroupUiItem>,
) : SubScreenState() {
    val gatewayName: String get() = savedState.gatewayName
}


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
