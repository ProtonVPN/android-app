/*
 * Copyright (c) 2025. Proton AG
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

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    isScrolledPredicate: () -> Boolean,
    modifier: Modifier = Modifier,
    backgroundColor: Color = ProtonTheme.colors.backgroundNorm,
    scrolledBackgroundColor: Color = ProtonTheme.colors.backgroundSecondary,
) {
    val isScrolled by remember { derivedStateOf(isScrolledPredicate) }
    val topAppBarColor by animateColorAsState(
        targetValue = if (isScrolled) scrolledBackgroundColor else backgroundColor,
        label = "topAppBarColor"
    )
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = topAppBarColor,
            titleContentColor = ProtonTheme.colors.textNorm,
        ),
        title = {
            ProvideTextStyle(ProtonTheme.typography.body1Medium, title)
        },
        navigationIcon = navigationIcon,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    backgroundColor: Color = ProtonTheme.colors.backgroundNorm,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = backgroundColor,
            titleContentColor = ProtonTheme.colors.textNorm,
        ),
        title = {
            ProvideTextStyle(ProtonTheme.typography.body1Medium, title)
        },
        navigationIcon = navigationIcon,
        modifier = modifier,
        windowInsets = windowInsets,
        actions = actions,
    )
}

@Composable
fun TopAppBarBackIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = CoreR.drawable.ic_arrow_back),
            contentDescription = stringResource(id = R.string.accessibility_back)
        )
    }
}

@Composable
fun TopAppBarCloseIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = CoreR.drawable.ic_proton_cross),
            contentDescription = stringResource(id = R.string.accessibility_close)
        )
    }
}

@Composable
fun TopAppBarTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(weight = 1f)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(space = 4.dp),
        ) {
            Text(
                text = title,
                color = ProtonTheme.colors.textNorm,
                style = ProtonTheme.typography.body1Medium,
            )

            subtitle?.let { text ->
                Text(
                    text = text,
                    color = ProtonTheme.colors.textWeak,
                    style = ProtonTheme.typography.captionRegular,
                )
            }
        }
    }
}
