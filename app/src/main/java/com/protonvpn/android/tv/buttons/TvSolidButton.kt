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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.base.ui.previews.PreviewBooleanProvider
import com.protonvpn.android.tv.settings.ProtonTvFocusableSurface
import me.proton.core.compose.theme.ProtonTheme
import androidx.compose.material3.ButtonDefaults as CoreButtonDefaults

@Composable
fun TvSolidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    ProtonTvFocusableSurface(
        onClick = {
            if (!isLoading) {
                onClick()
            }
        },
        color = { ProtonTheme.colors.interactionNorm },
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = text,
                    style = ProtonTheme.typography.body1Medium,
                    color = ProtonTheme.colors.textNorm,
                )

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(size = 16.dp),
                        color = ProtonTheme.colors.textNorm,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewTvSolidButton(
    @PreviewParameter(PreviewBooleanProvider::class) isLoading: Boolean,
) {
    ProtonVpnPreview(isDark = true) {
        TvSolidButton(
            text = "Button text",
            isLoading = isLoading,
            onClick = {},
        )
    }
}
