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

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.ServerId
import com.protonvpn.android.redesign.base.ui.ActiveDot
import com.protonvpn.android.redesign.base.ui.Flag
import com.protonvpn.android.redesign.base.ui.GatewayIndicator
import com.protonvpn.android.redesign.base.ui.InfoButton
import com.protonvpn.android.redesign.base.ui.InfoType
import com.protonvpn.android.redesign.base.ui.ServerLoadBar
import com.protonvpn.android.redesign.base.ui.thenNotNull
import com.protonvpn.android.redesign.base.ui.unavailableServerAlpha
import com.protonvpn.android.redesign.search.TextMatch
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.ui.iconRes
import com.protonvpn.android.redesign.vpn.ui.label
import com.protonvpn.android.redesign.vpn.ui.viaCountry
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionNorm
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.presentation.R as CoreR

/**
 * A row container for server group items.
 * Its main role is to provide layout and accessibility for actions:
 * - the main action on the whole row
 * - optional "open" button at the end. Its click area covers the entire area at the end of the row to prevent
 *   misclicks, however its ripple is smaller to preserve visual layout.
 */
@Composable
private fun ServerGroupItemRow(
    @StringRes rowClickLabel: Int,
    onRowClick: (() -> Unit)?,
    onOpen: (() -> Unit)?,
    isUnavailable: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val edgePadding = 16.dp
    val customAccessibilityActions = when {
        onOpen != null -> listOf(
            CustomAccessibilityAction(stringResource(R.string.accessibility_menu_action_open)) { onOpen(); true }
        )
        else -> emptyList()
    }
    val clickable = onRowClick?.let {
        Modifier.clickable(
            onClick = onRowClick,
            onClickLabel = stringResource(rowClickLabel),
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .semantics(mergeDescendants = true) {  // Merge in case it's not clickable.
                customActions = customAccessibilityActions
            }
            .thenNotNull(clickable)
            .unavailableServerAlpha(isUnavailable)
            .padding(start = edgePadding, end = edgePadding.takeIf { onOpen == null } ?: 0.dp)
    ) {
        content()

        if (onOpen != null) {
            // The open button has larger click area and no padding on the edge to avoid accidental clicks on the
            // row itself.
            val interactionSource = remember { MutableInteractionSource() }
            val iconOverflow = 8.dp // How much the icon sticks out into edgePadding
            Box(
                modifier = Modifier
                    .height(IntrinsicSize.Max)
                    .clearAndSetSemantics {} // Accessibility handled via semantics on the whole row.
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null, // Indication only on the icon.
                        onClick = onOpen
                    )
                    .padding(end = edgePadding - iconOverflow)
            ) {
                Icon(
                    painterResource(CoreR.drawable.ic_proton_three_dots_horizontal),
                    tint = ProtonTheme.colors.iconNorm,
                    modifier = Modifier
                        .clip(CircleShape)
                        .indication(interactionSource, ripple())
                        .padding(16.dp),
                    contentDescription = null // Accessibility handled via semantics on the whole row.
                )
            }
        }
    }
}

@Composable
fun ServerGroupItem(
    item: ServerGroupUiItem.ServerGroup,
    onItemOpen: (ServerGroupUiItem.ServerGroup) -> Unit,
    onItemClick: (ServerGroupUiItem.ServerGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    ServerGroupItemRow(
        rowClickLabel = item.clickLabel(),
        onRowClick = { onItemClick(item) },
        onOpen = { onItemOpen(item) }.takeIf { item.canOpen },
        isUnavailable = !item.available || item.data.inMaintenance,
        modifier = modifier.heightIn(min = 64.dp),
    ) {
        item.Icon(Modifier.padding(end = 12.dp))
        Column(
            Modifier
                .weight(1f)
                // The row will maintain min height but if text is very large it will expand to accommodate the
                // contents with this padding.
                .padding(vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val textMatch = item.data.textMatch
                if (textMatch != null) {
                    MatchedText(
                        textMatch,
                        style = ProtonTheme.typography.defaultNorm
                    )
                } else {
                    Text(
                        text = item.label(),
                        style = ProtonTheme.typography.defaultNorm
                    )
                }
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
            if (item.data is ServerGroupItemData.Server) {
                FeaturesAndLoad(
                    item.data,
                    invisibleLoad = item.data.inMaintenance // Lay out invisible load for alignment.
                )
            }
            if (item.data.inMaintenance) {
                Icon(
                    painterResource(id = CoreR.drawable.ic_proton_wrench),
                    contentDescription = stringResource(R.string.accessibility_item_in_maintenance),
                    modifier = Modifier
                        .padding(end = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MatchedText(
    match: TextMatch,
    modifier: Modifier = Modifier,
    style: TextStyle = ProtonTheme.typography.body1Regular,
    color: Color = ProtonTheme.colors.textWeak,
    matchedColor: Color = ProtonTheme.colors.textNorm,
) {
    val annotatedString = buildAnnotatedString {
        append(match.fullText)
        addStyle(
            style = SpanStyle(color = matchedColor),
            start = match.index,
            end = match.index + match.length
        )
    }
    Text(
        text = annotatedString,
        style = style,
        color = color,
        modifier = modifier
            .semantics { testTagsAsResourceId = true }
            .testTag("searchResult")
    )
}

@Composable
private fun ServerGroupUiItem.ServerGroup.label() : String = when (data) {
    is ServerGroupItemData.Country -> data.countryId.label()
    is ServerGroupItemData.City -> data.name
    is ServerGroupItemData.Server -> data.name
    is ServerGroupItemData.Gateway -> data.gatewayName
}

@Composable
private fun ServerGroupUiItem.ServerGroup.subLabel() = when (data) {
    is ServerGroupItemData.Country ->
        data.entryCountryId?.takeIf { !it.isFastest }?.let { viaCountry(it) }
    is ServerGroupItemData.City -> null
    is ServerGroupItemData.Server -> null
    is ServerGroupItemData.Gateway -> null
}

@Composable
private fun ServerGroupUiItem.ServerGroup.Icon(modifier: Modifier) {
    when (data) {
        is ServerGroupItemData.Country -> Flag(
            modifier = modifier,
            exitCountry = data.countryId,
            entryCountry = data.entryCountryId,
        )
        is ServerGroupItemData.City -> Icon(
            modifier = modifier,
            painter = painterResource(id = CoreR.drawable.ic_proton_map_pin),
            contentDescription = null
        )
        is ServerGroupItemData.Gateway -> GatewayIndicator(
            country = data.countryId,
            modifier = modifier
        )
        is ServerGroupItemData.Server -> if (data.gatewayName != null) {
            Flag(
                modifier = modifier,
                exitCountry = data.countryId,
                entryCountry = data.entryCountryId,
            )
        }
    }
}

@Composable
private fun FeaturesAndLoad(
    server: ServerGroupItemData.Server,
    modifier: Modifier = Modifier,
    invisibleLoad: Boolean,
) {
    Row(modifier) {
        ServerFeaturesRow(server.serverFeatures, server.isVirtualLocation)
        LoadInfo(
            server.loadPercent,
            if (invisibleLoad) Modifier.alpha(0f) else Modifier
        )
    }
}

@Composable
private fun ServerFeaturesRow(features: Set<ServerFeature>, isVirtualLocation: Boolean) {
    if (features.isNotEmpty()) {
        Row(modifier = Modifier.padding(start = 12.dp)) {
            val iconModifier = Modifier
                .padding(horizontal = 4.dp)
                .size(14.dp)
            val iconTint = ProtonTheme.colors.iconWeak
            if (isVirtualLocation) {
                val description = stringResource(R.string.server_feature_content_description_smart_routing)
                Icon(
                    modifier = iconModifier,
                    painter = painterResource(id = CoreR.drawable.ic_proton_globe),
                    tint = iconTint,
                    contentDescription = description
                )
            }
            features.forEach { feature ->
                val description = feature.contentDescription()
                Icon(
                    modifier = iconModifier,
                    painter = painterResource(id = feature.iconRes()),
                    tint = iconTint,
                    contentDescription = description
                )
            }
        }
    }
}

@Composable
private fun ServerFeature.contentDescription(): String {
    val resourceId = when(this) {
        ServerFeature.P2P -> R.string.server_feature_content_description_p2p
        ServerFeature.Tor -> R.string.server_feature_content_description_tor
    }
    return stringResource(resourceId)
}

@Composable
private fun LoadInfo(loadPercent: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ServerLoadBar(progress = loadPercent / 100f)
        Box {
            // Invisible view to reserve space for 100% load text
            LoadPercentText(loadPercent = 100, alpha = 0f)
            val description = stringResource(R.string.country_filter_server_load_content_description, loadPercent)
            val semanticsModifier = Modifier.semantics { contentDescription = description }
            LoadPercentText(loadPercent = loadPercent, alpha = 1f, semanticsModifier)
        }
    }
}

@Composable
private fun LoadPercentText(loadPercent: Int, alpha: Float, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier
            .alpha(alpha)
            .padding(start = 8.dp),
        text = stringResource(id = R.string.serverLoad, loadPercent),
        style = ProtonTheme.typography.body2Regular,
    )
}

private fun ServerGroupUiItem.ServerGroup.clickLabel() = when {
    available -> R.string.accessibility_action_connect
    else -> R.string.accessibility_action_upgrade
}

@Composable
fun CountryItemPreview(
    entry: CountryId? = null,
    inMaintenance: Boolean = false,
    available: Boolean = true,
    connected: Boolean = false,
    match: TextMatch? = null
) {
    ServerGroupItem(
        modifier = Modifier.padding(6.dp),
        item = ServerGroupUiItem.ServerGroup(
            data = ServerGroupItemData.Country(
                countryId = CountryId("us"),
                entryCountryId = entry,
                inMaintenance = inMaintenance,
                tier = 0,
                textMatch = match
            ),
            available = available,
            connected = connected,
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
    ServerGroupItem(
        item = ServerGroupUiItem.ServerGroup(
            data = ServerGroupItemData.City(
                countryId = CountryId("us"),
                cityStateId = CityStateId("Arizona", isState = true),
                name = "Arizona",
                inMaintenance = inMaintenance,
                tier = 0
            ),
            available = available,
            connected = connected,
        ),
        onItemOpen = {},
        onItemClick = {}
    )
}

@Composable
fun ServerGroupHeader(
    item: ServerGroupUiItem.Header,
    onOpenInfo: (InfoType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(id = item.labelRes, item.count),
            style = ProtonTheme.typography.body2Regular,
            color = ProtonTheme.colors.textWeak,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        )
        if (item.info != null) {
            InfoButton(item.info, onOpenInfo)
        }
    }
}

@ProtonVpnPreview
@Composable
private fun ServerGroupItemRowWithOpenPreview() {
    ProtonVpnPreview {
        Surface(
            color = ProtonTheme.colors.backgroundNorm
        ) {
            ServerGroupItemRow(
                rowClickLabel = R.string.accessibility_action_connect,
                onRowClick = {},
                onOpen = {},
                isUnavailable = false,
                modifier = Modifier
                    .heightIn(64.dp)
                    .fillMaxWidth(),
            ) {
                Text("Some content", modifier = Modifier.weight(1f))
            }
        }
    }
}

@ProtonVpnPreview
@Composable
fun SectionHeaderPreview() {
    ProtonVpnPreview {
        ServerGroupHeader(
            item = ServerGroupUiItem.Header(
                labelRes = R.string.country_filter_all_list_header,
                count = 10,
                info = InfoType.SecureCore,
            ),
            onOpenInfo = {},
            modifier = Modifier.background(ProtonTheme.colors.backgroundSecondary),
        )
    }
}

@Composable
fun ServerItemPreview(
    inMaintenance: Boolean = false,
    available: Boolean = true,
    load: Int = 50
) {
    ServerGroupItem(
        item = ServerGroupUiItem.ServerGroup(
            data = ServerGroupItemData.Server(
                countryId = CountryId("us"),
                serverId = ServerId("us-ny-01"),
                name = "US-NY#1",
                loadPercent = load,
                serverFeatures = setOf(ServerFeature.P2P, ServerFeature.Tor),
                isVirtualLocation = true,
                inMaintenance = inMaintenance,
                tier = 0,
                entryCountryId = null,
                gatewayName = null
            ),
            available = available,
            connected = true,
        ),
        onItemOpen = {},
        onItemClick = {}
    )
}

@ProtonVpnPreview
@Composable
fun CountryItemPreviews() {
    ProtonVpnPreview {
        Column {
            CountryItemPreview()
            CountryItemPreview(entry = CountryId("is"), connected = true)
            CountryItemPreview(entry = CountryId("se"), inMaintenance = true)
            CountryItemPreview(available = false)
            CountryItemPreview(match = TextMatch(0, 5, "United States"))
        }
    }
}

@ProtonVpnPreview
@Composable
fun CityItemPreviews() {
    ProtonVpnPreview {
        Column {
            CityItemPreview(connected = true)
            CityItemPreview(inMaintenance = true)
            CityItemPreview(available = false)
        }
    }
}

@ProtonVpnPreview
@Composable
fun ServerItemPreviews() {
    ProtonVpnPreview {
        Column {
            ServerItemPreview(load = 80)
            ServerItemPreview(load = 100)
            ServerItemPreview(inMaintenance = true)
            ServerItemPreview(available = false)
        }
    }
}

@ProtonVpnPreview
@Composable
fun GatewayItemPreviews() {
    ProtonVpnPreview {
        Column {
            ServerGroupItem(
                item = ServerGroupUiItem.ServerGroup(
                    data = ServerGroupItemData.Gateway(
                        gatewayName = "MyCompany",
                        inMaintenance = false,
                        tier = 0
                    ),
                    available = true,
                    connected = true,
                ),
                onItemOpen = {},
                onItemClick = {}
            )
            ServerGroupItem(
                item = ServerGroupUiItem.ServerGroup(
                    data = ServerGroupItemData.Server(
                        countryId = CountryId("us"),
                        serverId = ServerId("gateway-us-01"),
                        name = "MyCompany#1",
                        loadPercent = 50,
                        serverFeatures = setOf(),
                        isVirtualLocation = false,
                        inMaintenance = false,
                        tier = 0,
                        entryCountryId = null,
                        gatewayName = "MyCompany"
                    ),
                    available = true,
                    connected = true,
                ),
                onItemOpen = {},
                onItemClick = {}
            )
        }
    }
}
