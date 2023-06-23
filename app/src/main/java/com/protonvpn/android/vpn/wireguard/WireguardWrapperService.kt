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
import android.net.VpnService
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.notifications.NotificationHelper
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.CurrentVpnServiceProvider
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import com.wireguard.android.backend.GoBackend
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WireguardWrapperService : GoBackend.VpnService() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var wireguardBackend: WireguardBackend
    @Inject lateinit var connectionManager: VpnConnectionManager
    @Inject lateinit var currentVpnServiceProvider: CurrentVpnServiceProvider
    @Inject lateinit var currentUser: CurrentUser
    @Inject lateinit var vpnStateMonitor: VpnStateMonitor

    override fun onCreate() {
        super.onCreate()
        wireguardBackend.serviceCreated(this)
        currentVpnServiceProvider.onVpnServiceCreated(WireguardBackend::class, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ProtonLogger.logCustom(LogCategory.CONN_CONNECT, "Wireguard service started with intent: $intent")

        // Decision whether to keep the service running might take a moment (e.g. due to Smart Protocol pings) so
        // let's keep it in foreground to protect it from being killed by the system.
        if (intent?.action != VpnService.SERVICE_INTERFACE) {
            startForeground(Constants.NOTIFICATION_ID, notificationHelper.buildNotification())
        }

        when {
            intent == null -> {
                if (currentUser.isLoggedInCached() && handleProcessRestore())
                    return START_STICKY

            }
            intent.action == VpnService.SERVICE_INTERFACE -> {
                if (currentUser.isLoggedInCached() && handleAlwaysOn())
                    return START_STICKY
            }
            else ->
                return START_STICKY
        }
        return START_NOT_STICKY
    }

    private fun handleProcessRestore(): Boolean {
        ConnectionParams.readIntentFromStore()?.let { connectIntent ->
            return connectionManager.onRestoreProcess(connectIntent, "service restart")
        }
        return false
    }

    private fun handleAlwaysOn(): Boolean {
        // It's possible to get the always-on intent twice which causes a reconnection. Let's prevent this.
        return if (vpnStateMonitor.isDisabled) {
            connectionManager.connectInBackground(
                ConnectIntent.QuickConnect,
                ConnectTrigger.Auto("always-on")
            )
            true
        } else {
            false
        }
    }

    override fun onDestroy() {
        wireguardBackend.serviceDestroyed()
        connectionManager.onVpnServiceDestroyed()
        currentVpnServiceProvider.onVpnServiceDestroyed(WireguardBackend::class)
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
