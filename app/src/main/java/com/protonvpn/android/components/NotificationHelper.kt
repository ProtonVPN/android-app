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
package com.protonvpn.android.components

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.R
import com.protonvpn.android.bus.TrafficUpdate
import com.protonvpn.android.ui.home.HomeActivity
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStateMonitor.State.*

object NotificationHelper {

    val CHANNEL_ID = "com.protonvpn.android"

    fun initNotificationChannel(context: Context) {
        val channelOneName = "ProtonChannel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val notificationChannel = android.app.NotificationChannel(CHANNEL_ID, channelOneName,
                    android.app.NotificationManager.IMPORTANCE_LOW)
            notificationChannel.enableLights(true)
            notificationChannel.setShowBadge(true)
            notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            val manager =
                    context.getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(notificationChannel)
        }
    }

    fun buildNotificationWithTraffic(vpnState: VpnStateMonitor.VpnState, trafficUpdate: TrafficUpdate?): Notification {
        val builder =
                NotificationCompat.Builder(ProtonApplication.getAppContext(), CHANNEL_ID)
                        .setSmallIcon(getIconForState(vpnState.state))
                        .setContentTitle(getStringFromState(vpnState))
                        .setContentText(trafficUpdate?.notificationString)
                        .setStyle(NotificationCompat.BigTextStyle())
                        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        val intent = Intent(ProtonApplication.getAppContext(), HomeActivity::class.java)
        intent.putExtra("OpenStatus", true)
        val pending =
                PendingIntent.getActivity(ProtonApplication.getAppContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        builder.setContentIntent(pending)
        return builder.build()
    }

    fun buildNotification(vpnState: VpnStateMonitor.VpnState): Notification {
        return buildNotificationWithTraffic(vpnState, null)
    }

    private fun getIconForState(state: VpnStateMonitor.State): Int {
        return when (state) {
            DISABLED, ERROR -> R.drawable.ic_notification_disconnected
            CONNECTING, WAITING_FOR_NETWORK, DISCONNECTING, CHECKING_AVAILABILITY, RECONNECTING -> R.drawable.ic_notification_warning
            CONNECTED -> R.drawable.ic_notification
        }
    }

    private fun getStringFromState(vpnState: VpnStateMonitor.VpnState): String {
        val context = ProtonApplication.getAppContext()
        return when (vpnState.state) {
            CHECKING_AVAILABILITY -> context.getString(R.string.loaderCheckingAvailability)
            DISABLED -> context.getString(R.string.loaderNotConnected)
            CONNECTING -> context.getString(R.string.loaderConnectingTo, getServerName(vpnState))
            CONNECTED -> context.getString(R.string.loaderConnectedTo, getServerName(vpnState))
            DISCONNECTING -> context.getString(R.string.state_disconnecting)
            ERROR -> context.getString(R.string.state_error)
            RECONNECTING -> context.getString(R.string.loaderReconnecting)
            WAITING_FOR_NETWORK -> context.getString(R.string.loaderReconnectNoNetwork)
        }
    }

    private fun getServerName(vpnState: VpnStateMonitor.VpnState): String {
        val (profile, server) = vpnState.connectionInfo!!
        return if (profile.isPreBakedProfile || profile.name.isEmpty()) server.getDisplayName() else profile.name
    }
}
