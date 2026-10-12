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
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.vpnGreen
import com.protonvpn.android.redesign.base.ui.ActiveDot
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

private val FlagSize = DpSize(105.dp, 70.dp)
private val FlagShape = RoundedCornerShape(8.dp)
private val FocusFrameShape = RoundedCornerShape(12.dp)
private val FocusFramePadding = 4.dp
private val FocusBorderWidth = 2.dp
private val TileWidth = FlagSize.width + (FocusFramePadding + FocusBorderWidth) * 2
private const val FOCUSED_SCALE = 1.1f
private const val UNAVAILABLE_FLAG_ALPHA = 0.3f
private const val MAINTENANCE_FLAG_ALPHA = 0.5f

@Composable
fun CountryGridTile(
    item: CountryGridUiItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) FOCUSED_SCALE else 1f, label = "country tile scale")
    val isInMaintenance = item.status == CountryGridUiItem.Status.InMaintenance
    val flagAlpha = when {
        !item.available -> UNAVAILABLE_FLAG_ALPHA
        isInMaintenance -> MAINTENANCE_FLAG_ALPHA
        else -> 1f
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(TileWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Image(
            painter = painterResource(item.flagResource),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = flagAlpha,
            modifier = Modifier
                .border(
                    FocusBorderWidth,
                    if (isFocused) ProtonTheme.colors.textNorm else Color.Transparent,
                    FocusFrameShape
                )
                .padding(FocusBorderWidth + FocusFramePadding)
                .size(FlagSize)
                .clip(FlagShape)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            item.countryName,
            style = ProtonTheme.typography.body2Regular,
            color = if (item.available && !isInMaintenance) ProtonTheme.colors.textNorm else ProtonTheme.colors.textWeak,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        CountryTileStatus(item, isFocused)
    }
}

@Composable
private fun CountryTileStatus(item: CountryGridUiItem, isFocused: Boolean) {
    when {
        item.status == CountryGridUiItem.Status.Connected ->
            StatusLabel(stringResource(R.string.tv_country_tile_connected), ProtonTheme.colors.vpnGreen) {
                ActiveDot()
            }
        item.status == CountryGridUiItem.Status.Connecting ->
            StatusLabel(stringResource(R.string.tv_country_tile_connecting), ProtonTheme.colors.vpnGreen) {
                ActiveDot()
            }
        !item.available ->
            // Always laid out so that focus changes don't resize the grid row.
            Image(
                painter = painterResource(R.drawable.vpn_plus_badge),
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .alpha(if (isFocused) 1f else 0f)
            )
        item.status == CountryGridUiItem.Status.InMaintenance ->
            StatusLabel(stringResource(R.string.tv_country_tile_maintenance), ProtonTheme.colors.textWeak) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_proton_wrench),
                    contentDescription = null,
                    tint = ProtonTheme.colors.iconWeak,
                    modifier = Modifier.size(12.dp),
                )
            }
    }
}

@Composable
private fun StatusLabel(text: String, color: Color, trailingIcon: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = ProtonTheme.typography.captionRegular, color = color)
        trailingIcon()
    }
}
