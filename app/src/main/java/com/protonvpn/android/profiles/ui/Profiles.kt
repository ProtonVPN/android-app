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

package com.protonvpn.android.profiles.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.LightAndDarkPreview
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.data.ProfileInfo
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.ConnectIntentIcon
import com.protonvpn.android.redesign.base.ui.VpnDivider
import com.protonvpn.android.redesign.settings.ui.CollapsibleToolbarScaffold
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentAvailability
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentRow
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.vpn.ProtocolSelection

@Composable
fun Profiles(
    state: ProfilesState,
    onConnect: (ProfileViewItem) -> Unit,
    onSelect: (ProfileViewItem) -> Unit,
) {
    CollapsibleToolbarScaffold(
        titleResId = R.string.profiles_title,
        contentWindowInsets = WindowInsets.statusBars,
        toolbarActions = {},
        toolbarAdditionalContent = {},
    ) { padding ->
        when (state) {
            is ProfilesState.ZeroState -> {}
            is ProfilesState.ProfilesList -> {
                ProfilesList(
                    profiles = state.profiles,
                    onConnect = onConnect,
                    onSelect = onSelect,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
fun ProfilesList(
    profiles: List<ProfileViewItem>,
    onConnect: (ProfileViewItem) -> Unit,
    onSelect: (ProfileViewItem) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        state = listState,
        modifier = modifier
    ) {
        itemsIndexed(profiles, key = { _, profile -> profile.profile.id }) { index, profile ->
            ProfileItem(
                profile = profile,
                onConnect = onConnect,
                onSelect = onSelect,
            )
            if (index < profiles.lastIndex)
                VpnDivider()
        }
    }
}

@Composable
fun ProfileItem(
    profile: ProfileViewItem,
    onConnect: (ProfileViewItem) -> Unit,
    onSelect: (ProfileViewItem) -> Unit,
    modifier: Modifier = Modifier
) {
    ConnectIntentRow(
        availability = profile.availability,
        connectIntent = profile.intent,
        isConnected = profile.isConnected,
        onClick = { onConnect(profile) },
        onOpen = { onSelect(profile) },
        leadingComposable = { FlagOrGatewayIndicator(profile.intent.primaryLabel) },
        modifier = modifier,
    )
}

@Preview
@Composable
fun ProfileItemPreview() {
    LightAndDarkPreview {
        ProfileItem(
            profile = ProfileViewItem(
                ProfileInfo(
                    id = 1,
                    name = "Profile name",
                    icon = ProfileIcon.Icon1,
                    color = ProfileColor.Color1,
                    isGateway = false
                ),
                isConnected = false,
                availability = ConnectIntentAvailability.ONLINE,
                intent = ConnectIntentViewState(
                    ConnectIntentPrimaryLabel.Profile("Profile name", CountryId.sweden, false, ProfileIcon.Icon1, ProfileColor.Color1),
                    ConnectIntentSecondaryLabel.Country(CountryId.sweden),
                    emptySet(),
                ),
                netShieldEnabled = true,
                protocol = ProtocolSelection.SMART,
                natType = NatType.Strict,
                lanConnections = true
            ),
            onConnect = {},
            onSelect = {}
        )
    }
}