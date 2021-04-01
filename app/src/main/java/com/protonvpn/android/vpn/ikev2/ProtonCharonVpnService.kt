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
import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.ConnectionParamsIKEv2
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.Constants.MAIN_ACTIVITY_CLASS
import com.protonvpn.android.utils.Log
import com.protonvpn.android.utils.ProtonLogger
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.VpnConnectionManager
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.strongswan.android.logic.CharonVpnService
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import javax.inject.Inject

class ProtonCharonVpnService : CharonVpnService() {

    private val lifecycleJob = Job()
    private val lifecycleScope = CoroutineScope(lifecycleJob)

    @Inject lateinit var api: ProtonApiRetroFit
    @Inject lateinit var userData: UserData
    @Inject lateinit var appConfig: AppConfig
    @Inject lateinit var manager: ServerManager
    @Inject lateinit var vpnConnectionManager: VpnConnectionManager
    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()

        Log.i("[IKEv2] onCreate")
        AndroidInjection.inject(this)
        startCaptureLogFile()
    }

    override fun onDestroy() {
        Log.i("[IKEv2] onDestroy")

        lifecycleJob.cancel()
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
        startForeground(Constants.NOTIFICATION_ID, notificationHelper.buildNotification())
        when {
            intent == null ->
                handleRestoreState()
            intent.action == VpnService.SERVICE_INTERFACE && userData.isLoggedIn ->
                handleAlwaysOn()
            intent.action == DISCONNECT_ACTION -> {
                Log.i("[IKEv2] disconnecting")
                setNextProfile(null)
            }
            else -> {
                val serverToConnect = Storage.load(ConnectionParams::class.java, ConnectionParamsIKEv2::class.java)
                setNextProfile(serverToConnect?.getStrongSwanProfile(this, userData, appConfig))
                Log.i("[IKEv2] start next profile: " + serverToConnect?.server?.displayName)
                return if (serverToConnect != null) {
                    START_STICKY
                } else
                    START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun handleRestoreState() {
        Log.i("[IKEv2] handle restore state")
        val lastServer = Storage.load(ConnectionParams::class.java, ConnectionParamsIKEv2::class.java)
        if (lastServer == null)
            stopSelf()
        else {
            lastServer.profile.wrapper.setDeliverer(manager)
            if (!vpnConnectionManager.onRestoreProcess(this, lastServer.profile))
                stopSelf()
        }
    }

    private fun handleAlwaysOn() {
        Log.i("[IKEv2] handle always on")
        vpnConnectionManager.connect(this, manager.defaultConnection, "always-on")
    }

    private fun startCaptureLogFile() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("logcat -s charon -T 1 -v raw")
                BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                    lines.forEach { ProtonLogger.log(it) }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
