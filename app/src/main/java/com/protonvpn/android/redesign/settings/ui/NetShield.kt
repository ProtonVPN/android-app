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
import com.protonvpn.android.netshield.NetShieldProtocol
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionWeak

@Composable
fun NetShieldSetting(
    onClose: () -> Unit,
    value: Boolean,
    onLearnMore: () -> Unit,
    onNetShieldChange: (NetShieldProtocol) -> Unit,
) {
    SubSetting(
        title = stringResource(id = R.string.netshield_feature_name),
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
            name = stringResource(id = R.string.netshield_feature_name),
            description = stringResource(
                id = R.string.netshield_settings_description_not_html,
                stringResource(id = R.string.learn_more)
            ),
            annotatedPart = stringResource(id = R.string.learn_more),
            onAnnotatedClick = onLearnMore,
            value = value,
            onToggle = { onNetShieldChange(if (!value) NetShieldProtocol.ENABLED_EXTENDED else NetShieldProtocol.DISABLED) },
        )

        Text(
            text = stringResource(id = R.string.netshield_setting_warning),
            style = ProtonTheme.typography.captionWeak,
            modifier = Modifier.padding(16.dp)
        )
    }
}
