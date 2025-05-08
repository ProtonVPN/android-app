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

package com.protonvpn.android.base.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.protonvpn.android.base.ui.theme.VpnTheme
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.isNightMode

@Preview(name = "Default")
@Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
annotation class ProtonVpnPreview

@Composable
fun ProtonVpnPreview(
    isDark: Boolean = isNightMode(),
    addSurface: Boolean = true,
    surfaceColor: @Composable () -> Color = { ProtonTheme.colors.backgroundNorm },
    content: @Composable () -> Unit
) {
    @Composable
    fun OptionalSurface(surface: Boolean, content: @Composable () -> Unit) {
        if (surface) {
            Surface(
                color = surfaceColor(),
            ) {
                content()
            }
        } else {
            content()
        }
    }

    VpnTheme(isDark = isDark) {
        OptionalSurface(addSurface, content)
    }
}
