/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.android.redesign.countries.ui

import androidx.lifecycle.SavedStateHandle
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.redesign.main_screen.ui.ShouldShowcaseRecents
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CountriesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    dataAdapter: ServerListViewModelDataAdapter,
    vpnConnectionManager: VpnConnectionManager,
    shouldShowcaseRecents: ShouldShowcaseRecents,
    currentUser: CurrentUser,
    vpnStatusProviderUI: VpnStatusProviderUI,
) : ServerGroupsViewModel(
    "country_list",
    savedStateHandle,
    dataAdapter,
    vpnConnectionManager,
    shouldShowcaseRecents,
    currentUser,
    vpnStatusProviderUI,
    showFilters = true
) {
    override fun getUiItems(
        savedState: CountryScreenSavedState,
        userTier: Int?,
        locale: Locale,
        currentConnection: ActiveConnection?,
    ) : Flow<List<ServerGroupUiItem>> =
        dataAdapter.countries(savedState.filter).map { countries ->
            val filterType = savedState.filter.type
            val isFreeUser = userTier != null && userTier == VpnUser.FREE_TIER
            val items = buildList {
                if (!isFreeUser && countries.size > 1)
                    add(fastestCountryItem(savedState.filter))
                addAll(countries.sortedByLabel(locale))
            }.map {
                it.toState(userTier, savedState.filter, currentConnection)
            }

            buildList {
                add(ServerGroupUiItem.Header(filterType.headerLabel(isFreeUser), countries.size, filterType.info))
                if (isFreeUser)
                    add(ServerGroupUiItem.Banner(filterType.bannerType))
                addAll(items)
            }
        }
}