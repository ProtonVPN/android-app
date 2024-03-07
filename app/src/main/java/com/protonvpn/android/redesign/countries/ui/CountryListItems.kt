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

package com.protonvpn.android.redesign.countries.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.ServerId
import com.protonvpn.android.redesign.base.ui.ActiveDot
import com.protonvpn.android.redesign.base.ui.Flag
import com.protonvpn.android.redesign.base.ui.unavailableServerAlpha
import com.protonvpn.android.redesign.home_screen.ui.ServerLoadBar
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.ui.viaCountry
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionNorm
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.presentation.R as CoreR

@Composable
fun CountryListItem(
    item: CountryListItemState,
    onItemOpen: (CountryListItemState) -> Unit,
    onItemClick: (CountryListItemState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable { onItemClick(item) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val alphaModifier = Modifier
            .unavailableServerAlpha(!item.available || item.data.inMaintenance)
        item.Icon(alphaModifier.padding(end = 12.dp))
        Column(Modifier
            .weight(1f)
            .padding(vertical = 20.dp)
            .then(alphaModifier)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.label,
                    style = ProtonTheme.typography.defaultNorm
                )
                if (item.connected) {
                    ActiveDot(modifier = Modifier.padding(start = 8.dp))
                }
            }
            item.subLabel()?.let { subLabel ->
                Text(
                    text = subLabel,
                    style = ProtonTheme.typography.captionNorm
                )
            }
        }
        Box(
            contentAlignment = Alignment.CenterEnd,
        ) {
            item.Info(alphaModifier)
            item.openIconRes?.let { iconRes ->
                Icon(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = item.canOpen) { onItemOpen(item) }
                        .padding(8.dp),
                    tint = if (item.data.inMaintenance)
                        ProtonTheme.colors.iconWeak else ProtonTheme.colors.iconNorm,
                    painter = painterResource(id = iconRes),
                    contentDescription = null //TODO: accessibility
                )
            }
        }
    }
}

@Composable
private fun CountryListItemState.subLabel() = when (data) {
    is CountryListItemData.Country -> data.entryCountryId?.let { viaCountry(it) }
    is CountryListItemData.City -> null
    is CountryListItemData.Server -> null
}

@Composable
private fun CountryListItemState.Icon(modifier: Modifier) {
    when (data) {
        is CountryListItemData.Country -> Flag(
            modifier = modifier,
            exitCountry = data.countryId,
            entryCountry = data.entryCountryId,
        )
        is CountryListItemData.City -> Icon(
            modifier = modifier,
            painter = painterResource(id = CoreR.drawable.ic_proton_map_pin),
            contentDescription = null
        )
        is CountryListItemData.Server -> {}
    }
}

@Composable
private fun CountryListItemState.Info(modifier: Modifier) {
    if (data is CountryListItemData.Server) {
        Row(modifier) {
            ServerFeaturesRow(data.serverFeatures)
            LoadInfo(data.loadPercent, data.inMaintenance)
        }
    }
}

@Composable
private fun ServerFeaturesRow(features: Set<ServerFeature>) {
    if (features.isNotEmpty()) {
        Row(modifier = Modifier.padding(start = 12.dp)) {
            features.forEach { feature ->
                Icon(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(14.dp),
                    painter = painterResource(id = feature.iconRes),
                    tint = ProtonTheme.colors.iconWeak,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
private fun LoadInfo(loadPercent: Int, inMaintenance: Boolean) {
    // Don't show load if in maintenance but reserve space for it
    val loadAlpha = if (inMaintenance) 0f else 1f
    Row(
        modifier = Modifier
            .padding(start = 20.dp)
            .alpha(loadAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ServerLoadBar(progress = loadPercent / 100f)
        Box {
            // Invisible view to reserve space for 100% load text
            LoadPercentText(loadPercent = 100, alpha = 0f)
            LoadPercentText(loadPercent = loadPercent, alpha = 1f)
        }
    }
}

@Composable
private fun LoadPercentText(loadPercent: Int, alpha: Float) {
    Text(
        modifier = Modifier
            .alpha(alpha)
            .padding(start = 8.dp),
        text = stringResource(id = R.string.serverLoad, loadPercent),
        style = ProtonTheme.typography.captionNorm,
    )
}

val CountryListItemState.openIconRes get() = when {
    data.inMaintenance -> CoreR.drawable.ic_proton_wrench
    canOpen -> CoreR.drawable.ic_proton_three_dots_horizontal
    else -> null
}

val ServerFeature.iconRes get() = when (this) {
    ServerFeature.P2P -> CoreR.drawable.ic_proton_arrow_right_arrow_left
    ServerFeature.Tor -> CoreR.drawable.ic_proton_brand_tor
}

@Composable
fun CountryItemPreview(
    entry: CountryId? = null,
    inMaintenance: Boolean = false,
    available: Boolean = true,
    connected: Boolean = false,
) {
    CountryListItem(
        modifier = Modifier.padding(6.dp),
        item = CountryListItemState(
            data = CountryListItemData.Country(
                countryId = CountryId("us"),
                entryCountryId = entry,
                inMaintenance = inMaintenance,
                tier = 0
            ),
            available = available,
            connected = connected,
            label = "United States",
        ),
        onItemOpen = {},
        onItemClick = {}
    )
}

@Composable
fun CityItemPreview(
    inMaintenance: Boolean = false,
    available: Boolean = true,
    connected: Boolean = false,
) {
    CountryListItem(
        item = CountryListItemState(
            data = CountryListItemData.City(
                countryId = CountryId("us"),
                cityStateId = CityStateId("Arizona", isState = true),
                name = "Arizona",
                inMaintenance = inMaintenance,
                tier = 0
            ),
            available = available,
            connected = connected,
            label = "Arizona",
        ),
        onItemOpen = {},
        onItemClick = {}
    )
}

@Composable
fun ServerItemPreview(
    inMaintenance: Boolean = false,
    available: Boolean = true,
    load: Int = 50
) {
    CountryListItem(
        item = CountryListItemState(
            data = CountryListItemData.Server(
                countryId = CountryId("us"),
                serverId = ServerId("us-ny-01"),
                name = "US-NY#1",
                loadPercent = load,
                serverFeatures = setOf(ServerFeature.P2P, ServerFeature.Tor),
                isVirtualLocation = true,
                inMaintenance = inMaintenance,
                tier = 0
            ),
            available = available,
            connected = true,
            label = "US-NY#1",
        ),
        onItemOpen = {},
        onItemClick = {}
    )
}

@Preview
@Composable
fun CountryItemPreviews() {
    VpnTheme(isDark = true) {
        Column(modifier = Modifier.background(ProtonTheme.colors.backgroundNorm)) {
            CountryItemPreview()
            CountryItemPreview(entry = CountryId("is"), connected = true)
            CountryItemPreview(entry = CountryId("se"), inMaintenance = true)
            CountryItemPreview(available = false)
        }
    }
}

@Preview
@Composable
fun CityItemPreviews() {
    VpnTheme(isDark = true) {
        Column(modifier = Modifier.background(ProtonTheme.colors.backgroundNorm)) {
            CityItemPreview(connected = true)
            CityItemPreview(inMaintenance = true)
            CityItemPreview(available = false)
        }
    }
}

@Preview
@Composable
fun ServerItemPreviews() {
    VpnTheme(isDark = true) {
        Column(modifier = Modifier.background(ProtonTheme.colors.backgroundNorm)) {
            ServerItemPreview(load = 80)
            ServerItemPreview(load = 100)
            ServerItemPreview(inMaintenance = true)
            ServerItemPreview(available = false)
        }
    }
}