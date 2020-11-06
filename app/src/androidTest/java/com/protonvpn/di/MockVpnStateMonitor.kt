/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.di

import android.content.Context
import android.content.Intent
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.vpn.MaintenanceTracker
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import me.proton.core.network.domain.NetworkManager

class MockVpnStateMonitor(
    userData: UserData,
    api: ProtonApiRetroFit,
    vpnBackendProvider: VpnBackendProvider,
    serverListUpdater: ServerListUpdater,
    trafficMonitor: TrafficMonitor,
    networkManager: NetworkManager,
    maintenanceTracker: MaintenanceTracker,
    scope: CoroutineScope
) : VpnStateMonitor(ProtonApplication.getAppContext(), userData, api, vpnBackendProvider,
        serverListUpdater, trafficMonitor, networkManager, maintenanceTracker, scope) {

    override fun prepare(context: Context): Intent? = null
}
