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

package com.protonvpn.android.redesign.base.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun InfoButton(
    info: InfoType,
    onOpenInfo: (InfoType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable { onOpenInfo(info) }
            .padding(all = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(id = info.label),
            style = ProtonTheme.typography.body2Medium,
            color = ProtonTheme.colors.textWeak,
            modifier = Modifier.padding(end = 5.dp)
        )
        Icon(
            painter = painterResource(id = CoreR.drawable.ic_info_circle),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = ProtonTheme.colors.iconWeak
        )
    }
}

private val InfoType.label: Int get() = when(this) {
    InfoType.SecureCore,
    InfoType.VpnSpeed,
    InfoType.Protocol,
    InfoType.Tor,
    InfoType.P2P,
    InfoType.Streaming,
    InfoType.IPv6Traffic,
    is InfoType.IpAddress,
    InfoType.SmartRouting -> R.string.country_filter_info_label
    InfoType.ServerLoad -> R.string.server_load_title
    InfoType.Profiles -> R.string.generic_info_label
}
