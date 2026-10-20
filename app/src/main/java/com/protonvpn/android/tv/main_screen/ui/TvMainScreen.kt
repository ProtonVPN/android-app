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

package com.protonvpn.android.tv.main_screen.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnTvPreview
import com.protonvpn.android.base.ui.nav.VpnNavHostController
import com.protonvpn.android.base.ui.nav.rememberVpnNavHostController
import com.protonvpn.android.base.ui.theme.backgroundSelected
import com.protonvpn.android.tv.main_screen.ui.nav.TvMainScreenNav
import com.protonvpn.android.tv.main_screen.ui.nav.TvMainTarget
import com.protonvpn.android.tv.vpn.ui.VpnStatusBackgroundTv
import com.protonvpn.android.tv.vpn.ui.VpnStatusTvViewState
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun TvMainScreenRoute(
    modifier: Modifier = Modifier,
    viewModel: TvMainScreenViewModel = hiltViewModel(),
) {
    val navController = rememberVpnNavHostController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val vpnStatus = viewModel.vpnStatus.collectAsStateWithLifecycle().value
    TvMainScreen(
        navController = navController,
        drawerState = drawerState,
        vpnStatus = vpnStatus,
        modifier = modifier
    )
}

private val DrawerHorizontalPadding = 12.dp

@Composable
internal fun TvMainScreen(
    navController: VpnNavHostController,
    drawerState: DrawerState,
    vpnStatus: VpnStatusTvViewState.Status?,
    modifier: Modifier = Modifier,
    startScreen: TvMainTarget = TvMainTarget.Home,
) {
    val nav = remember(navController, startScreen) { TvMainScreenNav(navController, startScreen) }
    val currentTarget = nav.currentTargetAsState()

    BoxWithConstraints(modifier) {
        val collapsedDrawerWidth =
            NavigationDrawerItemDefaults.CollapsedDrawerItemWidth + DrawerHorizontalPadding * 2
        val contentWidth = maxWidth - collapsedDrawerWidth

        // TODO: display regular background when in settings.
        VpnStatusBackgroundTv(
            state = vpnStatus,
            modifier = Modifier
                .fillMaxWidth()
        )

        NavigationDrawer(
            drawerContent = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = DrawerHorizontalPadding)
                        .selectableGroup()
                ) {
                    TvMainTarget.entries.forEach { target ->
                        val isSelected = target == currentTarget
                        val interactionSource = remember { MutableInteractionSource() }
                        NavigationDrawerItem(
                            selected = isSelected,
                            onClick = { nav.navigate(target) },
                            leadingContent = {
                                NavDrawerIcon(target)
                            },
                            interactionSource = interactionSource,
                            colors = NavigationDrawerItemDefaults.colors(
                                contentColor = ProtonTheme.colors.iconWeak,
                                focusedContentColor = ProtonTheme.colors.iconInverted,
                                pressedContentColor = ProtonTheme.colors.iconInverted,
                                focusedContainerColor = ProtonTheme.colors.backgroundSelected,
                                pressedContainerColor = ProtonTheme.colors.backgroundSelected,
                                selectedContainerColor = ProtonTheme.colors.backgroundSelected.copy(
                                    alpha = 0.16f
                                ),
                            ),
                            shape = NavigationDrawerItemDefaults.shape(ProtonTheme.shapes.large),
                        ) {
                            Text(stringResource(target.labelRes()))
                        }
                    }
                }
            },
            drawerState = drawerState,
            modifier = Modifier.fillMaxSize(),
        ) {
            nav.NavHost(
                Modifier
                    .fillMaxHeight()
                    // Use fixed width to force the content to slide when the drawer expands.
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .width(contentWidth)
            )
        }
    }
}


@Composable
fun TvSettingsPlaceholder(modifier: Modifier = Modifier) {
    PagePlaceholder(stringResource(TvMainTarget.Settings.labelRes()), Color(0xFF4E2B7E), modifier)
}

@Composable
private fun PagePlaceholder(title: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(48.dp)
    ) {
        Text(title, style = ProtonTheme.typography.hero)
        Spacer(Modifier.height(24.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(color, RoundedCornerShape(16.dp))
        ) {
            Button(onClick = {}) {
                Text("Placeholder")
            }
        }
    }
}

@Composable
private fun NavDrawerIcon(
    target: TvMainTarget,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(target.drawerIconRes()),
        contentDescription = null,
        modifier = modifier,
    )
}

@StringRes
private fun TvMainTarget.labelRes(): Int = when (this) {
    TvMainTarget.Home -> R.string.bottom_nav_home
    TvMainTarget.Settings -> R.string.botton_nav_settings
}

@DrawableRes
private fun TvMainTarget.drawerIconRes(): Int = when (this) {
    TvMainTarget.Home -> CoreR.drawable.ic_proton_house_filled
    TvMainTarget.Settings -> CoreR.drawable.ic_proton_cog_wheel_filled
}

@ProtonVpnTvPreview
@Composable
private fun TvMainScreenPreview() {
    ProtonVpnTvPreview {
        TvMainScreen(
            navController = rememberVpnNavHostController(),
            drawerState = rememberDrawerState(DrawerValue.Closed),
            vpnStatus = VpnStatusTvViewState.Status.Unprotected,
        )
    }
}
