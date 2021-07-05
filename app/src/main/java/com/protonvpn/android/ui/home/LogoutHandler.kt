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

import com.protonvpn.android.api.ApiSessionProvider
import com.protonvpn.android.api.VpnApiClient
import com.protonvpn.android.api.VpnApiManager
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.api.HumanVerificationHandler
import com.protonvpn.android.utils.LiveEvent
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class LogoutHandler(
    val scope: CoroutineScope,
    val userData: UserData,
    val serverManager: ServerManager,
    val vpnApiManager: VpnApiManager,
    val apiSessionProvider: ApiSessionProvider,
    val vpnStateMonitor: VpnStateMonitor,
    val vpnConnectionManager: VpnConnectionManager,
    val humanVerificationHandler: HumanVerificationHandler,
    vpnApiClient: VpnApiClient
) {
    val logoutEvent = LiveEvent()

    init {
        scope.launch {
            vpnApiClient.forceUpdateEvent.collect {
                logout(true)
            }
        }
        scope.launch {
            apiSessionProvider.forceLogoutEvent.collect {
                if (apiSessionProvider.currentSessionId == it.sessionId)
                    logout(true)
            }
        }
    }

    fun logout(forced: Boolean) = scope.launch {
        vpnConnectionManager.disconnectSync()

        // Logout old session. This can take a while, so do it in background.
        val currentSessionId = apiSessionProvider.currentSessionId
        if (!forced) launch {
            vpnApiManager(sessionId = currentSessionId) {
                postLogout()
            }
        }

        userData.logout()
        serverManager.clearCache()
        humanVerificationHandler.clear()

        logoutEvent.emit()
    }
}
