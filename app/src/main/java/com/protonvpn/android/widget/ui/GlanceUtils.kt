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

package com.protonvpn.android.widget.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign

@Composable
fun GlanceButton(
    @StringRes label: Int,
    action: Action,
    secondary: Boolean = false,
    modifier: GlanceModifier = GlanceModifier.fillMaxWidth(),
) {
    // Using Text as there are multiple issues with Button:
    // - text is not center-aligned on old android (API 29)
    // - can't set maxLines
    // - can't set custom corner radius (though setting corner radius will work only on API 31+)
    val color =
        if (secondary) ProtonGlanceTheme.colors.onInteractionSecondary else ProtonGlanceTheme.colors.onInteractionNorm
    val backgroundResource = if (secondary) {
        ProtonGlanceTheme.resources.buttonBackgroundInteractionSecondary
    } else {
        ProtonGlanceTheme.resources.buttonBackgroundInteractionNorm
    }
    Text(
        text = glanceStringResource(label),
        style = ProtonGlanceTheme.typography.defaultOnInteraction.copy(color = color, textAlign = TextAlign.Center),
        maxLines = 1,
        modifier = modifier
            .background(ImageProvider(backgroundResource))
            .clickable(action)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    )
}

@Composable
fun glanceStringResource(@StringRes id: Int, vararg arguments: Any): String =
    LocalContext.current.getString(id, *arguments)
