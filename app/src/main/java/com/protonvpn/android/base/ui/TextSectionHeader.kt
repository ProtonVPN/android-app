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

package com.protonvpn.android.base.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.proton.core.compose.theme.ProtonTheme

/**
 * Commonly used header with standard top and bottom paddings.
 */
@Composable
fun TextSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 8.dp
) {
    Text(
        text = text,
        style = ProtonTheme.typography.body2Medium,
        color = ProtonTheme.colors.textAccent,
        modifier = modifier
            .padding(top = 24.dp, bottom = bottomPadding)
    )
}
