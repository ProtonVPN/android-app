/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.redesign.settings.ui.connectionpreferences

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.base.ui.previews.PreviewBooleanProvider
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun ConnectionPreferencesFreeContent(
    onDefaultConnectionClick: () -> Unit,
    onExcludeLocationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        item(key = "default_connection_upsell_key") {
            ConnectionPreferencesUpsellSection(
                modifier = Modifier.fillMaxWidth(),
                titleText = stringResource(id = R.string.settings_default_connection_title),
                subtitleText = stringResource(id = R.string.fastest_free_server),
                onClick = onDefaultConnectionClick,
            )
        }

        item(key = "exclude_locations_upsell_key") {
            ConnectionPreferencesUpsellSection(
                modifier = Modifier.fillMaxWidth(),
                titleText = stringResource(id = R.string.settings_excluded_locations_title),
                subtitleText = stringResource(id = R.string.settings_excluded_locations_description),
                onClick = onExcludeLocationClick,
            )
        }
    }
}

@Composable
private fun ConnectionPreferencesUpsellSection(
    titleText: String,
    subtitleText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(all = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        Column(
            modifier = Modifier.weight(weight = 1f, fill = true),
            verticalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            Text(
                text = titleText,
                style = ProtonTheme.typography.body1Regular,
            )

            Text(
                text = subtitleText,
                color = ProtonTheme.colors.textWeak,
                style = ProtonTheme.typography.body2Regular,
            )
        }

        Image(
            painter = painterResource(id = R.drawable.vpn_plus_badge),
            contentDescription = null,
        )
    }
}


@Preview
@Composable
private fun ConnectionPreferencesFreeContentPreview(
    @PreviewParameter(PreviewBooleanProvider::class) isDark: Boolean,
) {
    ProtonVpnPreview(isDark = isDark) {
        ConnectionPreferencesFreeContent(
            onDefaultConnectionClick = {},
            onExcludeLocationClick = {},
        )
    }
}
