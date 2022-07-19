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
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.ui.vpn.VpnBackgroundUiDelegate
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnConnectionErrorHandler
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import me.proton.core.network.domain.NetworkManager

class MockVpnConnectionManager(
    userData: UserData,
    vpnBackendProvider: VpnBackendProvider,
    networkManager: NetworkManager,
    vpnErrorHandler: VpnConnectionErrorHandler,
    vpnStateMonitor: VpnStateMonitor,
    vpnBackgroundUiDelegate: VpnBackgroundUiDelegate,
    serverManager: ServerManager,
    certificateRepository: CertificateRepository,
    scope: CoroutineScope,
    now: () -> Long,
    currentUser: CurrentUser
) : VpnConnectionManager(
    ProtonApplication.getAppContext(), userData, vpnBackendProvider, networkManager, vpnErrorHandler, vpnStateMonitor,
    vpnBackgroundUiDelegate, serverManager, certificateRepository, scope, now, currentUser
) {
    override fun prepare(context: Context): Intent? = null
}
