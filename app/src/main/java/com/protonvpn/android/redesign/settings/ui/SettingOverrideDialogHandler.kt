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

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.protonvpn.android.R
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.ui.nav.ProfileCreationTarget
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.ConnectIntentIcon
import com.protonvpn.android.redesign.base.ui.ConnectIntentIconSize
import com.protonvpn.android.redesign.base.ui.ProtonAlert
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultWeak


@Composable
fun SettingOverrideDialogHandler(
    onNavigateToEditProfile: (Long, ProfileCreationTarget) -> Unit,
    content: @Composable (onSettingItemClick: (OverrideType, () -> Unit) -> Unit) -> Unit
) {
    val viewModel = hiltViewModel<SettingOverrideViewModel>()
    var dialogSettingType by rememberSaveable { mutableStateOf<OverrideType?>(null) }

    dialogSettingType?.let { type ->
        SettingOverrideDialog(
            onDismissRequest = { dialogSettingType = null },
            settingType = type,
            onProfileSettingChosen = {
                viewModel.getCurrentProfileId()?.let { profileId ->
                    onNavigateToEditProfile(profileId, ProfileCreationTarget.FeaturesAndSettings)
                }
                dialogSettingType = null
            }
        )
    }

    val onSettingItemClick: (OverrideType, () -> Unit) -> Unit = { type, onSettingAction ->
        if (!viewModel.isConnectedToProfile()) {
            onSettingAction()
        } else {
            dialogSettingType = type
        }
    }
    content(onSettingItemClick)
}

@Composable
private fun SettingOverrideDialog(
    onDismissRequest: () -> Unit,
    settingType: OverrideType,
    onProfileSettingChosen: () -> Unit
) {
    ProtonAlert(
        title = stringResource(
            id = R.string.profile_connected_dialog_title,
            stringResource(settingType.stringRes)
        ),
        text = stringResource(id = R.string.profile_connected_edit_setting),
        textColor = ProtonTheme.colors.textWeak,
        confirmLabel = stringResource(id = R.string.edit_profile_dialog_action),
        onConfirm = {
            onProfileSettingChosen()
        },
        dismissLabel = stringResource(id = R.string.dialog_action_cancel),
        onDismissButton = { onDismissRequest() },
        onDismissRequest = onDismissRequest
    )
}

@Composable
fun ProfileOverrideView(
    modifier: Modifier = Modifier,
    profileOverrideInfo: SettingsViewModel.ProfileOverrideInfo
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ProtonTheme.colors.backgroundSecondary)
            .padding(12.dp)
    ) {
        ConnectIntentIcon(
            profileOverrideInfo.primaryLabel,
            profileConnectIntentIconSize = ConnectIntentIconSize.MEDIUM
        )
        Spacer(Modifier.size(12.dp))
        val parts = stringResource(id = R.string.profile_overrides_setting).split("%1\$s")
        Text(
            text = buildAnnotatedString {
                append(parts[0])
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(profileOverrideInfo.profileName)
                }
                append(parts[1])
            },
            style = ProtonTheme.typography.body2Regular,
            color = ProtonTheme.colors.textWeak
        )
    }
}

@Composable
fun OverrideSettingLabel(
    modifier: Modifier = Modifier,
    settingValue: SettingValue.SettingOverrideValue
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ProtonTheme.colors.backgroundSecondary)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        ConnectIntentIcon(
            settingValue.connectIntentPrimaryLabel,
            profileConnectIntentIconSize = ConnectIntentIconSize.SMALL
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(settingValue.subtitleRes),
            style = ProtonTheme.typography.defaultWeak
        )
    }
}

enum class OverrideType(@StringRes val stringRes: Int) {
    LAN(R.string.settings_advanced_allow_lan_title),
    NatType(R.string.settings_advanced_nat_type_title),
    NetShield(R.string.settings_netshield_title),
    Protocol(R.string.settings_protocol_title),
}

@Preview
@Composable
fun PreviewProfileSettingChange() {
    SettingOverrideDialog(
        onDismissRequest = {},
        settingType = OverrideType.NetShield,
        onProfileSettingChosen = {}
    )
}

@Preview
@Composable
fun PreviewOverrideLabel() {
    Column {
        val connectIntentLabel = ConnectIntentPrimaryLabel.Profile(
            "Profile name",
            CountryId.sweden,
            false,
            ProfileIcon.Icon1,
            ProfileColor.Color1
        )
        ProfileOverrideView(
            profileOverrideInfo = SettingsViewModel.ProfileOverrideInfo(
                connectIntentLabel,
                "profile"
            )
        )
        Spacer(Modifier.height(16.dp))
        OverrideSettingLabel(
            settingValue = SettingValue.SettingOverrideValue(
                connectIntentLabel,
                R.string.netshield_state_on
            )
        )
    }

}
