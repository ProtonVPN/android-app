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

package com.protonvpn.android.tv.settings.protocol

import android.content.DialogInterface
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.tv.dialogs.TvAlertDialog
import com.protonvpn.android.tv.settings.TvSettingsHeader
import com.protonvpn.android.tv.settings.TvSettingsItemRadioSmall
import com.protonvpn.android.tv.settings.TvSettingsItemSwitch
import com.protonvpn.android.tv.ui.TvUiConstants
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.mapFromProtun
import com.protonvpn.android.vpn.mapToProtun
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun TvSettingsProtocolMain(
    selectedProtocol: ProtocolSelection,
    showProtun: Boolean,
    onSelected: (ProtocolSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
    ) {
        TvSettingsHeader(
            stringResource(R.string.settings_protocol_title),
            modifier = Modifier.padding(top = TvUiConstants.ScreenPaddingVertical)
        )
        val protonProtocolsEnabled by rememberSaveable(selectedProtocol) {
            mutableStateOf(selectedProtocol.vpn == VpnProtocol.ProTun)
        }
        fun wireguardProtocol(transmissionProtocol: TransmissionProtocol) = ProtocolSelection(
            if (protonProtocolsEnabled) VpnProtocol.ProTun else VpnProtocol.WireGuard,
            transmissionProtocol
        )

        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        val openVpnDeprecationDialogForTransmission =
            rememberSaveable(selectedProtocol) { mutableStateOf<TransmissionProtocol?>(null) }

        if (openVpnDeprecationDialogForTransmission.value != null) {
            TvAlertDialog(
                title = stringResource(R.string.settings_protocol_openvpn_deprecation_dialog_title),
                description = stringResource(R.string.settings_protocol_openvpn_deprecation_dialog_description_tv),
                confirmText = stringResource(R.string.settings_protocol_openvpn_change_dialog_enable_smart),
                dismissText = stringResource(R.string.settings_protocol_openvpn_change_dialog_continue_openvpn),
                onConfirm = {
                    onSelected(ProtocolSelection.SMART)
                    openVpnDeprecationDialogForTransmission.value = null
                },
                onDismiss = {
                    onSelected(ProtocolSelection(VpnProtocol.OpenVPN, openVpnDeprecationDialogForTransmission.value))
                    openVpnDeprecationDialogForTransmission.value = null
                },
                onDismissRequest = {
                    openVpnDeprecationDialogForTransmission.value = null
                },
                focusedButton = DialogInterface.BUTTON_POSITIVE
            )
        }

        LazyColumn {
            if (showProtun) {
                item {
                    TvSettingsItemSwitch(
                        title = stringResource(R.string.settings_protocol_proton_protocols),
                        trailingTitleContent = {
                            LabelBadge(
                                text = stringResource(R.string.settings_beta_label_badge),
                                textColor = ProtonTheme.colors.brandLighten40,
                                borderColor = ProtonTheme.colors.brandLighten40,
                            )
                        },
                        checked = selectedProtocol.vpn == VpnProtocol.ProTun,
                        onClick = {
                            onSelected(
                                if (protonProtocolsEnabled) {
                                    selectedProtocol.mapFromProtun()
                                } else {
                                    selectedProtocol.mapToProtun()
                                }
                            )
                        },
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                }
                item {
                    Text(
                        text = stringResource(id = R.string.settings_protocol_proton_protocols_description),
                        style = ProtonTheme.typography.body2Regular,
                        color = ProtonTheme.colors.textWeak,
                        modifier = Modifier
                            .padding(horizontal = TvUiConstants.SelectionPaddingHorizontal)
                            .padding(top = 12.dp, bottom = 16.dp)
                    )
                    Divider(
                        color = ProtonTheme.colors.separatorNorm,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            item {
                ProtocolItem(
                    itemProtocol = if (protonProtocolsEnabled) ProtocolSelection.SMART_PROTUN else ProtocolSelection.SMART,
                    title = R.string.settings_protocol_smart_title,
                    description = R.string.settings_protocol_smart_description,
                    onSelected = {
                        onSelected(
                            if (protonProtocolsEnabled) ProtocolSelection.SMART_PROTUN
                            else ProtocolSelection.SMART
                        )
                    },
                    selectedProtocol = selectedProtocol,
                    trailingTitleContent = {
                        LabelBadge(
                            text = stringResource(R.string.settings_protocol_badge_recommended),
                            textColor = ProtonTheme.colors.textNorm,
                            borderColor = ProtonTheme.colors.separatorNorm,
                        )
                    },
                    modifier = Modifier.focusRequester(focusRequester)
                )
            }

            item {
                TvSettingSectionHeading(stringResource(R.string.settings_protocol_section_speed))
            }
            item {
                ProtocolItem(
                    itemProtocol = wireguardProtocol(TransmissionProtocol.UDP),
                    title = R.string.settings_protocol_wireguard_title,
                    description = R.string.settings_protocol_wireguard_udp_description,
                    onSelected = onSelected,
                    selectedProtocol = selectedProtocol,
                )
            }
            if (!protonProtocolsEnabled) item {
                ProtocolItem(
                    itemProtocol = ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.UDP),
                    title = R.string.settings_protocol_openvpn_title,
                    description = R.string.settings_protocol_openvpn_udp_description,
                    onSelected = { openVpnDeprecationDialogForTransmission.value = TransmissionProtocol.UDP },
                    selectedProtocol = selectedProtocol,
                )
            }

            item {
                TvSettingSectionHeading(stringResource(R.string.settings_protocol_section_reliability))
            }
            item {
                ProtocolItem(
                    itemProtocol = wireguardProtocol(TransmissionProtocol.TCP),
                    title = R.string.settings_protocol_wireguard_title,
                    description = R.string.settings_protocol_wireguard_tcp_description,
                    onSelected = onSelected,
                    selectedProtocol = selectedProtocol,
                )
            }
            if (!protonProtocolsEnabled) item {
                ProtocolItem(
                    itemProtocol = ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.TCP),
                    title = R.string.settings_protocol_openvpn_title,
                    description = R.string.settings_protocol_openvpn_tcp_description,
                    onSelected = { openVpnDeprecationDialogForTransmission.value = TransmissionProtocol.TCP },
                    selectedProtocol = selectedProtocol,
                )
            }
            item {
                ProtocolItem(
                    itemProtocol = wireguardProtocol(TransmissionProtocol.TLS),
                    title = R.string.settings_protocol_stealth_title,
                    description = R.string.settings_protocol_stealth_description,
                    onSelected = onSelected,
                    selectedProtocol = selectedProtocol,
                )
            }

            item {
                VerticalSpacer(height = 12.dp)
                Text(
                    stringResource(R.string.settings_protocol_description_no_link),
                    style = ProtonTheme.typography.body2Regular,
                    color = ProtonTheme.colors.textWeak,
                    modifier = Modifier.padding(horizontal = TvUiConstants.SelectionPaddingHorizontal, vertical = 24.dp)
                )
            }

            // Always last
            item {
                VerticalSpacer(height = TvUiConstants.ScreenPaddingVertical)
            }
        }
    }
}

@Composable
private fun ProtocolItem(
    itemProtocol: ProtocolSelection,
    @StringRes title: Int,
    @StringRes description: Int,
    onSelected: (ProtocolSelection) -> Unit,
    selectedProtocol: ProtocolSelection?,
    modifier: Modifier = Modifier,
    trailingTitleContent: (@Composable () -> Unit)? = null
) {
    TvSettingsItemRadioSmall(
        title = stringResource(title),
        description = stringResource(description),
        checked = itemProtocol == selectedProtocol,
        onClick = { onSelected(itemProtocol) },
        trailingTitleContent = trailingTitleContent,
        modifier = modifier,
    )
}

@Composable
private fun TvSettingSectionHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = ProtonTheme.typography.body2Medium,
        color = ProtonTheme.colors.textAccent,
        modifier = modifier
            .semantics { heading() }
            .padding(horizontal = TvUiConstants.SelectionPaddingHorizontal)
            .padding(top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun LabelBadge(
    text: String,
    textColor: Color,
    borderColor: Color,
) {
    Text(
        text = text.uppercase(),
        style = ProtonTheme.typography.overlineMedium,
        color = textColor,
        modifier = Modifier
            .border(width = 1.dp, color = borderColor, shape = ProtonTheme.shapes.small)
            .background(ProtonTheme.colors.backgroundSecondary, shape = ProtonTheme.shapes.small)
            .padding(horizontal = 6.dp, vertical = 2.dp)
        )
}
