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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.redesign.base.ui.optional
import com.protonvpn.android.redesign.countries.ui.MatchedText
import com.protonvpn.android.redesign.vpn.ui.label
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun ExcludedLocationsList(
    searchQuery: String,
    uiItems: List<ExcludedLocationsViewModel.ExcludedLocationUiItem>,
    onExcludedLocationClick: (ExcludedLocationsViewModel.ExcludedLocationUiItem.Location) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
    ) {
        items(
            items = uiItems,
            key = { uiItem -> uiItem.id },
        ) { uiItem ->
            when (uiItem) {
                is ExcludedLocationsViewModel.ExcludedLocationUiItem.Header -> {
                    ExcludedLocationsSectionHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 16.dp),
                        headerUiItem = uiItem,
                    )
                }

                is ExcludedLocationsViewModel.ExcludedLocationUiItem.Location.Country -> {
                    ExcludedLocationsCountryRow(
                        modifier = Modifier.fillMaxWidth(),
                        isSearching = searchQuery.isNotEmpty(),
                        countryUiItem = uiItem,
                        onExcludedLocationClick = onExcludedLocationClick,
                    )
                }

                is ExcludedLocationsViewModel.ExcludedLocationUiItem.Location.City,
                is ExcludedLocationsViewModel.ExcludedLocationUiItem.Location.State -> {
                    ExcludedLocationsRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExcludedLocationClick(uiItem) }
                            .padding(all = 16.dp),
                        isSearching = searchQuery.isNotEmpty(),
                        locationUiItem = uiItem,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExcludedLocationsSectionHeader(
    headerUiItem: ExcludedLocationsViewModel.ExcludedLocationUiItem.Header,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier,
        text = stringResource(id = headerUiItem.textResId),
        color = ProtonTheme.colors.textWeak,
        style = ProtonTheme.typography.body2Regular,
    )
}

@Composable
private fun ExcludedLocationsCountryRow(
    isSearching: Boolean,
    countryUiItem: ExcludedLocationsViewModel.ExcludedLocationUiItem.Location.Country,
    onExcludedLocationClick: (ExcludedLocationsViewModel.ExcludedLocationUiItem.Location) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable(key = countryUiItem.countryId.countryCode) {
        mutableStateOf(value = false)
    }

    val chevronTargetDegrees = if (isExpanded) -180f else 0f

    val chevronRotationDegrees by animateFloatAsState(
        targetValue = chevronTargetDegrees,
        animationSpec = tween(),
        label = "Chevron rotation animation",
    )

    Column(
        modifier = modifier.optional(
            predicate = { isExpanded },
            modifier = Modifier.background(color = ProtonTheme.colors.backgroundSecondary),
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExcludedLocationClick(countryUiItem) }
                .padding(
                    horizontal = 16.dp,
                    vertical = if (countryUiItem.isExpandable) 6.dp else 16.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExcludedLocationsRow(
                modifier = Modifier.weight(weight = 1f, fill = true),
                isSearching = isSearching,
                locationUiItem = countryUiItem,
            )

            if (countryUiItem.isExpandable) {
                IconButton(
                    onClick = {
                        isExpanded = !isExpanded
                    }
                ) {
                    Icon(
                        modifier = Modifier.rotate(degrees = chevronRotationDegrees),
                        painter = painterResource(id = CoreR.drawable.ic_proton_chevron_down),
                        contentDescription = null,
                        tint = ProtonTheme.colors.iconNorm,
                    )
                }
            }
        }

        AnimatedVisibility(
            modifier = Modifier.fillMaxWidth(),
            visible = countryUiItem.isExpandable && isExpanded,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                countryUiItem.countryCities.forEach { cityUiItem ->
                    ExcludedLocationsRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExcludedLocationClick(cityUiItem) }
                            .padding(all = 16.dp),
                        isSearching = isSearching,
                        locationUiItem = cityUiItem,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExcludedLocationsRow(
    isSearching: Boolean,
    locationUiItem: ExcludedLocationsViewModel.ExcludedLocationUiItem.Location,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExcludedLocationIcon(uiExcludedLocation = locationUiItem)

        locationUiItem.textMatch
            ?.let { match ->
                MatchedText(
                    color = if (isSearching) ProtonTheme.colors.textWeak else ProtonTheme.colors.textNorm,
                    match = match,
                )
            }
            ?: Text(
                text = locationUiItem.countryId.label(),
                style = ProtonTheme.typography.body1Regular,
            )
    }
}
