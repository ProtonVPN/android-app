/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.tv.countries.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import com.protonvpn.android.R
import me.proton.core.compose.theme.ProtonTheme

@Composable
internal fun CountriesGrid(
    countries: List<CountryGridUiItem>,
    onCountryClick: (CountryGridUiItem) -> Unit,
    onCountryLongClick: (CountryGridUiItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    var focusedKey by remember { mutableStateOf<Any?>(null) }
    // Rows that don't contain the focused tile are dimmed.
    val focusedRowKeys: Set<Any> by remember {
        derivedStateOf {
            val visibleItems = gridState.layoutInfo.visibleItemsInfo
            val focusedRow = visibleItems.firstOrNull { it.key == focusedKey }?.row
            if (focusedRow == null) emptySet()
            else visibleItems.filter { it.row == focusedRow }.mapTo(HashSet()) { it.key }
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.FixedSize(CountryGridTileWidth),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        // Distance from the navigation drawer to the tiles. It's also needed because focused tiles are scaled up and
        // draw outside their cells, it keeps them from being clipped.
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier,
    ) {
        val firstCountryKey = countries.firstOrNull()?.countryId?.countryCode
        if (firstCountryKey != null) {
            item(key = "all_countries_title", span = { GridItemSpan(maxLineSpan) }) {
                // The title follows the opacity of the row below it.
                RowTitle(
                    stringResource(R.string.tv_countries_grid_all_countries_title),
                    isRowFocused = firstCountryKey in focusedRowKeys,
                )
            }
        }
        items(countries, key = { it.countryId.countryCode }) { country ->
            val key = country.countryId.countryCode
            val alpha by animateFloatAsState(
                targetValue = rowAlpha(key in focusedRowKeys),
                label = "country row alpha"
            )
            CountryGridTile(
                item = country,
                onClick = { onCountryClick(country) },
                onLongClick = { onCountryLongClick(country) },
                modifier = Modifier
                    // The focused tile is scaled up and overlaps its neighbors.
                    .zIndex(if (key == focusedKey) 1f else 0f)
                    .padding(bottom = 8.dp)
                    .graphicsLayer {
                        this.alpha = alpha
                        // The default strategy renders translucent content offscreen, clipped to the tile's bounds,
                        // which would cut off the scaled-up focus border and text while the alpha animates.
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) focusedKey = key
                        else if (focusedKey == key) focusedKey = null
                    }
            )
        }
    }
}

@Composable
private fun RowTitle(text: String, isRowFocused: Boolean, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(rowAlpha(isRowFocused), label = "row title alpha")
    Text(
        text,
        style = ProtonTheme.typography.body1Medium,
        color = ProtonTheme.colors.textNorm,
        modifier = modifier
            .semantics { heading() }
            .alpha(alpha)
            // Aligns the title with the flags.
            .padding(start = 10.dp, top = 5.dp, bottom = 5.dp)
    )
}

fun rowAlpha(isFocused: Boolean) = if (isFocused) 1f else 0.5f
