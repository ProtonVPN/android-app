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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionWeak

@Composable
fun NetShieldSetting(
    onClose: () -> Unit,
    netShield: SettingsViewModel.SettingViewState.NetShield,
    onLearnMore: () -> Unit,
    onNetShieldToggle: () -> Unit,
) {
    SubSetting(
        title = stringResource(id = netShield.titleRes),
        onClose = onClose
    ) {
        Image(
            painter = painterResource(id = R.drawable.netshield_settings_promo),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        SettingsToggleItem(
            netShield,
            onToggle = onNetShieldToggle,
            onAnnotatedClick = onLearnMore
        )

        Text(
            text = stringResource(id = R.string.netshield_setting_warning),
            style = ProtonTheme.typography.captionWeak,
            modifier = Modifier.padding(16.dp)
        )
    }
}
