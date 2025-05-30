/*
 * Copyright (c) 2023 Proton AG
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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.protonvpn.android.redesign.search.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.TopAppBarBackIcon
import com.protonvpn.android.redesign.base.ui.InfoSheetState
import com.protonvpn.android.redesign.countries.ui.FiltersRow
import com.protonvpn.android.redesign.countries.ui.ServerGroupItemsList
import com.protonvpn.android.redesign.countries.ui.ServerGroupsMainScreenState
import com.protonvpn.android.redesign.countries.ui.ServerGroups
import com.protonvpn.android.redesign.countries.ui.ServerGroupsActions
import com.protonvpn.android.redesign.home_screen.ui.ShowcaseRecents
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.headlineNorm
import me.proton.core.presentation.R as CoreR

@Composable
fun SearchRoute(
    onBackIconClick: () -> Unit,
    onNavigateToHomeOnConnect: (ShowcaseRecents) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ProtonTheme.colors.backgroundNorm)
            .navigationBarsPadding()
            .imePadding()
    ) {
        val viewModel: SearchViewModel = hiltViewModel()
        val focusRequester = remember { FocusRequester() }

        val currentQuery = viewModel.searchQueryFlow.collectAsStateWithLifecycle(initialValue = null).value
            ?: return

        Column {
            SearchableTopAppBar(
                searchQuery = currentQuery,
                onSearchQueryChange = { viewModel.setQuery(it) },
                onCloseClicked = onBackIconClick,
                focusRequester = focusRequester
            )

            val mainState = viewModel.stateFlow.collectAsStateWithLifecycle().value
            val subScreenState = viewModel.subScreenStateFlow.collectAsStateWithLifecycle().value
            val serverGroupsActions = ServerGroupsActions(
                setLocale = { viewModel.localeFlow.value = it },
                onNavigateBack = viewModel::onNavigateBack,
                onClose = viewModel::onClose,
                onItemOpen = viewModel::onItemOpen,
                onItemConnect = viewModel::onItemConnect
            )
            ServerGroups(
                mainState,
                subScreenState,
                onNavigateToHomeOnConnect = onNavigateToHomeOnConnect,
                actions = serverGroupsActions,
            ) { mainState, infoSheetState ->
                val modifier = Modifier.weight(1f).fillMaxWidth()
                when (mainState) {
                    is SearchViewState.ZeroScreen ->
                        SearchZeroScreen(modifier)

                    is SearchViewState.Result ->
                        ResultScreen(serverGroupsActions, mainState.result, onNavigateToHomeOnConnect, infoSheetState, modifier)
                }
            }
        }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
fun ResultScreen(
    serverGroupsActions: ServerGroupsActions,
    result: ServerGroupsMainScreenState,
    onNavigateToHomeOnConnect: (ShowcaseRecents) -> Unit,
    infoSheetState: InfoSheetState,
    modifier: Modifier
) {
    Column(modifier) {
        FiltersRow(
            buttonActions = result.filterButtons,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        if (result.items.isEmpty())
            EmptySearchResult(Modifier.fillMaxSize())
        else
            ServerGroupItemsList(serverGroupsActions, result, onNavigateToHomeOnConnect, infoSheetState)
    }
}

@Composable
fun SearchZeroScreen(modifier: Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = stringResource(id = R.string.search_zero_screen_title),
            style = ProtonTheme.typography.body2Regular,
            color = ProtonTheme.colors.textNorm
        )
        Spacer(modifier = Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12 .dp)) {
            SearchInitInfoRow(CoreR.drawable.ic_proton_earth, R.string.search_zero_screen_info_countries_title, R.string.search_zero_screen_info_countries_examples)
            SearchInitInfoRow(CoreR.drawable.ic_proton_map_pin, R.string.search_zero_screen_info_cities_title, R.string.search_zero_screen_info_cities_examples)
            SearchInitInfoRow(CoreR.drawable.ic_proton_servers, R.string.search_zero_screen_info_servers_title, R.string.search_zero_screen_info_servers_examples)
        }
    }
}

@Composable
fun SearchInitInfoRow(
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    @StringRes examplesRes: Int
) {
    Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = ProtonTheme.colors.iconWeak
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(id = titleRes),
                style = ProtonTheme.typography.body1Medium,
                color = ProtonTheme.colors.textNorm
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(id = examplesRes),
                style = ProtonTheme.typography.body2Regular,
                color = ProtonTheme.colors.textWeak
            )
        }
    }
}

@Composable
fun EmptySearchResult(modifier: Modifier) {
    Box(
        modifier = modifier.padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(id = R.string.search_empty_result_title),
                style = ProtonTheme.typography.headlineNorm
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.search_empty_result_subtitle),
                style = ProtonTheme.typography.body2Regular,
                color = ProtonTheme.colors.textWeak,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SearchableTopAppBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCloseClicked: () -> Unit,
    focusRequester: FocusRequester
) {
    TopAppBar(
        navigationIcon = { TopAppBarBackIcon(onCloseClicked) },
        modifier =  Modifier.semantics { testTagsAsResourceId = true },
        title = {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(text = stringResource(id = R.string.server_search_menu_title), color = ProtonTheme.colors.textWeak)
                },
                singleLine = true,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("searchInput"),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = stringResource(id = R.string.search_clear_query_content_description)
                            )
                        }
                    }
                }
            )
        }
    )
}
