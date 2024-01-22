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

package com.protonvpn.android.redesign.settings.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.hilt.navigation.compose.hiltViewModel
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ComposableSettingsBinding
import com.protonvpn.android.ui.account.AccountActivity
import com.protonvpn.android.utils.AndroidUtils.launchActivity
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultSmallStrongUnspecified
import me.proton.core.compose.theme.defaultSmallWeak
import me.proton.core.compose.theme.defaultStrongNorm
import me.proton.core.compose.theme.defaultWeak
import me.proton.core.presentation.R as CoreR

@Composable
fun SettingsRoute(signOut: () -> Unit) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = hiltViewModel()
    val viewState = viewModel.viewState.collectAsState().value
    SettingsView(
        viewState = viewState,
        onAccountClick = {
            context.launchActivity<AccountActivity>()
        },
        signOut = signOut
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsibleToolbarScaffold(
    modifier: Modifier = Modifier,
    @StringRes titleResId: Int,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val isCollapsed = remember { derivedStateOf { scrollBehavior.state.collapsedFraction > 0.5 } }

    val topAppBarElementColor = if (isCollapsed.value) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    val collapsedTextSize = 16
    val expandedTextSize = 28
    val topAppBarTextSize =
        (collapsedTextSize + (expandedTextSize - collapsedTextSize) * (1 - scrollBehavior.state.collapsedFraction)).sp

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = titleResId),
                        style = ProtonTheme.typography.defaultStrongNorm,
                        fontSize = topAppBarTextSize
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = ProtonTheme.colors.backgroundNorm,
                    scrolledContainerColor = ProtonTheme.colors.backgroundSecondary,
                    navigationIconContentColor = topAppBarElementColor,
                    titleContentColor = topAppBarElementColor,
                    actionIconContentColor = topAppBarElementColor,
                ),
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = contentWindowInsets,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        content = content
    )
}

@Composable
private fun SettingsView(
    onAccountClick: () -> Unit,
    signOut: () -> Unit,
    viewState: SettingsViewModel.SettingsViewState,
    modifier: Modifier = Modifier
) {
    val userState = viewState.userInfo
    CollapsibleToolbarScaffold(
        modifier = modifier.windowInsetsPadding(WindowInsets.statusBars),
        titleResId = R.string.settings_title,
        contentWindowInsets = WindowInsets.statusBars
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Category(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                title = stringResource(id = R.string.settings_category_account)
            ) {
                SettingRowWithComposables(
                    leadingComposable = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ProtonTheme.colors.brandNorm)
                        ) {
                            Text(
                                text = userState.shortenedName,
                                style = ProtonTheme.typography.defaultNorm
                            )
                        }
                    },
                    title = userState.displayName,
                    subtitle = userState.email,
                    onClick = onAccountClick,
                )
                SettingRowWithIcon(
                    icon = CoreR.drawable.ic_proton_arrow_in_to_rectangle,
                    title = stringResource(id = R.string.settings_sign_out),
                    onClick = signOut
                )
            }

            // To be swapped to phase2 composable
            AndroidViewBinding(ComposableSettingsBinding::inflate)
            Text(
                text = stringResource(R.string.drawerAppVersion, BuildConfig.VERSION_NAME),
                style = ProtonTheme.typography.defaultSmallWeak,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ColumnScope.Category(
    modifier: Modifier = Modifier,
    title: String,
    content: @Composable () -> Unit
) {
    Text(
        text = title,
        color = ProtonTheme.colors.textAccent,
        style = ProtonTheme.typography.defaultSmallStrongUnspecified,
        modifier = modifier.padding(bottom = 8.dp, top = 16.dp)
    )
    content()
}

@Composable
private fun SettingRowWithComposables(
    modifier: Modifier = Modifier,
    leadingComposable: @Composable () -> Unit,
    trailingComposable: (@Composable () -> Unit)? = null,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    var baseModifier = modifier
        .fillMaxWidth()

    if (onClick != null) {
        baseModifier = baseModifier
            .clickable(onClick = onClick)

    }
    baseModifier = baseModifier.padding(vertical = 16.dp, horizontal = 16.dp)

    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(30.dp),
            contentAlignment = Alignment.Center
        ) {
            leadingComposable()
        }
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Text(
                text = title,
                style = ProtonTheme.typography.defaultNorm
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = ProtonTheme.typography.defaultWeak
                )
            }
        }
        trailingComposable?.invoke()
    }
}

@Composable
fun SettingRowWithIcon(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    iconTint: Color = ProtonTheme.colors.iconNorm,
    title: String,
    subtitle: String? = null,
    @DrawableRes trailingIcon: Int? = null,
    trailingIconTint: Color = ProtonTheme.colors.iconWeak,
    onClick: (() -> Unit)? = null
) {
    SettingRowWithComposables(
        leadingComposable = {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = iconTint,
            )
        },
        trailingComposable = {
            trailingIcon?.let {
                Icon(
                    painter = painterResource(id = it),
                    contentDescription = null,
                    tint = trailingIconTint,
                    modifier = Modifier
                        .padding(end = 8.dp)
                )
            }
        },
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        modifier = modifier
    )
}

@Preview
@Composable
fun SettingRowWithIconPreview() {
    SettingRowWithIcon(
        icon = R.drawable.vpn_plus_badge,
        title = "Netshield",
        subtitle = "On",
        onClick = { }
    )
}

@Preview
@Composable
fun SettingRowWithComposablesPreview() {
    SettingRowWithComposables(
        leadingComposable = {
            Text("A")
        },
        title = "User",
        subtitle = "user@mail.com",
        onClick = { }
    )
}

@Preview
@Composable
fun CategoryPreview() {
    Column {
        Category(title = stringResource(id = R.string.settings_category_features)) {
            SettingRowWithComposables(
                leadingComposable = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ProtonTheme.colors.brandNorm)
                    ) {
                        Text(
                            text = "AG",
                            style = ProtonTheme.typography.defaultNorm
                        )
                    }
                },
                title = stringResource(id = R.string.settings_netshield_title),
                subtitle = "On"
            )

            SettingRowWithIcon(
                icon = CoreR.drawable.ic_proton_earth,
                title = stringResource(id = R.string.settings_netshield_title),
                subtitle = "On"
            )
            SettingRowWithIcon(
                icon = CoreR.drawable.ic_proton_earth,
                title = stringResource(id = R.string.settings_split_tunneling_title),
                subtitle = "On"
            )
            SettingRowWithIcon(
                icon = CoreR.drawable.ic_proton_earth,
                title = stringResource(id = R.string.settings_kill_switch_title)
            )
        }
    }
}
