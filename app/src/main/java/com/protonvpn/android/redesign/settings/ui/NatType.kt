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

package com.protonvpn.android.redesign.settings.ui

import androidx.annotation.StringRes
import com.protonvpn.android.R

enum class NatType(
    val labelRes: Int,
    @StringRes val shortLabelRes: Int,
    @StringRes val descriptionRes: Int
) {
    Strict(
        labelRes = R.string.settings_advanced_nat_type_strict,
        shortLabelRes = R.string.settings_advanced_nat_type_strict_short,
        descriptionRes = R.string.settings_advanced_nat_type_strict_description
    ),
    Moderate(
        labelRes = R.string.settings_advanced_nat_type_moderate,
        shortLabelRes = R.string.settings_advanced_nat_type_moderate_short,
        descriptionRes = R.string.settings_advanced_nat_type_moderate_description
    );

    fun toRandomizedNat() = this == Strict

    companion object {
        fun fromRandomizedNat(randomizedNat: Boolean) = if (randomizedNat) Strict else Moderate
    }
}