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

package com.protonvpn.android.redesign.countries.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CountryListViewModel @Inject constructor() : ViewModel() {

    val countryToCities = linkedMapOf(
        "Germany" to listOf("Berlin", "Munich", "Frankfurt"),
        "USA" to listOf("New York", "Los Angeles", "Chicago"),
        "Canada" to listOf("Toronto", "Montreal", "Vancouver"),
        "Switzerland" to listOf("Zurich", "Geneva", "Basel"),
        "France" to listOf("Paris", "Marseille", "Lyon"),
    )

    fun serversForCity(city: String) = (1..10).map { "$city#$it" }
}
