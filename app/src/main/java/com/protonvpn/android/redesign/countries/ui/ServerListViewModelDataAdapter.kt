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

import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.countries.TranslationsData
import kotlinx.coroutines.flow.Flow

// Adapter separating server data storage from view model.
interface ServerListViewModelDataAdapter {

    suspend fun countriesCount(): Int

    suspend fun availableTypesFor(country: CountryId?): Set<ServerFilterType>

    suspend fun haveStates(country: CountryId): Boolean

    fun countries(filter: ServerFilterType = ServerFilterType.All):
        Flow<List<ServerGroupItemData.Country>>

    fun cities(
        filter: ServerFilterType = ServerFilterType.All,
        country: CountryId,
        translations: TranslationsData?
    ): Flow<List<ServerGroupItemData.City>>

    fun servers(
        filter: ServerFilterType = ServerFilterType.All,
        country: CountryId? = null,
        cityStateId: CityStateId? = null,
        gatewayName: String? = null,
    ): Flow<List<ServerGroupItemData.Server>>

    fun entryCountries(country: CountryId):
        Flow<List<ServerGroupItemData.Country>>

    fun gateways(): Flow<List<ServerGroupItemData.Gateway>>

    suspend fun getHostCountry(countryId: CountryId): CountryId?
}
