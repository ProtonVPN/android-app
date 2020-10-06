/*
 * Copyright (c) 2020 Proton Technologies AG
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

package com.protonvpn.android.ui.home

import com.protonvpn.android.api.NetworkUserData
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.VpnApiClient
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.LiveEvent
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AuthManager(
    val scope: CoroutineScope,
    val userData: UserData,
    val serverManager: ServerManager,
    val api: ProtonApiRetroFit,
    val vpnStateMonitor: VpnStateMonitor,
    vpnApiClient: VpnApiClient,
    networkUserData: NetworkUserData
) {
    val logoutEvent = LiveEvent()

    init {
        vpnApiClient.forceUpdateEvent.observeForever {
            logout(true)
        }
        networkUserData.forceLogoutEvent.observeForever {
            logout(true)
        }
    }

    fun logout(forced: Boolean) = scope.launch {
        if (!forced)
            api.logout()

        userData.logout()
        serverManager.clearCache()
        vpnStateMonitor.disconnect()
        logoutEvent.emit()
    }
}
