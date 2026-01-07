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

package com.protonvpn.app.excludedlocations

import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.excludedlocations.ExcludedLocation
import com.protonvpn.android.excludedlocations.data.ExcludedLocationType

object TestExcludedLocation {

    fun create(
        id: Long = 0L,
        countryCode: String = "CH",
        nameEn: String = "Switzerland",
        type: ExcludedLocationType = ExcludedLocationType.Country,
    ): ExcludedLocation = when (type) {
        ExcludedLocationType.City -> createCity(
            id = id,
            countryCode = countryCode,
            nameEn = nameEn,
        )

        ExcludedLocationType.Country -> createCountry(
            id = id,
            countryCode = countryCode,
        )

        ExcludedLocationType.State -> createState(
            id = id,
            countryCode = countryCode,
            nameEn = nameEn,
        )
    }

    fun createCity(
        id: Long = 0L,
        countryCode: String = "CH",
        nameEn: String = "Switzerland",
    ): ExcludedLocation.City = ExcludedLocation.City(
        id = id,
        countryId = CountryId(countryCode = countryCode),
        nameEn = nameEn,
    )

    fun createCountry(
        id: Long = 0L,
        countryCode: String = "CH",
    ): ExcludedLocation.Country = ExcludedLocation.Country(
        id = id,
        countryId = CountryId(countryCode = countryCode),
    )

    fun createState(
        id: Long = 0L,
        countryCode: String = "CH",
        nameEn: String = "Switzerland",
    ): ExcludedLocation.State = ExcludedLocation.State(
        id = id,
        countryId = CountryId(countryCode = countryCode),
        nameEn = nameEn,
    )

}
