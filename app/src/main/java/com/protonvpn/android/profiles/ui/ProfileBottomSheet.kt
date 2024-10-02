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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.SimpleModalBottomSheet
import com.protonvpn.android.base.ui.theme.LightAndDarkPreview
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.data.ProfileInfo
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.VpnDivider
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentAvailability
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.redesign.vpn.ui.StaticConnectIntentRow
import com.protonvpn.android.vpn.ProtocolSelection
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun ProfileBottomSheet(
    profile: ProfileViewItem?,
    onClose: () -> Unit,
    onProfileDelete: (ProfileViewItem) -> Unit,
) {
    if (profile != null) {
        SimpleModalBottomSheet(
            content = { ProfileSheetContent(profile, onProfileDelete) },
            onDismissRequest = onClose
        )
    }
}

@Composable
private fun ProfileSheetContent(
    profile: ProfileViewItem,
    onProfileDelete: (ProfileViewItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        StaticConnectIntentRow(
            profile.intent,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        ProfileSettingItem(
           R.string.netshield_feature_name,
           stringResource(if (profile.netShieldEnabled) R.string.netshield_state_on else R.string.netshield_state_off),
           if (profile.netShieldEnabled) R.drawable.feature_netshield_on else R.drawable.ic_netshield_off,
           modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 16.dp, end = 16.dp)
        )
        VpnDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            ProfileSettingItem(
                R.string.settings_protocol_title,
                stringResource(profile.protocol.displayName),
                null,
                modifier = Modifier.weight(1f)
            )
            ProfileSettingItem(
                R.string.profile_bottom_sheet_nat_title,
                stringResource(profile.natType.shortLabelRes),
                null,
                modifier = Modifier.weight(1f)
            )
            ProfileSettingItem(
                R.string.profile_bottom_sheet_lan_title,
                stringResource(if (profile.lanConnections) R.string.lan_state_on else R.string.lan_state_off),
                null,
                modifier = Modifier.weight(1f)
            )
        }
        VpnDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
        BottomSheetAction(
            CoreR.drawable.ic_proton_trash,
            R.string.profile_action_delete,
            { onProfileDelete(profile) }
        )
    }
}

@Composable
fun BottomSheetAction(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(16.dp)
            .fillMaxWidth(),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(title),
            style = ProtonTheme.typography.body1Regular,
        )
    }
}

@Composable
fun ProfileSettingItem(
    @StringRes title: Int,
    value: String,
    @DrawableRes icon: Int?,
    modifier: Modifier = Modifier
) {
    Column(modifier.semantics(mergeDescendants = true) {  }) {
        Text(
            text = stringResource(title),
            style = ProtonTheme.typography.body2Regular,
            color = ProtonTheme.colors.textWeak,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.padding(vertical = 2.dp).size(20.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = value,
                style = ProtonTheme.typography.body1Regular,
            )
        }
    }
}

@Preview
@Composable
private fun ProfileBottomSheetPreview() {
    LightAndDarkPreview {
        ProfileSheetContent(
            ProfileViewItem(
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
                    ConnectIntentPrimaryLabel.Profile(
                        "Profile name",
                        CountryId.fastest,
                        false,
                        ProfileIcon.Icon1,
                        ProfileColor.Color1
                    ),
                    ConnectIntentSecondaryLabel.Country(CountryId.fastest),
                    emptySet(),
                ),
                netShieldEnabled = true,
                protocol = ProtocolSelection.SMART,
                natType = NatType.Strict,
                lanConnections = true,
            ),
            {},
        )
    }
}