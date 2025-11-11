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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.AnnotatedClickableText
import com.protonvpn.android.base.ui.LabelBadge
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.redesign.base.ui.ProtonAlert
import com.protonvpn.android.redesign.base.ui.SettingsRadioItemSmall
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.openUrl
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
        protocolViewState.descriptionText()?.let { descriptionText ->
            if (protocolViewState.annotationRes != null) {
                AnnotatedClickableText(
                    fullText = descriptionText,
                    annotatedPart = stringResource(protocolViewState.annotationRes),
                    onAnnotatedClick = onLearnMore,
                    style = ProtonTheme.typography.body2Regular,
                    annotatedStyle = ProtonTheme.typography.body2Medium,
                    color = ProtonTheme.colors.textWeak,
                    modifier = footerPadding,
                )
            } else {
                Text(
                    text = descriptionText,
                    style = ProtonTheme.typography.body2Regular,
                    color = ProtonTheme.colors.textWeak,
                    modifier = footerPadding,
                )
            }
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
    val openVpnDeprecationDialogForTransmission =
        rememberSaveable(currentProtocol) { mutableStateOf<TransmissionProtocol?>(null) }

    openVpnDeprecationDialogForTransmission.value?.let { transmission ->
        OpenVpnDeprecationDialog(
            transmission,
            onProtocolSelected,
            dismissDialog = { openVpnDeprecationDialogForTransmission.value = null }
        )
    }

    Column(modifier = modifier) {
        ProtocolItem(
            itemProtocol = ProtocolSelection.SMART,
            title = R.string.settings_protocol_smart_title,
            description = R.string.settings_protocol_smart_description,
            onProtocolSelected = onProtocolSelected,
            selectedProtocol = currentProtocol,
            horizontalContentPadding = horizontalContentPadding,
            trailingTitleContent = {
                LabelBadge(stringResource(R.string.settings_protocol_badge_recommended))
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
            onProtocolSelected = { openVpnDeprecationDialogForTransmission.value = TransmissionProtocol.UDP },
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
            onProtocolSelected = { openVpnDeprecationDialogForTransmission.value = TransmissionProtocol.TCP },
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
fun OpenVpnDeprecationDialog(
    transmission: TransmissionProtocol,
    selectProtocol: (ProtocolSelection) -> Unit,
    dismissDialog: () -> Unit,
) {
    val context = LocalContext.current
    val textHtml = stringResource(R.string.settings_protocol_openvpn_deprecation_dialog_description)
    val text = AnnotatedString.fromHtml(
        textHtml,
        linkStyles = TextLinkStyles(
            style = ProtonTheme.typography.body2Regular.copy(
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline
            ).toSpanStyle(),
        ),
        linkInteractionListener = {
            context.openUrl(Constants.URL_OPENVPN_DEPRECATION)
        }
    )
    ProtonAlert(
        title = stringResource(R.string.settings_protocol_openvpn_deprecation_dialog_title),
        text = text,
        textColor = ProtonTheme.colors.textWeak,
        onDismissRequest = dismissDialog,
        confirmLabel = stringResource(R.string.settings_protocol_openvpn_change_dialog_continue_openvpn),
        dismissLabel = stringResource(R.string.settings_protocol_openvpn_change_dialog_enable_smart),
        onConfirm = {
            selectProtocol(ProtocolSelection(VpnProtocol.OpenVPN, transmission))
            dismissDialog()
        },
        onDismissButton = {
            selectProtocol(ProtocolSelection.SMART)
            dismissDialog()
        }
    )
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
