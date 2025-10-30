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

package com.protonvpn.android.base.ui.tooltips

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.base.ui.previews.PreviewBooleanProvider
import kotlinx.coroutines.launch
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnIconTooltip(
    @DrawableRes iconResId: Int,
    iconTint: Color,
    tooltipText: String,
    modifier: Modifier = Modifier,
    iconContentDescription: String? = null,
    isPersistent: Boolean = false,
) {
    val coroutineScope = rememberCoroutineScope()

    val tooltipState = rememberTooltipState(isPersistent = isPersistent)

    TooltipBox(
        modifier = modifier,
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip(
                modifier = Modifier
                    .fillMaxWidth(fraction = 0.8f)
                    .border(
                        width = 1.dp,
                        shape = ProtonTheme.shapes.large,
                        color = ProtonTheme.colors.separatorNorm,
                    ),
                contentColor = ProtonTheme.colors.textWeak,
                containerColor = ProtonTheme.colors.backgroundSecondary,
                shape = ProtonTheme.shapes.large,
            ) {
                Text(
                    modifier = Modifier.padding(all = 16.dp),
                    text = tooltipText,
                    style = ProtonTheme.typography.captionRegular,
                )
            }
        },
        state = tooltipState,
    ) {
        IconButton(onClick = {
            coroutineScope.launch {
                tooltipState.show()
            }
        }) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = iconContentDescription,
                tint = iconTint,
            )
        }
    }
}

@Preview
@Composable
private fun VpnIconTooltipPreview(
    @PreviewParameter(PreviewBooleanProvider::class) isDark: Boolean,
) {
    ProtonVpnPreview(isDark = isDark) {
        VpnIconTooltip(
            tooltipText = "Tooltip text",
            iconResId = CoreR.drawable.ic_info_circle,
            iconTint = ProtonTheme.colors.iconWeak,
        )
    }
}
