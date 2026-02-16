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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.protonButtonColors
import com.protonvpn.android.redesign.base.ui.ProtonOutlinedTextField
import com.protonvpn.android.redesign.base.ui.SettingsItem
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
@Suppress("LongParameterList")
fun DebugTools(
    state: DebugToolsState,
    onConnectGuestHole: () -> Unit,
    onRefreshConfig: () -> Unit,
    setNetzone: (String) -> Unit,
    setCountry: (String) -> Unit,
    onClose: () -> Unit,
    setPcapActive: (Boolean) -> Unit,
    onSharePcapFile: () -> Unit,
    onRemovePcapFile: () -> Unit,
    onSetMaxPcapSizeMB: (Long) -> Unit,
) {
    SubSetting(
        title = "Debug tools",
        onClose = onClose
    ) {
        SettingsItem(
            modifier = Modifier.clickable(onClick = onConnectGuestHole),
            name = "Connect Guest Hole",
            description = "Simulates a 10s API call that triggers Guest Hole.",
        )

        val paddingModifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp).fillMaxWidth()
        DebugTextInputRow(
            value = state.netzone,
            onValueChange = setNetzone,
            labelText = "x-pm-netzone",
            placeholderText = "IP address",
            modifier = paddingModifier,
        )
        DebugTextInputRow(
            value = state.country,
            onValueChange = setCountry,
            labelText = "x-pm-country",
            placeholderText = "2-letter country code",
            modifier = paddingModifier,
        )
        VpnSolidButton(
            onClick = onRefreshConfig,
            text = "Refresh config and servers",
            modifier = paddingModifier,
        )

        SettingsItem(
            modifier = Modifier.clickable(onClick = onConnectGuestHole),
            name = "Packet capture",
        )

        VpnSolidButton(
            onClick = { setPcapActive(!state.isPacketCaptureActive) },
            text = if (state.isPacketCaptureActive) "Stop PCAP" else "Start PCAP",
            colors =
                if (state.isPacketCaptureActive) ButtonDefaults.protonButtonColors(
                    contentColor = ProtonTheme.colors.textNorm,
                    backgroundColor = ProtonTheme.colors.notificationError
                ) else {
                    ButtonDefaults.protonButtonColors()
                },
            modifier = paddingModifier,
        )

        if (!state.isPacketCaptureActive) {
            DebugTextInputRow(
                value = if (state.pcapMaxMBytes == 0L) "" else state.pcapMaxMBytes.toString(),
                onValueChange = { newValue ->
                    // Filter to only allow digits (positive numbers)
                    val filtered = newValue.filter { it.isDigit() }
                    val sizeInMB = filtered.toLongOrNull() ?: 0L
                    onSetMaxPcapSizeMB(sizeInMB)
                },
                labelText = "Max PCAP size (MB)",
                placeholderText = state.pcapMaxMBytes.takeIf { it > 0 }?.toString() ?: "Unlimited",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = paddingModifier,
            )
        }

        state.existingPcapFileName?.let { fileName ->
            Row(
                modifier = paddingModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fileName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onSharePcapFile) {
                    Icon(
                        painter = painterResource(id = CoreR.drawable.ic_proton_arrow_up_from_square),
                        contentDescription = "Share",
                    )
                }
                IconButton(onClick = onRemovePcapFile) {
                    Icon(
                        painter = painterResource(id = CoreR.drawable.ic_proton_trash),
                        contentDescription = "Remove",
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugTextInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    labelText: String,
    placeholderText: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
    ) {
        ProtonOutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            labelText = labelText,
            placeholderText = placeholderText,
            singleLine = true,
            modifier = Modifier.weight(1f),
            keyboardOptions = keyboardOptions,
            trailingIcon = {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_proton_close),
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onValueChange("") }
                )
            },
        )
    }
}

@ProtonVpnPreview
@Composable
fun DebugToolsPreview() {
    ProtonVpnPreview {
        DebugTools(
            state = DebugToolsState(
                netzone = "netzone",
                country = "country",
                isPacketCaptureActive = true,
                pcapMaxMBytes = 100,
                existingPcapFileName = "protonvpn.pcap",
            ),
            onConnectGuestHole = {},
            onRefreshConfig = {},
            setNetzone = {},
            setCountry = {},
            onClose = {},
            setPcapActive = {},
            onSharePcapFile = {},
            onRemovePcapFile = {},
            onSetMaxPcapSizeMB = {},
        )
    }
}