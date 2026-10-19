/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.tv.countries.ui

import androidx.annotation.DrawableRes
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId

data class CountryConnectMenuUiState(
    val countryId: CountryId,
    val countryName: String,
    @param:DrawableRes val flagResource: Int,
    val options: List<CountryConnectMenuOption>,
)

data class CountryConnectMenuOption(
    val type: Type,
    val available: Boolean,
    val inMaintenance: Boolean,
    val connected: Boolean,
) {
    sealed interface Type {
        data object Fastest : Type
        data object Random : Type
        data class CityState(val cityStateId: CityStateId, val name: String) : Type
    }
}
