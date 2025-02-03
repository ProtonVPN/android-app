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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.ProtonSecondaryButton
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.redesign.base.ui.ProtonOutlinedTextField
import com.protonvpn.android.redesign.base.ui.SettingsItem
import me.proton.core.presentation.R as CoreR

@Composable
fun DebugTools(
    onConnectGuestHole: () -> Unit,
    onRefreshConfig: () -> Unit,
    netzone: String,
    country: String,
    setNetzone: (String) -> Unit,
    setCountry: (String) -> Unit,
    onClose: () -> Unit,
) {
    SubSetting(
        title = "Debug tools",
        onClose = onClose
    ) {
        SettingsItem(
            name = "Enable Guest Hole",
            description = "Simulates a 10s API call that triggers Guest Hole.",
        ) {
            ProtonSecondaryButton(onClick = onConnectGuestHole) {
                Text("Connect")
            }
        }

        val paddingModifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp).fillMaxWidth()
        DebugTextInputRow(
            value = netzone,
            onValueChange = setNetzone,
            labelText = "x-pm-netzone",
            placeholderText = "IP address",
            modifier = paddingModifier,
        )
        DebugTextInputRow(
            value = country,
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
    }
}

@Composable
private fun DebugTextInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    labelText: String,
    placeholderText: String,
    modifier: Modifier = Modifier,
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