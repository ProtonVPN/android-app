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

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.base.ui.ProtonOutlinedTextField
import com.protonvpn.android.redesign.base.ui.previews.PreviewBooleanProvider
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun ExcludedLocationsSearchTextField(
    onSearchQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialSearchQuery: String = "",
) {
    var searchValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(value = TextFieldValue(text = initialSearchQuery))
    }

    ProtonOutlinedTextField(
        modifier = modifier,
        value = searchValue,
        onValueChange = { newSearchValue ->
            searchValue = newSearchValue

            onSearchQueryChanged(searchValue.text)
        },
        placeholderText = stringResource(id = R.string.server_search_menu_title),
        leadingIcon = {
            Icon(
                painter = painterResource(id = CoreR.drawable.ic_proton_magnifier),
                contentDescription = null,
                tint = ProtonTheme.colors.iconWeak,
            )
        },
        trailingIcon = if (searchValue.text.isEmpty()) {
            null
        } else {
            {
                Icon(
                    modifier = Modifier.clickable {
                        searchValue = TextFieldValue(text = initialSearchQuery)

                        onSearchQueryChanged(initialSearchQuery)
                    },
                    painter = painterResource(id = CoreR.drawable.ic_proton_cross),
                    contentDescription = null,
                    tint = ProtonTheme.colors.iconNorm,
                )
            }
        },
        singleLine = true,
        cursorColor = ProtonTheme.colors.interactionNorm,
    )
}

@Preview
@Composable
private fun ExcludedLocationsSearchTextFieldEmptyPreview(
    @PreviewParameter(PreviewBooleanProvider::class) isDark: Boolean,
) {
    ProtonVpnPreview(isDark = isDark) {
        ExcludedLocationsSearchTextField(
            modifier = Modifier,
            onSearchQueryChanged = {},
        )
    }
}

@Preview
@Composable
private fun ExcludedLocationsSearchTextFieldFilledPreview(
    @PreviewParameter(PreviewBooleanProvider::class) isDark: Boolean,
) {
    ProtonVpnPreview(isDark = isDark) {
        ExcludedLocationsSearchTextField(
            modifier = Modifier,
            initialSearchQuery = "Search query",
            onSearchQueryChanged = {},
        )
    }
}
