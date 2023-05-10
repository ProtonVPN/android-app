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

package com.protonvpn.android.redesign.main_screen.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.LightAndDarkPreview
import com.protonvpn.android.redesign.main_screen.ui.nav.MainTarget
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionStrongUnspecified

@Composable
fun BottomBarView(
    modifier: Modifier = Modifier,
    selectedTarget: MainTarget? = MainTarget.Home,
    navigateTo: (MainTarget) -> Unit
) {
    val bgColor = ProtonTheme.colors.backgroundSecondary
    val indicatorColor = ProtonTheme.colors.textAccent
        .copy(alpha = 0.32f).compositeOver(bgColor)
    NavigationBar(
        modifier,
        containerColor = bgColor,
        tonalElevation = 0.dp
    ) {
        MainTarget.values().forEach { target ->
            val isSelected = target == selectedTarget
            val label = stringResource(id = target.labelRes())
            NavigationBarItem(
                selected = isSelected,
                onClick = { navigateTo(target) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ProtonTheme.colors.textAccent,
                    unselectedIconColor = ProtonTheme.colors.textWeak,
                    selectedTextColor = ProtonTheme.colors.textAccent,
                    unselectedTextColor = ProtonTheme.colors.textWeak,
                    indicatorColor = indicatorColor,
                ),
                icon = {
                    Icon(
                        painterResource(id = target.getIcon(isSelected)),
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        text = label,
                        style = ProtonTheme.typography.captionStrongUnspecified,
                    )
                }
            )
        }
    }
}

@StringRes
private fun MainTarget.labelRes(): Int = when (this) {
    MainTarget.Home -> R.string.bottom_nav_home
    MainTarget.Countries -> R.string.bottom_nav_countries
    MainTarget.Settings -> R.string.botton_nav_settings
}

@DrawableRes
private fun MainTarget.getIcon(selected: Boolean): Int = when (this) {
    MainTarget.Home ->
        if (selected) R.drawable.ic_proton_house_filled else R.drawable.ic_proton_house
    MainTarget.Countries ->
        if (selected) R.drawable.ic_proton_earth_filled else R.drawable.ic_proton_earth
    MainTarget.Settings ->
        if (selected) R.drawable.ic_proton_cog_wheel_filled else R.drawable.ic_proton_cog_wheel
}

@Preview()
@Composable
fun BottomBarPreviewDark() {
    LightAndDarkPreview { darkMode ->
        BottomBarView(selectedTarget = MainTarget.Home) {}
    }
}
