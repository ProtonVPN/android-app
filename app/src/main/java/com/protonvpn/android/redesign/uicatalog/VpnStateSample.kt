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
package com.protonvpn.android.redesign.uicatalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.netshield.NetShieldStats
import com.protonvpn.android.redesign.vpn.ui.LocationText
import com.protonvpn.android.redesign.vpn.ui.VpnStatusView
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewState
import kotlinx.coroutines.flow.MutableStateFlow

class VpnStateSample : SampleScreen("Vpn state sample", "vpn_state") {

    @Composable
    override fun Content(modifier: Modifier, snackbarHostState: SnackbarHostState) {
        Column(modifier = modifier.padding(16.dp)) {
            val statusFlow = MutableStateFlow<VpnStatusViewState>(VpnStatusViewState.Disabled(LocationText("Lithuania", "192.1.1.1.1")))
            VpnSolidButton(
                "Disabled", onClick = {
                    statusFlow.value = VpnStatusViewState.Disabled(LocationText("Lithuania", "192.1.1.1.1"))
                }, modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            VpnSolidButton(
                "Connecting", onClick = {
                    statusFlow.value = VpnStatusViewState.Connecting(LocationText("Lithuania", "192.1.1.1.1"))
                }, modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            VpnSolidButton(
                "Connected", onClick = {
                    statusFlow.value = VpnStatusViewState.Connected(true, false, NetShieldStats(1, 0, 5234))
                }, modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            VpnStatusView(
                stateFlow = statusFlow, modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}
