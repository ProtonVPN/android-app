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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.protonvpn.android.profiles.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.data.ProfileInfo
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentAvailability
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun ProfileBottomSheet(profile: ProfileViewItem?, onClose: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    if (profile != null) {
        ModalBottomSheet(
            sheetState = sheetState,
            content = {
                ProfileSheetContent(profile, Modifier.padding(bottom = bottomPadding))
            },
            windowInsets = WindowInsets(0, 0, 0 ,0), // Draw under navigation bar to cover bottom sheet below
            onDismissRequest = onClose
        )
    }
}

@Composable
private fun ProfileSheetContent(profile: ProfileViewItem, modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = profile.profile.name,
            style = ProtonTheme.typography.body1Regular
        )
        Text(
            text = "Sub label",
            style = ProtonTheme.typography.body2Regular,
            color = ProtonTheme.colors.textWeak
        )
    }
}

@Preview
@Composable
private fun ProfileBottomSheetPreview() {
    ProfileBottomSheet(
        ProfileViewItem(
            ProfileInfo(
                id = 1,
                name = "Profile name",
                icon = ProfileIcon.Icon1,
                color = ProfileColor.Color1,
            ),
            isConnected = false,
            availability = ConnectIntentAvailability.ONLINE,
            intent = ConnectIntentViewState(
                ConnectIntentPrimaryLabel.Profile("Profile name", CountryId.fastest),
                ConnectIntentSecondaryLabel.Country(CountryId.fastest),
                emptySet(),
            )
        ),
        onClose = {}
    )
}