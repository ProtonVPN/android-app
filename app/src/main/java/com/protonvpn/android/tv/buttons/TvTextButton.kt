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

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.tv.settings.ProtonTvFocusableSurface
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun TvTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProtonTvFocusableSurface(
        modifier = modifier,
        color = { Color.Transparent },
        focusedColor = { ProtonTheme.colors.backgroundNorm },
        shape = CircleShape,
        onClick = onClick,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            text = text,
            color = ProtonTheme.colors.textNorm,
            style = ProtonTheme.typography.body2Medium,
        )
    }
}

@Preview
@Composable
private fun PreviewTvTextButton() {
    ProtonVpnPreview(isDark = true) {
        TvTextButton(
            text = "Button text",
            onClick = {},
        )
    }
}
