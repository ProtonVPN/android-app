/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.vpn.protun

import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.redesign.recents.usecases.GetQuickConnectIntent
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.Lazy
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.proton.vpn.sdk.api.SystemEventHandler
import javax.inject.Inject

@Reusable
class VpnSdkSystemEventHandler @Inject constructor(
    private val mainScope: CoroutineScope,
    private val vpnStateMonitor: Lazy<VpnStateMonitor>,
    private val connectionManager: Lazy<VpnConnectionManager>,
    private var quickConnectIntent: Lazy<GetQuickConnectIntent>
) : SystemEventHandler {

    override fun onProcessRestored() {
        ConnectionParams.readIntentFromStore()?.let { connectIntent ->
            connectionManager.get().onRestoreProcess(connectIntent, "service restart")
        }
    }

    override fun onAlwaysOnEnabled() {
        if (vpnStateMonitor.get().isDisabled) {
            mainScope.launch {
                connectionManager.get().onAlwaysOn(quickConnectIntent.get()())
            }
        }
    }
}