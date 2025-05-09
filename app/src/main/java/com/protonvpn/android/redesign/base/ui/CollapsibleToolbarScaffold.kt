/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.android.redesign.base.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import me.proton.core.compose.theme.ProtonTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsibleToolbarTitle(
    @StringRes titleResId: Int,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    val collapsedTextSize = 16
    val expandedTextSize = 28
    val topAppBarTextSize =
        (collapsedTextSize + (expandedTextSize - collapsedTextSize) * (1 - scrollBehavior.state.collapsedFraction)).sp
    Text(
        text = stringResource(id = titleResId),
        style = ProtonTheme.typography.hero,
        fontSize = topAppBarTextSize,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsibleToolbarScaffold(
    modifier: Modifier = Modifier,
    @StringRes titleResId: Int,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    toolbarActions: @Composable RowScope.() -> Unit = {},
    toolbarAdditionalContent: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val title = @Composable { CollapsibleToolbarTitle(titleResId, scrollBehavior) }

    CollapsibleToolbarScaffold(
        modifier, title, scrollBehavior, contentWindowInsets, toolbarActions, toolbarAdditionalContent, snackbarHostState, content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsibleToolbarScaffold(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState()),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    toolbarActions: @Composable RowScope.() -> Unit = {},
    toolbarAdditionalContent: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (PaddingValues) -> Unit
) {
    val topAppBarElementColor = ProtonTheme.colors.textNorm
    val expandedColor = ProtonTheme.colors.backgroundNorm
    val collapsedColor = ProtonTheme.colors.backgroundSecondary
    val topAppBarColor by remember {
        derivedStateOf {
            lerp(expandedColor, collapsedColor, scrollBehavior.state.collapsedFraction)
        }
    }
    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(color = topAppBarColor)) {
                MediumTopAppBar(
                    title = title,
                    actions = toolbarActions,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = topAppBarColor,
                        scrolledContainerColor = topAppBarColor,
                        navigationIconContentColor = topAppBarElementColor,
                        titleContentColor = topAppBarElementColor,
                        actionIconContentColor = topAppBarElementColor,
                    ),
                    scrollBehavior = scrollBehavior
                )
                toolbarAdditionalContent()
            }
        },
        contentWindowInsets = contentWindowInsets,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        content = content,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { ProtonSnackbar(it) }
        }
    )
}
