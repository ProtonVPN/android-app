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

package com.protonvpn.android.redesign.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.protonvpn.android.R
import com.protonvpn.android.redesign.settings.ui.nav.SubSettingsScreen
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultStrongNorm
import me.proton.core.presentation.R as CoreR

@Composable
fun SubSettingsRoute(type: SubSettingsScreen.Type, onClose: () -> Unit) {
    when (type) {
        SubSettingsScreen.Type.VpnAccelerator -> VpnAccelerator(onClose)
    }
}

@Composable
fun VpnAccelerator(onClose: () -> Unit) {
    SubSetting(title = stringResource(id = R.string.settings_vpn_accelerator_title), onClose = onClose) {
        
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubSetting(
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = ProtonTheme.colors.backgroundNorm)
    ) {
        TopAppBar(
            title = {
                Text(text = title, style = ProtonTheme.typography.defaultStrongNorm)
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = ProtonTheme.colors.backgroundNorm
            ),
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(id = CoreR.drawable.ic_arrow_back),
                        contentDescription = stringResource(id = R.string.accessibility_back)
                    )
                }
            },
        )
        content()
    }
}