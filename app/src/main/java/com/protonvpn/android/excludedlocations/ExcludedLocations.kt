/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.excludedlocations

import com.protonvpn.android.redesign.CountryId

data class ExcludedLocations(val allLocations: List<ExcludedLocation>) {

    val hasExclusions: Boolean = allLocations.isNotEmpty()

    val countryExclusionsCount: Int = allLocations.filterIsInstance<ExcludedLocation.Country>().size

    val cityExclusionsCount: Int = allLocations.filterIsInstance<ExcludedLocation.City>().size

    val stateExclusionsCount: Int = allLocations.filterIsInstance<ExcludedLocation.State>().size

    private val excludedCountryCodes by lazy {
        allLocations.filterIsInstance<ExcludedLocation.Country>()
            .map { excludedCountry -> excludedCountry.countryId.countryCode }
            .toSet()
    }

    private val excludedCountryCities by lazy {
        allLocations.filterIsInstance<ExcludedLocation.City>()
            .mapNotNull { excludedCity ->
                excludedCity.nameEn?.let { excludedCityNameEn ->
                    excludedCity.countryId.countryCode to excludedCityNameEn
                }
            }
            .toSet()
    }

    private val excludedCountryStates by lazy {
        allLocations.filterIsInstance<ExcludedLocation.State>()
            .mapNotNull { excludedState ->
                excludedState.nameEn?.let { excludedStateNameEn ->
                    excludedState.countryId.countryCode to excludedStateNameEn
                }
            }
            .toSet()
    }

    fun isCountryExcluded(countryCode: String): Boolean {
        return excludedCountryCodes.contains(countryCode)
    }

    fun isCityExcluded(countryCode: String, nameEn: String): Boolean {
        return excludedCountryCities.contains(countryCode to nameEn)
    }

    fun isStateExcluded(countryCode: String, nameEn: String): Boolean {
        return excludedCountryStates.contains(countryCode to nameEn)
    }

    companion object {

        val Empty: ExcludedLocations = ExcludedLocations(allLocations = emptyList())

    }

}

sealed interface ExcludedLocation {

    val id: Long

    val countryId: CountryId

    data class City(
        override val id: Long = 0L,
        override val countryId: CountryId,
        val nameEn: String?,
    ) : ExcludedLocation

    data class Country(
        override val id: Long = 0L,
        override val countryId: CountryId,
    ) : ExcludedLocation

    data class State(
        override val id: Long = 0L,
        override val countryId: CountryId,
        val nameEn: String?,
    ) : ExcludedLocation

}
