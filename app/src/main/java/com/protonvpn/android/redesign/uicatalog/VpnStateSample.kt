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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.netshield.NetShieldStats
import com.protonvpn.android.netshield.NetShieldViewState
import com.protonvpn.android.redesign.vpn.ui.LocationText
import com.protonvpn.android.redesign.vpn.ui.VpnStatusBottom
import com.protonvpn.android.redesign.vpn.ui.VpnStatusTop
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewState
import com.protonvpn.android.redesign.vpn.ui.rememberVpnStateAnimationProgress
import com.protonvpn.android.redesign.vpn.ui.vpnStatusOverlayBackground

class VpnStateSample : SampleScreen("Vpn state sample", "vpn_state") {

    @Composable
    override fun Content(modifier: Modifier, snackbarHostState: SnackbarHostState) {
        Column(modifier = modifier.padding(16.dp)) {
            var state by remember {
                val initialState =VpnStatusViewState.Disabled(LocationText("Lithuania", "192.1.1.1.1"))
                mutableStateOf<VpnStatusViewState>(initialState)
            }
            VpnSolidButton(
                "Disabled", onClick = {
                    state = VpnStatusViewState.Disabled(LocationText("Lithuania", "192.1.1.1.1"))
                }, modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            VpnSolidButton(
                "Connecting", onClick = {
                    state = VpnStatusViewState.Connecting(LocationText("Lithuania", "192.1.1.1.1"))
                }, modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            VpnSolidButton(
                "Connected", onClick = {
                    state = VpnStatusViewState.Connected(true, NetShieldViewState.NetShieldState(
                        protocol = NetShieldProtocol.ENABLED_EXTENDED,
                        netShieldStats = NetShieldStats(
                            adsBlocked = 3,
                            trackersBlocked = 0,
                            savedBytes = 2000
                        )
                    ))
                }, modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            val transitonProgress = rememberVpnStateAnimationProgress(state = state)
            Column(
                Modifier
                    .vpnStatusOverlayBackground(state)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                VpnStatusTop(state, { transitonProgress.value })
                VpnStatusBottom(state, { transitonProgress.value }, {}, {}, {})
            }
        }
    }
}
