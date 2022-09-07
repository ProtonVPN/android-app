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

package com.protonvpn.android.vpn.ikev2

import android.app.Notification
import android.content.Intent
import android.net.VpnService
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.ConnectionParamsIKEv2
import com.protonvpn.android.notifications.NotificationHelper
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.Constants.MAIN_ACTIVITY_CLASS
import com.protonvpn.android.utils.Log
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.CurrentVpnServiceProvider
import com.protonvpn.android.vpn.VpnConnectionManager
import dagger.hilt.android.AndroidEntryPoint
import org.strongswan.android.logic.CharonVpnService
import javax.inject.Inject

@AndroidEntryPoint
class ProtonCharonVpnService : CharonVpnService() {

    @Inject lateinit var api: ProtonApiRetroFit
    @Inject lateinit var userData: UserData
    @Inject lateinit var appConfig: AppConfig
    @Inject lateinit var manager: ServerManager
    @Inject lateinit var vpnConnectionManager: VpnConnectionManager
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var currentVpnServiceProvider: CurrentVpnServiceProvider
    @Inject lateinit var currentUser: CurrentUser

    override fun onCreate() {
        super.onCreate()
        currentVpnServiceProvider.onVpnServiceCreated(StrongSwanBackend::class, this)
        Log.i("[IKEv2] onCreate")
    }

    override fun onDestroy() {
        Log.i("[IKEv2] onDestroy")
        vpnConnectionManager.onVpnServiceDestroyed()
        currentVpnServiceProvider.onVpnServiceDestroyed(StrongSwanBackend::class)
        super.onDestroy()
    }

    override fun getMainActivityClass(): Class<*> = MAIN_ACTIVITY_CLASS

    override fun buildNotification(unused: Boolean): Notification =
        notificationHelper.buildNotification()

    override fun getNotificationID() =
        Constants.NOTIFICATION_ID

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Decision whether to keep the service running might take a moment (e.g. due to Smart Protocol pings) so
        // let's keep it in foreground to protect it from being killed by the system.
        if (intent?.action != VpnService.SERVICE_INTERFACE) {
            startForeground(Constants.NOTIFICATION_ID, notificationHelper.buildNotification())
        }
        val user = currentUser.vpnUserCached()
        when {
            intent == null ->
                handleRestoreState()
            intent.action == VpnService.SERVICE_INTERFACE && currentUser.isLoggedInCached() ->
                handleAlwaysOn()
            intent.action == DISCONNECT_ACTION -> {
                Log.i("[IKEv2] disconnecting")
                setNextProfile(null)
            }
            user != null -> {
                val serverToConnect = Storage.load(ConnectionParams::class.java, ConnectionParamsIKEv2::class.java)
                setNextProfile(serverToConnect?.getStrongSwanProfile(this, userData, user, appConfig))
                Log.i("[IKEv2] start next profile: " + serverToConnect?.server?.displayName)
                return if (serverToConnect != null) {
                    START_STICKY
                } else
                    START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        ProtonLogger.logCustom(LogCategory.APP, "ProtonCharonVpnService: onTrimMemory level $level")
    }

    private fun handleRestoreState() {
        Log.i("[IKEv2] handle restore state")
        val lastServer = Storage.load(ConnectionParams::class.java, ConnectionParamsIKEv2::class.java)
        if (lastServer == null)
            stopSelf()
        else {
            if (!vpnConnectionManager.onRestoreProcess(lastServer.profile, "service restart"))
                stopSelf()
        }
    }

    private fun handleAlwaysOn() {
        Log.i("[IKEv2] handle always on")
        vpnConnectionManager.connectInBackground(manager.defaultAvailableConnection, "always-on")
    }
}
