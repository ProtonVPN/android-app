package com.protonvpn.android.vpn.wireguard
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

import android.content.Intent
import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.VpnConnectionManager
import com.wireguard.android.backend.GoBackend
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WireguardWrapperService : GoBackend.VpnService() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var wireguardBackend: WireguardBackend
    @Inject lateinit var connectionManager: VpnConnectionManager

    override fun onCreate() {
        super.onCreate()
        wireguardBackend.serviceCreated(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            if (handleProcessRestore())
                return START_STICKY
        } else {
            startForeground(Constants.NOTIFICATION_ID, notificationHelper.buildNotification())
            return START_STICKY
        }
        return START_NOT_STICKY
    }

    private fun handleProcessRestore(): Boolean {
        Storage.load(ConnectionParams::class.java)?.profile?.let { profile ->
            return connectionManager.onRestoreProcess(profile)
        }
        return false
    }

    override fun onDestroy() {
        wireguardBackend.serviceDestroyed()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        ProtonLogger.logCustom(LogCategory.APP, "WirguardWrapperService: onTrimMemory level $level")
    }

    fun close() {
        stopForeground(false)
        stopSelf()
    }
}
