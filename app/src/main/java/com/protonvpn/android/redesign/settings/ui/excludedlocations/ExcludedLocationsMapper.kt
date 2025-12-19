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

package com.protonvpn.android.redesign.settings.ui.excludedlocations
import com.protonvpn.android.excludedlocations.ExcludedLocation
import com.protonvpn.android.redesign.countries.TranslationsData
import com.protonvpn.android.redesign.countries.city
import com.protonvpn.android.redesign.countries.state
import com.protonvpn.android.redesign.search.TextMatch
import com.protonvpn.android.redesign.settings.ui.excludedlocations.ExcludedLocationsViewModel.ExcludedLocationUiItem
import com.protonvpn.android.utils.CountryTools
import java.util.Locale

fun ExcludedLocation.toExcludedLocationUiItem(
    locale: Locale,
    translations: TranslationsData?,
): ExcludedLocationUiItem.Location = when (this) {
    is ExcludedLocation.City -> {
        val fullText = nameEn?.let { translations.city(countryId, it) }

        ExcludedLocationUiItem.Location.City(
            locationId = id,
            countryId = countryId,
            textMatch = fullText?.let { text ->
                TextMatch(
                    index = 0,
                    length = text.length,
                    fullText = text,
                )
            },
            nameEn = nameEn,
        )
    }

    is ExcludedLocation.Country -> {
        val fullText = CountryTools.getFullName(locale = locale, country = countryId.countryCode)

        ExcludedLocationUiItem.Location.Country(
            locationId = id,
            countryId = countryId,
            textMatch = TextMatch(
                index = 0,
                length = fullText.length,
                fullText = fullText,
            ),
            countryCities = emptyList(),
        )
    }

    is ExcludedLocation.State -> {
        val fullText = nameEn?.let { translations.state(countryId, it) }

        ExcludedLocationUiItem.Location.State(
            locationId = id,
            countryId = countryId,
            textMatch = fullText?.let { text ->
                TextMatch(
                    index = 0,
                    length = text.length,
                    fullText = text,
                )
            },
            nameEn = nameEn,
        )
    }
}

fun ExcludedLocationUiItem.Location.toExcludedLocation(): ExcludedLocation {
    val excludedLocationId = locationId ?: 0L

    return when (this) {
        is ExcludedLocationUiItem.Location.City -> {
            ExcludedLocation.City(
                id = excludedLocationId,
                countryId = countryId,
                nameEn = nameEn,
            )
        }

        is ExcludedLocationUiItem.Location.Country -> {
            ExcludedLocation.Country(
                id = excludedLocationId,
                countryId = countryId,
            )
        }

        is ExcludedLocationUiItem.Location.State -> {
            ExcludedLocation.State(
                id = excludedLocationId,
                countryId = countryId,
                nameEn = nameEn,
            )
        }
    }
}
