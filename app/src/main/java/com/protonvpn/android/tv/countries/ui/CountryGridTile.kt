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

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnTvPreview
import com.protonvpn.android.base.ui.theme.backgroundSelected
import com.protonvpn.android.base.ui.vpnGreen
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.tv.ui.ActiveDotTv
import com.protonvpn.android.tv.ui.PlusBadgeTv
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

internal val CountryGridTileWidth = 125.dp
private val FlagSize = DpSize(105.dp, 70.dp)
private val FocusBorderWidth = 2.dp

@Composable
fun CountryGridTile(
    item: CountryGridUiItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusProgress by animateFloatAsState(if (isFocused) 1f else 0f, label = "country tile focus")
    CountryGridTileContent(
        item = item,
        focusProgress = focusProgress,
        modifier = modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClickLabel = item.clickLabel()?.let { stringResource(it) },
            onLongClickLabel = stringResource(R.string.accessibility_action_show_connection_options),
            onLongClick = onLongClick,
            onClick = onClick,
        ),
    )
}

// Matches the click handling in TvHomeViewModel.onCountryClick.
@StringRes
private fun CountryGridUiItem.clickLabel(): Int? = when {
    status == CountryGridUiItem.Status.InMaintenance -> null
    !available -> R.string.accessibility_action_upgrade
    else -> R.string.accessibility_action_connect
}

// All focus effects are applied when drawing so that focus changes never affect the grid layout.
@Composable
internal fun CountryGridTileContent(
    item: CountryGridUiItem,
    focusProgress: Float,
    modifier: Modifier = Modifier,
) {
    // 1.2x when focused.
    val scale = 1f + 0.2f * focusProgress
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(CountryGridTileWidth)
            .padding(vertical = 16.dp)
    ) {
        CountryTileFlag(item, scale, focusProgress)
        Spacer(Modifier.height(17.dp))
        CountryTileText(
            item,
            focusProgress,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0f)
                // Pushes the scaled text down so that it keeps its distance from the scaled flag.
                translationY = 5.dp.toPx() * focusProgress
            }
        )
    }
}

@Composable
private fun CountryTileFlag(item: CountryGridUiItem, scale: Float, focusProgress: Float) {
    val flagAlpha = when {
        !item.available -> 0.3f
        item.status == CountryGridUiItem.Status.InMaintenance -> 0.5f
        else -> 1f
    }
    Box(
        modifier = Modifier
            .size(FlagSize)
            .focusBorder(ProtonTheme.colors.backgroundSelected, scale, focusProgress)
    ) {
        Image(
            painter = painterResource(item.flagResource),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = flagAlpha,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shape = RoundedCornerShape(10.dp)
                    clip = true
                }
        )
    }
}

// Draws the border around the flag scaled by [scale]. The border itself isn't scaled, it keeps its width and radius.
private fun Modifier.focusBorder(color: Color, scale: Float, focusProgress: Float) = drawBehind {
    if (focusProgress > 0f) {
        val strokeWidth = FocusBorderWidth.toPx()
        val outset = 4.dp.toPx()
        // The stroke is centered on the rectangle's outline.
        val borderSize = Size(
            size.width * scale + 2 * outset - strokeWidth,
            size.height * scale + 2 * outset - strokeWidth
        )
        drawRoundRect(
            color = color,
            topLeft = Offset((size.width - borderSize.width) / 2, (size.height - borderSize.height) / 2),
            size = borderSize,
            cornerRadius = CornerRadius(15.dp.toPx() - strokeWidth / 2),
            style = Stroke(strokeWidth),
            alpha = focusProgress,
        )
    }
}

@Composable
private fun CountryTileText(item: CountryGridUiItem, focusProgress: Float, modifier: Modifier = Modifier) {
    val isInMaintenance = item.status == CountryGridUiItem.Status.InMaintenance
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            item.countryName,
            style = ProtonTheme.typography.body2Regular,
            color = if (item.available && !isInMaintenance) ProtonTheme.colors.textNorm else ProtonTheme.colors.textWeak,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // Always laid out so that all tiles have the same height regardless of their status.
        // It fits both the Plus badge and a line of status text at any font scale.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.heightIn(min = 24.dp) // Height of the Plus badge.,
        ) {
            Text("", style = StatusTextStyle, modifier = Modifier.clearAndSetSemantics {})
            CountryTileStatus(item, focusProgress)
        }
    }
}

@Composable
private fun CountryTileStatus(item: CountryGridUiItem, focusProgress: Float) {
    when {
        item.status == CountryGridUiItem.Status.Connected ->
            StatusLabel(stringResource(R.string.tv_country_tile_connected), ProtonTheme.colors.vpnGreen) {
                ActiveDotTv()
            }
        item.status == CountryGridUiItem.Status.Connecting ->
            StatusLabel(stringResource(R.string.tv_country_tile_connecting), ProtonTheme.colors.vpnGreen) {
                ActiveDotTv()
            }
        !item.available ->
            PlusBadgeTv(Modifier.alpha(focusProgress))
        item.status == CountryGridUiItem.Status.InMaintenance ->
            StatusLabel(stringResource(R.string.tv_country_tile_maintenance), ProtonTheme.colors.textWeak) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_proton_wrench),
                    contentDescription = null,
                    tint = ProtonTheme.colors.iconWeak,
                    modifier = Modifier.size(12.dp),
                )
            }
        // A hint that there are several locations to choose from, a single city or state doesn't need the label.
        item.cityStateCount > 1 ->
            Text(
                pluralStringResource(
                    if (item.hasStates) R.plurals.tv_country_tile_states else R.plurals.tv_country_tile_cities,
                    item.cityStateCount,
                    item.cityStateCount
                ),
                style = StatusTextStyle,
                color = ProtonTheme.colors.textWeak,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(focusProgress),
            )
    }
}

@Composable
private fun StatusLabel(text: String, color: Color, trailingIcon: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // A single line keeps the tile height the same for all statuses.
        Text(
            text,
            style = StatusTextStyle,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        trailingIcon()
    }
}

private val StatusTextStyle: TextStyle
    @Composable get() = ProtonTheme.typography.captionRegular

private val previewItem = CountryGridUiItem(
    countryId = CountryId("no"),
    countryName = "Norway",
    flagResource = R.drawable.flag_tv_no,
    status = CountryGridUiItem.Status.Regular,
    available = true,
    tier = 1,
    cityStateCount = 3,
    hasStates = false,
)

@Preview
@Composable
private fun CountryGridTileFocusedPreview() {
    ProtonVpnTvPreview {
        CountryGridTileContent(previewItem, focusProgress = 1f, modifier = Modifier.padding(16.dp))
    }
}

@Preview
@Composable
private fun CountryGridTileStatesFocusedPreview() {
    ProtonVpnTvPreview {
        CountryGridTileContent(
            previewItem.copy(
                countryId = CountryId("us"),
                countryName = "United States",
                flagResource = R.drawable.flag_tv_us,
                cityStateCount = 12,
                hasStates = true,
            ),
            focusProgress = 1f,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview
@Composable
private fun CountryGridTileConnectedPreview() {
    ProtonVpnTvPreview {
        CountryGridTileContent(previewItem.copy(status = CountryGridUiItem.Status.Connected), focusProgress = 0f)
    }
}

@Preview
@Composable
private fun CountryGridTileMaintenancePreview() {
    ProtonVpnTvPreview {
        CountryGridTileContent(previewItem.copy(status = CountryGridUiItem.Status.InMaintenance), focusProgress = 0f)
    }
}

@Preview
@Composable
private fun CountryGridTilePlusFocusedPreview() {
    ProtonVpnTvPreview {
        CountryGridTileContent(
            previewItem.copy(available = false, tier = 2),
            focusProgress = 1f,
            modifier = Modifier.padding(16.dp)
        )
    }
}
