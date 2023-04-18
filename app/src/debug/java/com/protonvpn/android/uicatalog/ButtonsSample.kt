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

package com.protonvpn.android.uicatalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.ProtonOutlinedButton
import com.protonvpn.android.base.ui.ProtonSecondaryButton
import com.protonvpn.android.base.ui.ProtonSolidButton
import com.protonvpn.android.base.ui.ProtonTextButton
import com.protonvpn.android.base.ui.VpnOutlinedButton
import com.protonvpn.android.base.ui.VpnOutlinedNeutralButton
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.VpnTextButton
import com.protonvpn.android.base.ui.VpnWeakSolidButton
import me.proton.core.compose.component.VerticalSpacer

class ButtonsSample : SampleScreen("Buttons", "buttons") {

    @Composable
    override fun Content(modifier: Modifier, snackbarHostState: SnackbarHostState) {
        val topPadding = 16.dp
        val dummyText = "Default button"
        Column(modifier = modifier.padding(16.dp)) {
            val buttonModifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding)
            SamplesSectionLabel("VPN buttons")
            VpnSolidButton("Connect", onClick = {}, modifier = buttonModifier)
            VpnWeakSolidButton("Cancel", onClick = {}, modifier = buttonModifier)
            VpnOutlinedButton("Learn more", onClick = {}, isExternalLink = true, modifier = buttonModifier)
            VpnOutlinedNeutralButton(text = "Change server", onClick = {}, modifier = buttonModifier)
            VpnTextButton("Troubleshooting", onClick = {}, modifier = buttonModifier)

            VerticalSpacer(height = 24.dp)
            SamplesSectionLabel("Generic buttons (all states)")
            ProtonSolidButton(
                onClick = {},
                modifier = buttonModifier
            ) {
                Text(dummyText)
            }

            ProtonSolidButton(
                onClick = {},
                enabled = false,
                modifier = buttonModifier
            ) {
                Text(dummyText)
            }

            ProtonSolidButton(
                contained = false,
                onClick = {},
                loading = true,
                modifier = buttonModifier
            ) {
                Text(dummyText)
            }

            ProtonTextButton(
                onClick = {},
                modifier = buttonModifier
            ) {
                Text(dummyText)
            }

            ProtonTextButton(
                contained = false,
                onClick = {},
                loading = true,
                modifier = buttonModifier
            ) {
                Text(dummyText)
            }

            ProtonOutlinedButton(
                onClick = {},
                modifier = buttonModifier
            ) {
                Text(dummyText)
            }

            ProtonOutlinedButton(
                onClick = {},
                enabled = false,
                modifier = buttonModifier
            ) {
                Text(dummyText)
            }

            ProtonSecondaryButton(
                onClick = {},
                modifier = buttonModifier
            ) {
                Text(dummyText)
            }

            ProtonSecondaryButton(
                onClick = {},
                loading = true,
                modifier = buttonModifier
            ) {
                Text(
                    dummyText,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
