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

package com.protonvpn.android.redesign.settings.ui.connectionpreferences

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.LabelBadge
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.ConnectIntentIcon
import com.protonvpn.android.redesign.base.ui.Flag
import com.protonvpn.android.redesign.recents.data.DefaultConnection
import com.protonvpn.android.redesign.settings.ui.IconRecent
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel.SettingViewState
import com.protonvpn.android.redesign.settings.ui.excludedlocations.ExcludedLocationIcon
import com.protonvpn.android.redesign.settings.ui.excludedlocations.ExcludedLocationsViewModel.ExcludedLocationUiItem
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.label
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun ConnectionPreferencesPaidContent(
    state: SettingViewState.ConnectionPreferencesState,
    onDefaultConnectionClick: () -> Unit,
    onExcludeLocationClick: () -> Unit,
    onDeleteExcludedLocationClick: (ExcludedLocationUiItem.Location) -> Unit,
    onExcludedLocationsFeatureDiscovered: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(key1 = Unit) {
        onDispose {
            onExcludedLocationsFeatureDiscovered()
        }
    }

    val sectionModifier = remember {
        Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 8.dp,
            )
    }

    LazyColumn(modifier = modifier) {
        item(key = "default_connection_key") {
            with(receiver = state.defaultConnectionPreferences) {
                ConnectionPreferencesSection(
                    modifier = sectionModifier,
                    onClick = onDefaultConnectionClick,
                    titleText = stringResource(id = R.string.settings_default_connection_title),
                    content = {
                        ConnectionPreferencesDefaultConnectionIcon(
                            defaultConnection = defaultConnection,
                            connectIntentPrimaryLabel = connectIntentPrimaryLabel,
                        )

                        Text(
                            modifier = Modifier.weight(weight = 1f, fill = true),
                            text = if (predefinedTitle == null) {
                                connectIntentPrimaryLabel?.label().orEmpty()
                            } else {
                                stringResource(id = predefinedTitle)
                            }
                        )
                    }
                )
            }
        }

        item(key = "exclude_locations_key") {
            with(receiver = state.excludeLocationsPreferences) {
                ConnectionPreferencesSection(
                    modifier = sectionModifier,
                    titleText = stringResource(id = R.string.settings_excluded_locations_title),
                    descriptionText = stringResource(id = R.string.settings_excluded_locations_description),
                    isEnabled = canSelectLocations,
                    onClick = onExcludeLocationClick,
                    showNewLabel = !isFeatureDiscovered,
                    content = {
                        val (textResId, textColor) = if (canSelectLocations) {
                            R.string.settings_excluded_locations_selection_hint to ProtonTheme.colors.textWeak
                        } else {
                            R.string.settings_excluded_locations_unavailable to ProtonTheme.colors.textHint
                        }

                        Text(
                            modifier = Modifier.weight(weight = 1f, fill = true),
                            text = stringResource(id = textResId),
                            color = textColor,
                        )
                    }
                )
            }
        }

        items(
            items = state.excludeLocationsPreferences.excludedLocationUiItems,
            key = { excludedLocationUiItem -> excludedLocationUiItem.id },
        ) { excludedLocationUiItem ->
            ConnectionPreferencesExcludedLocationRowItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = stringResource(id = R.string.settings_excluded_locations_accessibility_label_deletion),
                        onClick = { onDeleteExcludedLocationClick(excludedLocationUiItem) },
                    )
                    .padding(
                        horizontal = 16.dp,
                        vertical = 12.dp,
                    ),
                excludedLocationUiItem = excludedLocationUiItem,
            )
        }
    }
}

@Composable
private fun ConnectionPreferencesSection(
    titleText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    descriptionText: String? = null,
    showNewLabel: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = titleText,
                style = ProtonTheme.typography.body1Regular,
            )

            if (showNewLabel) {
                LabelBadge(
                    text = stringResource(R.string.settings_new_label_badge),
                    textColor = ProtonTheme.colors.notificationWarning,
                    borderColor = ProtonTheme.colors.notificationWarning,
                )
            }
        }

        descriptionText?.let { text ->
            Text(
                text = text,
                color = ProtonTheme.colors.textWeak,
                style = ProtonTheme.typography.body2Regular,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = ProtonTheme.shapes.large)
                .background(color = ProtonTheme.colors.backgroundSecondary)
                .clickable(
                    enabled = isEnabled,
                    onClick = onClick,
                )
                .padding(all = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            content()

            Icon(
                painter = painterResource(id = CoreR.drawable.ic_proton_chevron_right),
                contentDescription = null,
                tint = if (isEnabled) ProtonTheme.colors.iconNorm else ProtonTheme.colors.iconHint,
            )
        }
    }
}

@Composable
private fun ConnectionPreferencesDefaultConnectionIcon(
    defaultConnection: DefaultConnection,
    connectIntentPrimaryLabel: ConnectIntentPrimaryLabel?,
    modifier: Modifier = Modifier,
) {
    when (defaultConnection) {
        DefaultConnection.FastestConnection -> {
            Flag(
                modifier = modifier,
                exitCountry = CountryId.fastest,
            )
        }

        DefaultConnection.LastConnection -> {
            IconRecent(
                modifier = modifier,
            )
        }

        is DefaultConnection.Recent -> {
            connectIntentPrimaryLabel?.let { label ->
                ConnectIntentIcon(
                    modifier = modifier,
                    label = label,
                )
            }
        }
    }
}

@Composable
private fun ConnectionPreferencesExcludedLocationRowItem(
    excludedLocationUiItem: ExcludedLocationUiItem.Location,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExcludedLocationIcon(uiExcludedLocation = excludedLocationUiItem)

        Text(
            modifier = Modifier.weight(weight = 1f, fill = true),
            text = excludedLocationUiItem.textMatch?.fullText ?: excludedLocationUiItem.countryId.label(),
            style = ProtonTheme.typography.body2Regular,
        )

        Icon(
            painter = painterResource(id = CoreR.drawable.ic_proton_minus_circle_filled),
            contentDescription = null,
            tint = ProtonTheme.colors.iconNorm,
        )
    }
}
