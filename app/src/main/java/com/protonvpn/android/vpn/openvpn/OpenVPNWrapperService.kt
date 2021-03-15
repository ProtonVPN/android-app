/*
 * Copyright (c) 2021 Proton Technologies AG
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
package com.protonvpn.android.vpn.openvpn

import android.content.Intent
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.ConnectionParamsOpenVpn
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.VpnConnectionManager
import dagger.android.AndroidInjection
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.VpnStatus.StateListener
import javax.inject.Inject

class OpenVPNWrapperService : OpenVPNService(), StateListener {

    @Inject lateinit var userData: UserData
    @Inject lateinit var appConfig: AppConfig
    @Inject lateinit var vpnConnectionManager: VpnConnectionManager
    @Inject lateinit var serverManager: ServerManager
    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
        NotificationHelper.initNotificationChannel(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(Constants.NOTIFICATION_ID, notificationHelper.buildNotification())
        return super.onStartCommand(intent, flags, startId)
    }

    override fun getProfile(): VpnProfile? =
        Storage.load(ConnectionParams::class.java, ConnectionParamsOpenVpn::class.java)
                ?.openVpnProfile(this, userData, appConfig)

    override fun onProcessRestore(): Boolean {
        val lastServer = Storage.load(ConnectionParams::class.java, ConnectionParamsOpenVpn::class.java)
                ?: return false
        lastServer.profile.wrapper.setDeliverer(serverManager)
        return vpnConnectionManager.onRestoreProcess(this, lastServer.profile)
    }
}
