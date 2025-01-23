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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.data.ProfileInfo
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.CollapsibleToolbarTitle
import com.protonvpn.android.redesign.base.ui.CollapsibleToolbarScaffold
import com.protonvpn.android.redesign.base.ui.ConnectIntentIcon
import com.protonvpn.android.redesign.base.ui.ConnectIntentIconSize
import com.protonvpn.android.redesign.base.ui.InfoButton
import com.protonvpn.android.redesign.base.ui.InfoSheet
import com.protonvpn.android.redesign.base.ui.InfoSheetState
import com.protonvpn.android.redesign.base.ui.InfoType
import com.protonvpn.android.redesign.base.ui.VpnDivider
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.redesign.base.ui.rememberInfoSheetState
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentAvailability
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentRow
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.utils.openUrl
import com.protonvpn.android.vpn.ProtocolSelection
import me.proton.core.compose.theme.ProtonTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Profiles(
    state: ProfilesState,
    onAddNew: () -> Unit,
    onConnect: (ProfileViewItem) -> Unit,
    onSelect: (ProfileViewItem) -> Unit,
    snackbarHostState: SnackbarHostState,
    infoSheetState: InfoSheetState = rememberInfoSheetState(),
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val largeTitleContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            CollapsibleToolbarTitle(R.string.profiles_title, scrollBehavior, modifier = Modifier.weight(1f))
            InfoButton(InfoType.Profiles, onOpenInfo = infoSheetState::show, modifier = Modifier.padding(end = 16.dp))
        }
    }

    CollapsibleToolbarScaffold(
        title = largeTitleContent,
        scrollBehavior = scrollBehavior,
        contentWindowInsets = WindowInsets.statusBars,
        toolbarAdditionalContent = {},
        snackbarHostState = snackbarHostState,
    ) { padding ->
        val modifier = Modifier
            .padding(padding)
            .padding(horizontal = largeScreenContentPadding())
            .fillMaxSize()
        when (state) {
            is ProfilesState.ZeroState ->
                ProfilesListZeroScreen(onAddNew, modifier)
            is ProfilesState.ProfilesList -> {
                ProfilesList(
                    profiles = state.profiles,
                    onAddNew = onAddNew,
                    onConnect = onConnect,
                    onSelect = onSelect,
                    modifier = modifier,
                )
            }
        }
    }
    val context = LocalContext.current
    InfoSheet(
        infoSheetState,
        onOpenUrl = { context.openUrl(it) },
        onGotItClick = infoSheetState::dismiss
    )
}

@Composable
fun ProfilesListZeroScreen(
    onAddNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Image(
            painter = painterResource(id = R.drawable.ic_proton_stars),
            contentDescription = null,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Text(
            text = stringResource(R.string.profiles_zero_state_title),
            style = ProtonTheme.typography.body1Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Text(
            text = stringResource(R.string.profiles_zero_state_description),
            style = ProtonTheme.typography.body2Regular,
            color = ProtonTheme.colors.textWeak,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Spacer(modifier = Modifier.weight(1f))

        VpnSolidButton(
            text = stringResource(R.string.profiles_button_create_profile),
            onClick = onAddNew,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfilesList(
    profiles: List<ProfileViewItem>,
    onAddNew: () -> Unit,
    onConnect: (ProfileViewItem) -> Unit,
    onSelect: (ProfileViewItem) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    Column(
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(profiles, key = { _, profile -> profile.profile.id }) { index, profile ->
                Column(modifier = Modifier.animateItemPlacement()) {
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

        VpnSolidButton(
            text = stringResource(R.string.profiles_button_create_profile),
            onClick = onAddNew,
            modifier = Modifier.padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
        )
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
        leadingComposable = {
            ConnectIntentIcon(
                profile.intent.primaryLabel,
                profileConnectIntentIconSize = ConnectIntentIconSize.LARGE
            )
        },
        modifier = modifier,
    )
}

@Preview
@Composable
fun ProfileItemPreview() {
    ProtonVpnPreview {
        ProfileItem(
            profile = ProfileViewItem(
                ProfileInfo(
                    id = 1,
                    name = "Profile name",
                    icon = ProfileIcon.Icon1,
                    color = ProfileColor.Color1,
                    createdAt = 0L,
                    isUserCreated = true,
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
                lanConnections = true,
            ),
            onConnect = {},
            onSelect = {}
        )
    }
}
