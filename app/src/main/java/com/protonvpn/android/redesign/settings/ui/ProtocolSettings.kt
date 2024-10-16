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

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.AnnotatedClickableText
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.redesign.base.ui.SettingsRadioItemSmall
import com.protonvpn.android.vpn.ProtocolSelection
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun ProtocolSettings(
    onClose: () -> Unit,
    protocolViewState: SettingsViewModel.SettingViewState.Protocol,
    onLearnMore: () -> Unit,
    onProtocolSelected: (ProtocolSelection) -> Unit,
) {
    SubSetting(
        title = stringResource(protocolViewState.titleRes),
        onClose = onClose
    ) {
        ProtocolSettingsList(
            currentProtocol = protocolViewState.value,
            onProtocolSelected = onProtocolSelected,
        )

        val footerPadding = Modifier.padding(top = 12.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
        if (protocolViewState.annotationRes != null) {
            AnnotatedClickableText(
                fullText = protocolViewState.descriptionText(),
                annotatedPart = stringResource(protocolViewState.annotationRes),
                onAnnotatedClick = onLearnMore,
                style = ProtonTheme.typography.body2Regular,
                color = ProtonTheme.colors.textWeak,
                modifier = footerPadding,
            )
        } else {
            Text(
                text = protocolViewState.descriptionText(),
                style = ProtonTheme.typography.body2Regular,
                color = ProtonTheme.colors.textWeak,
                modifier = footerPadding,
            )
        }
    }
}

@Composable
fun ProtocolSettingsList(
    currentProtocol: ProtocolSelection,
    onProtocolSelected: (ProtocolSelection) -> Unit,
    modifier: Modifier = Modifier,
    horizontalContentPadding: Dp = 16.dp,
) {
    Column(modifier = modifier) {
        ProtocolItem(
            itemProtocol = ProtocolSelection.SMART,
            title = R.string.settings_protocol_smart_title,
            description = R.string.settings_protocol_smart_description,
            onProtocolSelected = onProtocolSelected,
            selectedProtocol = currentProtocol,
            horizontalContentPadding = horizontalContentPadding,
            trailingTitleContent = {
                ProtocolBadge(stringResource(R.string.settings_protocol_badge_recommended))
            }
        )

        SettingsSectionHeading(
            text = stringResource(R.string.settings_protocol_section_speed),
            modifier = Modifier.padding(horizontal = horizontalContentPadding)
        )
        ProtocolItem(
            itemProtocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP),
            title = R.string.settings_protocol_wireguard_title,
            description = R.string.settings_protocol_wireguard_udp_description,
            onProtocolSelected = onProtocolSelected,
            selectedProtocol = currentProtocol,
            horizontalContentPadding = horizontalContentPadding,
        )
        ProtocolItem(
            itemProtocol = ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.UDP),
            title = R.string.settings_protocol_openvpn_title,
            description = R.string.settings_protocol_openvpn_udp_description,
            onProtocolSelected = onProtocolSelected,
            selectedProtocol = currentProtocol,
            horizontalContentPadding = horizontalContentPadding,
        )

        SettingsSectionHeading(
            text = stringResource(R.string.settings_protocol_section_reliability),
            modifier = Modifier.padding(horizontal = horizontalContentPadding)
        )
        ProtocolItem(
            itemProtocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TCP),
            title = R.string.settings_protocol_wireguard_title,
            description = R.string.settings_protocol_wireguard_tcp_description,
            onProtocolSelected = onProtocolSelected,
            selectedProtocol = currentProtocol,
            horizontalContentPadding = horizontalContentPadding,
        )
        ProtocolItem(
            itemProtocol = ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.TCP),
            title = R.string.settings_protocol_openvpn_title,
            description = R.string.settings_protocol_openvpn_tcp_description,
            onProtocolSelected = onProtocolSelected,
            selectedProtocol = currentProtocol,
            horizontalContentPadding = horizontalContentPadding,
        )
        ProtocolItem(
            itemProtocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS),
            title = R.string.settings_protocol_stealth_title,
            description = R.string.settings_protocol_stealth_description,
            onProtocolSelected = onProtocolSelected,
            selectedProtocol = currentProtocol,
            horizontalContentPadding = horizontalContentPadding,
        )
    }
}

@Composable
fun ProtocolItem(
    itemProtocol: ProtocolSelection,
    @StringRes title: Int,
    @StringRes description: Int,
    onProtocolSelected: (ProtocolSelection) -> Unit,
    selectedProtocol: ProtocolSelection,
    modifier: Modifier = Modifier,
    horizontalContentPadding: Dp = 16.dp,
    trailingTitleContent: (@Composable () -> Unit)? = null,
) {
    SettingsRadioItemSmall(
        title = stringResource(id = title),
        description = stringResource(id = description),
        selected = itemProtocol == selectedProtocol,
        onSelected = { onProtocolSelected(itemProtocol) },
        horizontalContentPadding = horizontalContentPadding,
        modifier = modifier,
        trailingTitleContent = trailingTitleContent,
    )
}

@Composable
fun ProtocolBadge(
    text: String,
    textColor: Color = ProtonTheme.colors.textNorm,
    borderColor: Color = ProtonTheme.colors.separatorNorm,
) {
    Text(
        text = text.uppercase(),
        style = ProtonTheme.typography.overlineMedium,
        color = textColor,
        modifier = Modifier
            .border(width = 1.dp, color = borderColor, shape = ProtonTheme.shapes.small)
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .background(ProtonTheme.colors.backgroundSecondary),
        )
}
