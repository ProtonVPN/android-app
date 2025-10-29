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

package com.protonvpn.android.tv.buttons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Text
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.tv.settings.ProtonTvFocusableSurface
import me.proton.core.compose.theme.ProtonTheme
import androidx.compose.material3.ButtonDefaults as CoreButtonDefaults

@Composable
fun TvProtonTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProtonTvFocusableSurface(
        onClick = onClick,
        color = { Color.Transparent },
        focusedColor = { ProtonTheme.colors.backgroundNorm },
        shape = ProtonTheme.shapes.medium,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CoreButtonDefaults.MinHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = ProtonTheme.typography.body1Medium,
                color = ProtonTheme.colors.textAccent,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewTvProtonTextButton() {
    ProtonVpnPreview(isDark = true) {
        TvProtonTextButton(
            text = "Button text",
            onClick = {},
        )
    }
}
