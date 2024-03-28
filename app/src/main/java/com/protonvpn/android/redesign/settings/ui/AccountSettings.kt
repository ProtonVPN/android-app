/*
 * Copyright (c) 2024. Proton AG
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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.VpnOutlinedButton
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.theme.LightAndDarkPreview
import com.protonvpn.android.redesign.base.ui.SettingsItem
import com.protonvpn.android.redesign.base.ui.UpsellBanner
import com.protonvpn.android.redesign.base.ui.VpnDivider
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.domain.entity.UserId

@Composable
fun AccountSettings(
    viewState: SettingsViewModel.AccountSettingsViewState,
    onChangePassword: () -> Unit,
    onChangeRecoveryEmail: () -> Unit,
    onOpenMyAccount: () -> Unit,
    onDeleteAccount: () -> Unit,
    onUpgrade: () -> Unit,
    onClose: () -> Unit,
) {
    SubSetting(
        title = stringResource(R.string.settings_account_settings_title),
        onClose = onClose,
    ) {
        if (viewState.upgradeToPlusBanner) {
            UpsellBanner(
                titleRes = null,
                descriptionRes = R.string.vpnplus_upsell_banner_description,
                iconRes = R.drawable.banner_icon_vpnplus,
                onClick = onUpgrade,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        SettingsItem(
            name = viewState.displayName,
            subTitle = viewState.planDisplayName ?: stringResource(R.string.accountFree),
            actionComposable = {},
        )
        SettingsItem(
            name = stringResource(R.string.settings_account_change_password),
            subTitle = viewState.passwordHint?.let { stringResource(id = it) },
            actionComposable = {},
            modifier = Modifier.clickable(onClick = onChangePassword),
        )
        SettingsItem(
            name = stringResource(R.string.settings_account_recovery_email),
            subTitle = viewState.recoveryEmail ?: stringResource(R.string.settings_account_recovery_email_not_set),
            actionComposable = {},
            modifier = Modifier.clickable(onClick = onChangeRecoveryEmail),
        )

        VpnSolidButton(
            text = stringResource(R.string.settings_account_button_my_account),
            onClick = onOpenMyAccount,
            isExternalLink = true,
            modifier = Modifier.padding(16.dp)
        )
        VpnDivider(modifier = Modifier.padding(horizontal = 16.dp))
        VpnOutlinedButton(
            text = stringResource(R.string.settings_account_button_delete_account),
            onClick = onDeleteAccount,
            isExternalLink = true,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview
@Composable
private fun PreviewAccountSettings() {
    LightAndDarkPreview {
        val state = SettingsViewModel.AccountSettingsViewState(
            userId = UserId("dummyUserId"),
            displayName = "user@proton.me",
            planDisplayName = "VPN Free",
            recoveryEmail = null,
            passwordHint = null,
            upgradeToPlusBanner = true,
        )
        Surface(color = ProtonTheme.colors.backgroundNorm) {
            AccountSettings(viewState = state, {}, {}, {}, {}, {},        {})
        }
    }
}
