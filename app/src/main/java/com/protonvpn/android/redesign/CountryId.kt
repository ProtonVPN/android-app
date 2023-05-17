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

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import com.protonvpn.android.R
import com.protonvpn.android.utils.CountryTools

// TODO: this class should replace raw String used for representing the country code including the logic
//  for converting GB to UK that we have in several places.
@Immutable
@JvmInline
value class CountryId private constructor(val countryCode: String) {

    val isFastest: Boolean
        get() = countryCode == ""

    companion object {
        val fastest = CountryId("")

        // Constants for countries that are compared in code.
        val iceland = invoke("is")
        val sweden = invoke("se")
        val switzerland = invoke("ch")

        operator fun invoke(countryCode: String) = CountryId(sanitizeCountryCode(countryCode))

        private fun sanitizeCountryCode(code: String) =
            code.uppercase().let {
                if (it == "GB") "UK" else it
            }
    }
}

@DrawableRes
fun CountryId.flagResource(context: Context): Int =
    if (isFastest) R.drawable.flag_fastest else CountryTools.getFlagResource(context, countryCode)
