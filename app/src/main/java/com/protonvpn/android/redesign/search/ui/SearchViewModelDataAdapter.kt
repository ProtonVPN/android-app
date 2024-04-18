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

package com.protonvpn.android.redesign.search.ui

import com.protonvpn.android.redesign.countries.ui.ServerFilterType
import com.protonvpn.android.redesign.countries.ui.ServerGroupItemData
import kotlinx.coroutines.flow.Flow
import java.util.Locale

data class SearchResults(
    val countries: List<ServerGroupItemData.Country>,
    val cities: List<ServerGroupItemData.City>,
    val states: List<ServerGroupItemData.City>,
    val servers: List<ServerGroupItemData.Server>,
) {
    companion object {
        val empty = SearchResults(emptyList(), emptyList(), emptyList(), emptyList())
    }
}
fun SearchResults?.isEmpty(): Boolean =
    this == null || countries.isEmpty() && cities.isEmpty() && states.isEmpty() && servers.isEmpty()

interface SearchViewModelDataAdapter {

    fun search(term: String, locale: Locale): Flow<Map<ServerFilterType, SearchResults>>
}