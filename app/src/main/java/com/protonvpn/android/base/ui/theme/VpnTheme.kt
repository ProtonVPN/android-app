/*
 * Copyright (c) 2023 Proton AG
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

package com.protonvpn.android.redesign.base.ui.theme

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.ProtonTheme3
import me.proton.core.compose.theme.defaultUnspecified
import me.proton.core.compose.theme.isNightMode

@Composable
fun VpnTheme(isDark: Boolean = isNightMode(), content: @Composable () -> Unit) {
    ProtonTheme(isDark = isDark) {
        ProtonTheme3(isDark = isDark) {
            val view = LocalView.current
            if (!view.isInEditMode) {
                val bottomBarColor = ProtonTheme.colors.backgroundSecondary
                val systemUiController = rememberSystemUiController()
                SideEffect {
                    val window = (view.context as Activity).window
                    window.navigationBarColor = bottomBarColor.toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                    systemUiController.setStatusBarColor(color = Color.Transparent, darkIcons = !isDark)
                }
            }
            CompositionLocalProvider(
                LocalContentColor provides ProtonTheme.colors.textNorm,
                LocalTextStyle provides ProtonTheme.typography.defaultUnspecified
            ) {
                content()
            }
        }
    }
}

@Composable
fun LightAndDarkPreview(content: @Composable (isDark: Boolean) -> Unit) {
    Column {
        VpnTheme(isDark = false) { content(false) }
        Spacer(modifier = Modifier.height(10.dp))
        VpnTheme(isDark = true) { content(true) }
    }
}
