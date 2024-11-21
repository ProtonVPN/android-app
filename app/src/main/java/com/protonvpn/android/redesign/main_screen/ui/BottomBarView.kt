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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.main_screen.ui.nav.MainTarget
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionStrongUnspecified
import me.proton.core.presentation.R as CoreR

@Composable
fun BottomBarView(
    modifier: Modifier = Modifier,
    showGateways: Boolean,
    showProfiles: Boolean,
    selectedTarget: MainTarget? = MainTarget.Home,
    notificationDots: Set<MainTarget> = emptySet(),
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
        MainTarget.entries.forEach { target ->
            if (target == MainTarget.Gateways && !showGateways ||
                    target == MainTarget.Profiles && !showProfiles)
                return@forEach

            val isSelected = target == selectedTarget
            val label = stringResource(id = target.labelRes())
            val notificationBadge: @Composable BoxScope.() -> Unit = if (notificationDots.contains(target)) {
                { Badge(containerColor = ProtonTheme.colors.notificationError) }
            } else {
                {}
            }
            NavigationBarItem(
                modifier = Modifier.alignByBaseline(),
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
                    BadgedBox(
                        badge = notificationBadge
                    ) {
                        Icon(
                            painterResource(id = target.getIcon(isSelected)),
                            contentDescription = null,
                        )
                    }
                },
                label = {
                    Text(
                        text = label,
                        style = ProtonTheme.typography.captionStrongUnspecified,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            )
        }
    }
}

@StringRes
private fun MainTarget.labelRes(): Int = when (this) {
    MainTarget.Home -> R.string.bottom_nav_home
    MainTarget.Gateways -> R.string.bottom_nav_gateways
    MainTarget.Profiles -> R.string.bottom_nav_profiles
    MainTarget.Countries -> R.string.bottom_nav_countries
    MainTarget.Settings -> R.string.botton_nav_settings
}

@DrawableRes
private fun MainTarget.getIcon(selected: Boolean): Int = when (this) {
    MainTarget.Home ->
        if (selected) CoreR.drawable.ic_proton_house_filled else CoreR.drawable.ic_proton_house
    MainTarget.Profiles ->
        //TODO: use core asset for _filled when available
        if (selected) R.drawable.ic_proton_window_terminal_filled else CoreR.drawable.ic_proton_window_terminal
    MainTarget.Gateways ->
        if (selected) CoreR.drawable.ic_proton_servers_filled else CoreR.drawable.ic_proton_servers
    MainTarget.Countries ->
        if (selected) CoreR.drawable.ic_proton_earth_filled else CoreR.drawable.ic_proton_earth
    MainTarget.Settings ->
        if (selected) CoreR.drawable.ic_proton_cog_wheel_filled else CoreR.drawable.ic_proton_cog_wheel
}

@Preview()
@Composable
fun BottomBarPreviewDark() {
    ProtonVpnPreview {
        BottomBarView(selectedTarget = MainTarget.Home, showGateways = true, showProfiles = true) {}
    }
}
