/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.ui.home.vpn

import androidx.lifecycle.ViewModel
import com.protonvpn.android.bus.ConnectToProfile
import com.protonvpn.android.bus.EventBus
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VpnStateNotConnectedViewModel @Inject constructor(
    serverListUpdater: ServerListUpdater,
    private val manager: ServerManager
) : ViewModel() {

    val ipAddress = serverListUpdater.ipAddress

    fun quickConnect() {
        EventBus.post(
            ConnectToProfile(
                manager.defaultConnection,
                ConnectTrigger.ConnectionPanel("quick connect in connection panel"),
                DisconnectTrigger.ConnectionPanel("quick connect in connection panel")
            )
        )
    }
}
