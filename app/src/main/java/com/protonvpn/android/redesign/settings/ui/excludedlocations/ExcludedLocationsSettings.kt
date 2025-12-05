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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.collectAsEffect
import com.protonvpn.android.redesign.search.ui.EmptySearchResult
import com.protonvpn.android.redesign.settings.ui.SubSettingWithLazyContent
import com.protonvpn.android.ui.progress.ScreenContentLoading
import me.proton.core.presentation.utils.currentLocale

@Composable
fun ExcludedLocationsSettings(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SubSettingWithLazyContent(
        modifier = modifier,
        title = stringResource(id = R.string.settings_excluded_locations_title),
        onClose = onClose,
    ) {
        ExcludedLocationsContent(
            modifier = Modifier.fillMaxSize(),
            onExcludedLocationAdded = onClose,
        )
    }
}

@Composable
private fun ExcludedLocationsContent(
    onExcludedLocationAdded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<ExcludedLocationsViewModel>()

    val viewState = viewModel.viewStateFlow.collectAsStateWithLifecycle().value

    val locale = LocalConfiguration.current.currentLocale()

    LaunchedEffect(key1 = locale) {
        viewModel.onLocaleChanged(newLocale = locale)
    }

    viewModel.eventsFlow.collectAsEffect { event ->
        when (event) {
            ExcludedLocationsViewModel.Event.OnExcludedLocationAdded -> {
                onExcludedLocationAdded()
            }
        }
    }

    viewState
        ?.let { state ->
            Column(
                modifier = modifier.imePadding(),
                verticalArrangement = Arrangement.spacedBy(space = 16.dp),
            ) {
                ExcludedLocationsSearchTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            top = 16.dp,
                            end = 16.dp,
                            bottom = 8.dp,
                        ),
                    onSearchQueryChanged = viewModel::onSearchQueryChanged,
                )

                when (state) {
                    is ExcludedLocationsViewModel.ViewState.LocationResults -> {
                        ExcludedLocationsList(
                            modifier = Modifier.fillMaxSize(),
                            searchQuery = state.searchQuery,
                            uiItems = state.uiItems,
                            onExcludedLocationClick = viewModel::onExcludedLocationSelected,
                        )
                    }

                    ExcludedLocationsViewModel.ViewState.NoLocationResults -> {
                        EmptySearchResult(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        ?: ScreenContentLoading(modifier = modifier)
}
