/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.android.vpn

import com.protonvpn.android.models.config.UserData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressWarnings("UseDataClass")
class UpdateSecureCoreToMatchConnectedServer @Inject constructor(
    mainScope: CoroutineScope,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val userData: UserData
) {
    init {
        mainScope.launch {
            vpnStatusProviderUI.status
                .onEach { vpnState ->
                    val server = vpnState.connectionParams?.server
                    if (vpnState.state == VpnState.Connected &&
                        server != null &&
                        server.isSecureCoreServer != userData.secureCoreEnabled
                    ) {
                        userData.secureCoreEnabled = server.isSecureCoreServer
                    }
                }
                .launchIn(mainScope)
        }
    }
}
