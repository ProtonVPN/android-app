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

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.base.ui.Flag
import com.protonvpn.android.redesign.base.ui.previews.PreviewBooleanProvider
import me.proton.core.presentation.R as CoreR

@Composable
fun ExcludedLocationIcon(
    uiExcludedLocation: ExcludedLocationsViewModel.ExcludedLocationUiItem.Location,
    modifier: Modifier = Modifier,
) {
    when (uiExcludedLocation) {
        is ExcludedLocationsViewModel.ExcludedLocationUiItem.Location.City -> {
            ExcludedLocationPinIcon(
                modifier = modifier,
                contentDescription = stringResource(id = R.string.settings_excluded_locations_accessibility_label_city)
            )
        }

        is ExcludedLocationsViewModel.ExcludedLocationUiItem.Location.Country -> {
            val countryContentDescription = stringResource(id = R.string.settings_excluded_locations_accessibility_label_country)

            Flag(
                modifier = modifier.semantics {
                    contentDescription = countryContentDescription
                },
                exitCountry = uiExcludedLocation.countryId,
            )
        }

        is ExcludedLocationsViewModel.ExcludedLocationUiItem.Location.State -> {
            ExcludedLocationPinIcon(
                modifier = modifier,
                contentDescription = stringResource(id = R.string.settings_excluded_locations_accessibility_label_state)
            )
        }
    }
}

@Composable
private fun ExcludedLocationPinIcon(
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    // We provide width and height to match Flag size so the icon is properly aligned
    Icon(
        modifier = modifier.size(width = 30.dp, height = 24.dp),
        painter = painterResource(id = CoreR.drawable.ic_proton_map_pin),
        contentDescription = contentDescription,
    )
}

@Preview
@Composable
private fun ExcludedLocationPinIconPreview(
    @PreviewParameter(PreviewBooleanProvider::class) isDark: Boolean,
) {
    ProtonVpnPreview(isDark = isDark) {
        ExcludedLocationPinIcon(
            contentDescription = "content description"
        )
    }
}
