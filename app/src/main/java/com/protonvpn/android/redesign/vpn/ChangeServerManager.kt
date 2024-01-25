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

package com.protonvpn.android.redesign.vpn

import com.protonvpn.android.appconfig.RestrictionsConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.ui.home.vpn.ChangeServerPrefs
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChangeServerManager @Inject constructor(
    private val mainScope: CoroutineScope,
    vpnStatusProviderUI: VpnStatusProviderUI,
    private val restrictConfig: RestrictionsConfig,
    private val vpnConnectionManager: VpnConnectionManager,
    private val serverManager: ServerManager2,
    private val changeServerPrefs: ChangeServerPrefs,
    private val currentUser: CurrentUser,
    @WallClock private val wallClock: () -> Long
) {

    private val changeInProgress = MutableStateFlow(false)
    val isChangingServer: StateFlow<Boolean> get() = changeInProgress

    init {
        vpnStatusProviderUI.uiStatus
            .onEach {
                if (it.state !is VpnState.Connected && !it.state.isEstablishingConnection)
                    changeInProgress.value = false
            }
            .launchIn(mainScope)
    }

    fun changeServer(vpnUiDelegate: VpnUiDelegate) {
        mainScope.launch {
            changeInProgress.value = true
            val server = requireNotNull(serverManager.getRandomServer(currentUser.vpnUser()))
            vpnConnectionManager.connect(
                vpnUiDelegate,
                ConnectIntent.Server(server.serverId, emptySet()),
                ConnectTrigger.ChangeServer
            )

            val currentCount = changeServerPrefs.changeCounter + 1
            changeServerPrefs.changeCounter =
                if (currentCount > restrictConfig.changeServerConfig().maxAttemptCount) 0 else currentCount
            changeServerPrefs.lastChangeTimestamp = wallClock()
        }
    }


}
