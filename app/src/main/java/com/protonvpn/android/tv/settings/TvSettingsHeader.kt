/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.tv.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.protonvpn.android.tv.ui.TvUiConstants
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun TvSettingsHeader(
    title: String,
    @DrawableRes imageRes: Int,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Image(
            painterResource(id = imageRes),
            contentDescription = null,
            modifier = Modifier.padding(end = 24.dp)
        )
        Text(title, style = ProtonTheme.typography.hero)
    }
}

@Composable
fun TvSettingsHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        title,
        style = ProtonTheme.typography.hero,
        modifier = modifier.padding(horizontal = TvUiConstants.SelectionPaddingHorizontal, vertical = 24.dp)
    )
}
