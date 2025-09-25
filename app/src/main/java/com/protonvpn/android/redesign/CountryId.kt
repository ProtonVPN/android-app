/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.redesign

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

private const val FASTEST_EXCLUDING_MY_COUNTRY = "FASTEST_EXCLUDING_MY_COUNTRY"

// TODO: this class should replace raw String used for representing the country code including the logic
//  for converting GB to UK that we have in several places.
@Immutable
@JvmInline
@Parcelize
value class CountryId private constructor(val countryCode: String) : Parcelable {

    // Note: this includes isFastestExcludingMyCountry.
    val isFastest: Boolean
        get() = countryCode == "" || isFastestExcludingMyCountry

    val isFastestExcludingMyCountry: Boolean
        get() = countryCode == FASTEST_EXCLUDING_MY_COUNTRY

    companion object {
        val fastest = CountryId("")
        val fastestExcludingMyCountry = CountryId(FASTEST_EXCLUDING_MY_COUNTRY)

        // Constants for countries that are compared in code.
        val iceland = invoke("is")
        val sweden = invoke("se")
        val switzerland = invoke("ch")

        operator fun invoke(countryCode: String) = CountryId(sanitizeCountryCode(countryCode))

        private fun sanitizeCountryCode(code: String) =
            code.uppercase().let {
                if (it == "UK") "GB" else it
            }
    }
}

@Parcelize
data class CityStateId(val name: String, val isState: Boolean) : Parcelable {
    val isFastest: Boolean get() = name == ""

    companion object {
        val fastestCity = CityStateId("", false)
        val fastestState = CityStateId("", true)
    }
}

@Immutable
@JvmInline
@Parcelize
value class ServerId(val id: String) : Parcelable
