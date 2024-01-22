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

package com.protonvpn.android.search

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ComposableSearchViewBinding
import kotlinx.coroutines.flow.Flow

@Composable
fun SearchRoute(onBackIconClick: () -> Unit) {
    val activity = LocalContext.current as AppCompatActivity
    val searchViewModel: SearchViewModel = hiltViewModel(viewModelStoreOwner = activity)

    SearchView(
        onBackIconClick = onBackIconClick,
        onQueryChange = { searchViewModel.setQuery(it) },
        queryFlow = searchViewModel.query.asFlow(),
        recentFlow = searchViewModel.queryFromRecents.asFlow()
    )
}

@Composable
fun SearchView(
    onBackIconClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    queryFlow: Flow<String>,
    recentFlow: Flow<String>,
) {
    val focusRequester = remember { FocusRequester() }
    val currentQuery = queryFlow.collectAsStateWithLifecycle(initialValue = "")
    val recentQuery = recentFlow.collectAsStateWithLifecycle(initialValue = "")
    Column {
        SearchableTopAppBar(
            searchQuery = currentQuery.value,
            recentQuery = recentQuery.value,
            onSearchQueryChange = onQueryChange,
            onCloseClicked = onBackIconClick,
            focusRequester = focusRequester
        )
        AndroidViewBinding(ComposableSearchViewBinding::inflate)
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableTopAppBar(
    searchQuery: String,
    recentQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCloseClicked: () -> Unit,
    focusRequester: FocusRequester
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(searchQuery)) }
    LaunchedEffect(textFieldValue) {
        onSearchQueryChange(textFieldValue.text)
    }
    if (recentQuery.isNotEmpty()) {
        textFieldValue = TextFieldValue(recentQuery, selection = TextRange(recentQuery.length))
    }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onCloseClicked) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(id = R.string.back))
            }
        },
        title = {
            TextField(
                value = textFieldValue,
                onValueChange = {
                    textFieldValue = it
                },
                placeholder = { Text(text = stringResource(id = R.string.server_search_menu_title)) },
                singleLine = true,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { textFieldValue = TextFieldValue("") }) {
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
