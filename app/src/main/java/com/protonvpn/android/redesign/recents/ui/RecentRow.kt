/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.redesign.recents.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.ConnectIntentIcon
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentAvailability
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentRow
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

data class RecentItemViewState(
    val id: Long,
    val connectIntent: ConnectIntentViewState,
    val isPinned: Boolean,
    val isConnected: Boolean,
    val availability: ConnectIntentAvailability,
)

@Composable
fun RecentRow(
    item: RecentItemViewState,
    onClick: () -> Unit,
    onRecentSettingOpen: (RecentItemViewState) -> Unit,
    modifier: Modifier = Modifier
) {
    val pinnedStateDescription = stringResource(id = R.string.recent_action_accessibility_state_pinned)
    val iconRes =
        if (item.isPinned) CoreR.drawable.ic_proton_pin_filled else CoreR.drawable.ic_proton_clock_rotate_left
    ConnectIntentRow(
        availability = item.availability,
        connectIntent = item.connectIntent,
        isConnected = item.isConnected,
        onClick = onClick,
        onOpen = { onRecentSettingOpen(item) },
        leadingComposable = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    tint = ProtonTheme.colors.iconWeak,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp)
                )
                ConnectIntentIcon(item.connectIntent.primaryLabel)
            }
        },
        modifier = modifier,
        semanticsStateDescription = if (item.isPinned) pinnedStateDescription else null
    )
}

@Preview
@Composable
private fun PreviewRecent() {
    VpnTheme {
        var isPinned by remember { mutableStateOf(false) }
        RecentRow(
            item = RecentItemViewState(
                id = 0,
                ConnectIntentViewState(
                    primaryLabel = ConnectIntentPrimaryLabel.Country(CountryId.switzerland, CountryId.sweden),
                    secondaryLabel = ConnectIntentSecondaryLabel.SecureCore(null, CountryId.sweden),
                    serverFeatures = emptySet()
                ),
                isPinned = isPinned,
                isConnected = true,
                availability = ConnectIntentAvailability.AVAILABLE_OFFLINE,
            ),
            onClick = {},
            onRecentSettingOpen = {},
            modifier = Modifier.fillMaxWidth()
        )
    }
}
