/*
 * Copyright (c) 2025. Proton AG
 *
 *  This file is part of ProtonVPN.
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

package com.protonvpn.android.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.base.ui.ProtonAlert
import com.protonvpn.android.redesign.base.ui.previews.PreviewBooleanProvider
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun VpnUpdateBanner(
    message: String,
    viewState: AppUpdateBannerState,
    onAppUpdate: (AppUpdateInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = viewState is AppUpdateBannerState.Shown
    ) {
        var vpnConnectedWarningShown by rememberSaveable { mutableStateOf(false) }
        val launchUpdate = {
            if (viewState is AppUpdateBannerState.Shown) {
                onAppUpdate(viewState.appUpdateInfo)
            }
        }
        val onClick = {
            if ((viewState as? AppUpdateBannerState.Shown)?.showVpnConnectedWarning == true) {
                vpnConnectedWarningShown = true
            } else {
                launchUpdate()
            }
        }
        UpdateBanner(
            message = message,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
        if (vpnConnectedWarningShown) {
            val dismissDialog = { vpnConnectedWarningShown = false }
            ProtonAlert(
                title = stringResource(R.string.update_banner_warning_vpn_connected_title),
                text = stringResource(R.string.update_banner_warning_vpn_connected_message),
                confirmLabel = stringResource(R.string.dialogContinue),
                dismissLabel = stringResource(R.string.cancel),
                onConfirm = { launchUpdate(); dismissDialog() },
                onDismissRequest = dismissDialog,
            )
        }
    }
}

@Composable
private fun UpdateBanner(
    message: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(shape = ProtonTheme.shapes.large)
            .background(color = ProtonTheme.colors.backgroundSecondary)
            .clickable(onClick = onClick)
            .padding(all = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        Image(
            painter = painterResource(id = R.drawable.update_proton_vpn_badge),
            contentDescription = null,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            Column(
                modifier = Modifier.weight(weight = 1f),
                verticalArrangement = Arrangement.spacedBy(space = 4.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.update_screen_title),
                    color = ProtonTheme.colors.textNorm,
                    style = ProtonTheme.typography.body2Regular,
                )

                Text(
                    text = message,
                    color = ProtonTheme.colors.textWeak,
                    style = ProtonTheme.typography.body2Regular,
                )
            }

            Icon(
                painter = painterResource(id = CoreR.drawable.ic_proton_chevron_tiny_right),
                contentDescription = null,
                tint = ProtonTheme.colors.iconWeak,
            )
        }
    }
}

@Preview
@Composable
private fun UpdateBannerPreview(
    @PreviewParameter(PreviewBooleanProvider::class) isDark: Boolean,
) {
    ProtonVpnPreview(isDark = isDark) {
        UpdateBanner(
            message = stringResource(id = R.string.update_screen_description),
            onClick = {},
        )
    }
}
 