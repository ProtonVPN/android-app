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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.SimpleModalBottomSheet
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.data.ProfileInfo
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.ConnectIntentIconSize
import com.protonvpn.android.redesign.base.ui.ProfileConnectIntentIcon
import com.protonvpn.android.redesign.base.ui.VpnDivider
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentAvailability
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentBlankRow
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewStateProfile
import com.protonvpn.android.redesign.vpn.ui.label
import com.protonvpn.android.vpn.ProtocolSelection
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun ProfileBottomSheet(
    profile: ProfileViewItem,
    onClose: () -> Unit,
    onProfileEdit: (ProfileViewItem) -> Unit,
    onProfileDuplicate: (ProfileViewItem) -> Unit,
    onProfileDelete: (ProfileViewItem) -> Unit,
) {
    SimpleModalBottomSheet(
        content = {
            ProfileSheetContent(
                profile,
                onProfileEdit = { profile ->
                    onClose()
                    onProfileEdit(profile)
                },
                onProfileDuplicate = { profile ->
                    onClose()
                    onProfileDuplicate(profile)
                },
                onProfileDelete
            )
        },
        onDismissRequest = onClose
    )
}

@Composable
private fun ProfileSheetContent(
    profile: ProfileViewItem,
    onProfileEdit: (ProfileViewItem) -> Unit,
    onProfileDuplicate: (ProfileViewItem) -> Unit,
    onProfileDelete: (ProfileViewItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        StaticProfileConnectIntentRow(
            profile.intent,
            connectIntentIconSize = ConnectIntentIconSize.LARGE,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            val netshieldStateResource = when {
                !profile.netShieldEnabled -> R.string.netshield_state_off
                profile.customDnsEnabled -> R.string.netshield_state_unavailable
                else -> R.string.netshield_state_on
            }
            ProfileSettingItem(
                R.string.netshield_feature_name,
                stringResource(netshieldStateResource),
                if (profile.netShieldEnabled) R.drawable.feature_netshield_on else R.drawable.ic_netshield_off,
                modifier = Modifier.weight(1f)
            )
            ProfileSettingItem(
                R.string.settings_custom_dns_title,
                stringResource(if (profile.customDnsEnabled) R.string.netshield_state_on else R.string.netshield_state_off),
                null,
                modifier = Modifier.weight(1f)
            )
        }
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
            CoreR.drawable.ic_proton_pencil,
            R.string.profile_action_edit,
            { onProfileEdit(profile) }
        )
        BottomSheetAction(
            CoreR.drawable.ic_proton_folders,
            R.string.profile_action_duplicate,
            { onProfileDuplicate(profile) }
        )
        BottomSheetAction(
            CoreR.drawable.ic_proton_trash,
            R.string.profile_action_delete,
            { onProfileDelete(profile) },
            enabled = !profile.isConnected,
        )
    }
}

@Composable
fun BottomSheetAction(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val contentColor = if (enabled) LocalContentColor.current else ProtonTheme.colors.textHint
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .clickable(onClick = onClick, enabled = enabled)
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

// Intent row without actions and connection/availability state.
@Composable
private fun StaticProfileConnectIntentRow(
    intent: ConnectIntentViewStateProfile,
    connectIntentIconSize: ConnectIntentIconSize,
    modifier: Modifier = Modifier,
) {
    ConnectIntentBlankRow(
        title = intent.primaryLabel.label(),
        subTitle = intent.secondaryLabel?.label(),
        serverFeatures = intent.serverFeatures,
        isConnected = false,
        isUnavailable = false,
        leadingComposable = {
            ProfileConnectIntentIcon(intent.primaryLabel, profileConnectIntentIconSize = connectIntentIconSize)
        },
        trailingComposable = {},
        modifier = modifier
    )
}

@Preview
@Composable
private fun ProfileBottomSheetPreview() {
    ProtonVpnPreview {
        ProfileSheetContent(
            ProfileViewItem(
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
                intent = ConnectIntentViewStateProfile(
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
                customDnsEnabled = false,
                lanConnections = true,
            ),
            {},
            {},
            {},
            Modifier
        )
    }
}
