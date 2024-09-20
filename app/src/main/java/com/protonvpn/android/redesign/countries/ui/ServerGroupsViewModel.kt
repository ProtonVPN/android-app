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
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UiConnect
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
import com.protonvpn.android.vpn.VpnConnect
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
import java.text.DecimalFormat
import java.util.Locale

private const val MainScreenStateKey = "main_screen_state"
private const val SubScreenStateKey = "sub_screen_state"

enum class ServerFilterType {
    All, SecureCore, P2P, Tor;
}

@Parcelize
data class ServerGroupsMainScreenSaveState(
    val selectedFilter: ServerFilterType,
): Parcelable

data class ServerGroupsMainScreenState(
    val selectedFilter: ServerFilterType,
    val items: List<ServerGroupUiItem>,
    val filterButtons: List<FilterButton>,
)

@Parcelize
sealed class ServerGroupsSubScreenSaveState: Parcelable {
    abstract val previousScreen: ServerGroupsSubScreenSaveState?
    abstract val selectedFilter: ServerFilterType
}

@Parcelize
data class CitiesScreenSaveState(
    val countryId: CountryId,
    override val selectedFilter: ServerFilterType,
    override val previousScreen: ServerGroupsSubScreenSaveState? = null
) : ServerGroupsSubScreenSaveState()

@Parcelize
data class ServersScreenSaveState(
    val countryId: CountryId,
    val cityStateId: CityStateId,
    override val selectedFilter: ServerFilterType,
    override val previousScreen: ServerGroupsSubScreenSaveState?,
) : ServerGroupsSubScreenSaveState()

@Parcelize
data class GatewayServersScreenSaveState(
    val gatewayName: String,
    override val previousScreen: ServerGroupsSubScreenSaveState? = null
) : ServerGroupsSubScreenSaveState() {
    override val selectedFilter: ServerFilterType get() = ServerFilterType.All
}

sealed class ServerGroupsSubScreenState {
    abstract val selectedFilter: ServerFilterType
    abstract val items: List<ServerGroupUiItem>
}

data class CitiesScreenState(
    override val selectedFilter: ServerFilterType,
    override val items: List<ServerGroupUiItem>,
    val filterButtons: List<FilterButton>,
    val countryId: CountryId,
    val hostCountryId: CountryId?,
) : ServerGroupsSubScreenState()

data class ServersScreenState(
    override val selectedFilter: ServerFilterType,
    override val items: List<ServerGroupUiItem>,
    val countryId: CountryId,
    val cityStateDisplay: String,
) : ServerGroupsSubScreenState()

data class GatewayServersScreenState(
    override val items: List<ServerGroupUiItem>,
    val gatewayName: String
) : ServerGroupsSubScreenState() {
    override val selectedFilter: ServerFilterType get() = ServerFilterType.All
}

abstract class ServerGroupsViewModel<MainStateT>(
    screenId: String,
    savedStateHandle: SavedStateHandle,
    protected val dataAdapter: ServerListViewModelDataAdapter,
    private val connect: VpnConnect,
    private val shouldShowcaseRecents: ShouldShowcaseRecents,
    currentUser: CurrentUser,
    vpnStatusProviderUI: VpnStatusProviderUI,
    private val translator: Translator,
    defaultMainSavedState: ServerGroupsMainScreenSaveState,
) : ViewModel() {

    private val mainStateKey = "$screenId:$MainScreenStateKey"
    protected var mainSaveState by savedStateHandle.state<ServerGroupsMainScreenSaveState>(defaultMainSavedState, mainStateKey)
    protected val mainSaveStateFlow = savedStateHandle.getStateFlow(mainStateKey, mainSaveState)

    private val subStateKey = "$screenId:$SubScreenStateKey"
    private var subScreenSaveState by savedStateHandle.state<ServerGroupsSubScreenSaveState?>(null, subStateKey)
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

    protected abstract fun mainScreenState(
        savedStateFlow: Flow<ServerGroupsMainScreenSaveState>,
        userTier: Int?,
        locale: Locale,
        currentConnection: ActiveConnection?
    ) : Flow<MainStateT>

    // Screen states
    val stateFlow: StateFlow<MainStateT?> =
        combine(
            userTierFlow,
            localeFlow.filterNotNull(),
            currentConnectionFlow,
        ) { userTier, locale, currentConnection ->
            mainScreenState(mainSaveStateFlow, userTier, locale, currentConnection)
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
        savedState: ServerGroupsSubScreenSaveState,
        userTier: Int?,
        locale: Locale,
        currentConnection: ActiveConnection?
    ): Flow<List<ServerGroupUiItem>> =
        when (savedState) {
            is CitiesScreenSaveState -> when (savedState.selectedFilter) {
                ServerFilterType.All, ServerFilterType.P2P ->
                    dataAdapter.cities(savedState.selectedFilter, savedState.countryId)
                ServerFilterType.SecureCore ->
                    dataAdapter.entryCountries(savedState.countryId)
                ServerFilterType.Tor ->
                    dataAdapter.servers(savedState.selectedFilter, savedState.countryId, null, null)
            }
            is GatewayServersScreenSaveState -> dataAdapter.servers(savedState.selectedFilter, null, null, savedState.gatewayName)
            is ServersScreenSaveState -> dataAdapter.servers(savedState.selectedFilter, savedState.countryId, savedState.cityStateId, null)
        }.map { dataItems ->
            buildList {
                val filterType = savedState.selectedFilter
                val hasStates = if (filterType == ServerFilterType.All)
                    dataItems.any { (it as? ServerGroupItemData.City)?.cityStateId?.isState == true }
                else
                    false

                val items =  dataItems.sortedForUi(locale).map { data ->
                    data.toState(userTier, filterType, currentConnection)
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

    private suspend fun getCitiesScreenFilterButtons(
        availableTypes: Set<ServerFilterType>,
        selectedFilter: ServerFilterType,
        savedState: CitiesScreenSaveState
    ): List<FilterButton> {
        val allLabel = if (dataAdapter.haveStates(savedState.countryId))
            R.string.country_filter_states
        else
            R.string.country_filter_cities
        return getFilterButtons(availableTypes, selectedFilter, allLabel) { type ->
            subScreenSaveState = savedState.copy(selectedFilter = type)
        }
    }

    protected fun getFilterButtons(
        availableTypes: Set<ServerFilterType>,
        selectedType: ServerFilterType,
        @StringRes allLabel: Int,
        emptyTypes: Set<ServerFilterType> = emptySet(),
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
                    isEmpty = filter in emptyTypes,
                    label = when (filter) {
                        ServerFilterType.All -> allLabel
                        ServerFilterType.SecureCore -> R.string.country_filter_secure_core
                        ServerFilterType.P2P -> R.string.country_filter_p2p
                        ServerFilterType.Tor -> R.string.country_filter_tor
                    },
                )
            }

    private suspend fun subScreenState(
        savedState: ServerGroupsSubScreenSaveState,
        items: List<ServerGroupUiItem>,
    ): ServerGroupsSubScreenState {
        return when (savedState) {
            is CitiesScreenSaveState -> CitiesScreenState(
                selectedFilter = savedState.selectedFilter,
                countryId = savedState.countryId,
                filterButtons = getCitiesScreenFilterButtons(
                    dataAdapter.availableTypesFor(savedState.countryId),
                    savedState.selectedFilter,
                    savedState
                ),
                items = items,
                hostCountryId = dataAdapter.getHostCountry(savedState.countryId)
            )
            is GatewayServersScreenSaveState -> GatewayServersScreenState(
                gatewayName = savedState.gatewayName,
                items = items
            )
            is ServersScreenSaveState -> ServersScreenState(
                selectedFilter = savedState.selectedFilter,
                countryId = savedState.countryId,
                cityStateDisplay = translator.translateCityState(savedState.cityStateId),
                items = items
            )
        }
    }

    protected fun ServerGroupItemData.toState(
        userTier: Int?,
        filterType: ServerFilterType,
        connection: ActiveConnection?,
    ): ServerGroupUiItem.ServerGroup {
        val itemConnectIntent = getConnectIntent(filterType)
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
            selectedFilter = type,
            countryId = countryId,
        )
    }

    private fun onOpenGateway(gatewayName: String) {
        subScreenSaveState = GatewayServersScreenSaveState(
            gatewayName = gatewayName,

        )
    }

    private fun onOpenCity(type: ServerFilterType, countryId: CountryId, cityStateId: CityStateId) {
        subScreenSaveState = ServersScreenSaveState(
            countryId = countryId,
            cityStateId = cityStateId,
            selectedFilter = type,
            previousScreen = subScreenSaveState,
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
        filterType: ServerFilterType,
        navigateToHome: (ShowcaseRecents) -> Unit,
        navigateToUpsell: () -> Unit,
    ) {
        viewModelScope.launch {
            subScreenSaveState = null
            if (!item.data.inMaintenance) {
                if (item.available) {
                    val trigger = connectTrigger(item.data)
                    // Assumes that description contains all the necessary information.
                    ProtonLogger.log(UiConnect, trigger.description)
                    val connectIntent = item.data.getConnectIntent(filterType).takeIf { !item.connected }
                    if (connectIntent != null)
                        connect(vpnUiDelegate, connectIntent, trigger)
                    navigateToHome(connectIntent != null && shouldShowcaseRecents(connectIntent))
                } else {
                    navigateToUpsell()
                }
            }
        }
    }

    protected abstract fun connectTrigger(item: ServerGroupItemData): ConnectTrigger

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
    val isEmpty: Boolean = false, // indicates that there's no content for this filter
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

internal fun List<ServerGroupItemData>.sortedForUi(locale: Locale): List<ServerGroupItemData> {
    val sortLabel = associateWith { data -> data.getUiSortProperty(locale) }
    return sortedByLocaleAware(locale) { data -> sortLabel[data]!! }
}

private val loadPercentFormatter by lazy(LazyThreadSafetyMode.NONE) { DecimalFormat("000") }
private fun ServerGroupItemData.getUiSortProperty(locale: Locale): String = when(this) {
    is ServerGroupItemData.Country -> CountryTools.getFullName(locale, countryId.countryCode)
    is ServerGroupItemData.City -> name
    is ServerGroupItemData.Gateway -> gatewayName
    is ServerGroupItemData.Server -> loadPercentFormatter.format(loadPercent)
}

private fun ServerGroupItemData.getConnectIntent(filterType: ServerFilterType): ConnectIntent = when(this) {
    is ServerGroupItemData.City -> {
        if (cityStateId.isState)
            ConnectIntent.FastestInState(countryId, cityStateId.name, filterType.toFeatures())
        else
            ConnectIntent.FastestInCity(countryId, cityStateId.name, filterType.toFeatures())
    }
    is ServerGroupItemData.Server ->
        when {
            entryCountryId != null -> ConnectIntent.SecureCore(countryId, entryCountryId)
            gatewayName != null -> ConnectIntent.Gateway(gatewayName, serverId.id)
            else -> ConnectIntent.Server(serverId.id, serverFeatures)
        }
    is ServerGroupItemData.Country ->
        entryCountryId?.let { ConnectIntent.SecureCore(countryId, it) } ?:
            ConnectIntent.FastestInCountry(countryId, filterType.toFeatures())

    is ServerGroupItemData.Gateway -> ConnectIntent.Gateway(gatewayName, null)
}

private fun ServerFilterType.toFeatures(): Set<ServerFeature> = when (this) {
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

private fun subScreenHeaderLabel(hasStates: Boolean, type: ServerGroupsSubScreenSaveState, filterType: ServerFilterType): Int = when(filterType) {
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

private fun subScreenHeaderInfo(subScreen: ServerGroupsSubScreenSaveState, filter: ServerFilterType) = when(filter) {
    ServerFilterType.All -> when (subScreen) {
        is CitiesScreenSaveState -> null
        is GatewayServersScreenSaveState, is ServersScreenSaveState -> InfoType.ServerLoad
    }
    ServerFilterType.SecureCore -> InfoType.SecureCore
    ServerFilterType.P2P -> InfoType.P2P
    ServerFilterType.Tor -> InfoType.Tor
}

private fun Translator.translateCityState(cityStateId: CityStateId): String =
    if (cityStateId.isState)
        getState(cityStateId.name)
    else
        getCity(cityStateId.name)
