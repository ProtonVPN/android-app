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
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.R
import com.protonvpn.android.bus.TrafficUpdate
import com.protonvpn.android.ui.home.HomeActivity
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnState.CheckingAvailability
import com.protonvpn.android.vpn.VpnState.Connected
import com.protonvpn.android.vpn.VpnState.Connecting
import com.protonvpn.android.vpn.VpnState.Disabled
import com.protonvpn.android.vpn.VpnState.Disconnecting
import com.protonvpn.android.vpn.VpnState.Reconnecting
import com.protonvpn.android.vpn.VpnState.ScanningPorts
import com.protonvpn.android.vpn.VpnState.WaitingForNetwork
import com.protonvpn.android.vpn.VpnState.Error

object NotificationHelper {

    const val CHANNEL_ID = "com.protonvpn.android"
    const val DISCONNECT_ACTION = "DISCONNECT_ACTION"

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

    fun showInformationNotification(
        context: Context,
        content: String,
        title: String? = null,
        @DrawableRes icon: Int = R.drawable.ic_info
    ) {
        with(NotificationManagerCompat.from(context)) {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(icon)
                    .setContentText(content)
                    .setStyle(NotificationCompat.BigTextStyle())
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
            if (title != null)
                builder.setContentTitle(title)

            builder.setContentIntent(
                    PendingIntent.getActivity(
                            context, 0,
                            Intent(context, HomeActivity::class.java),
                            PendingIntent.FLAG_UPDATE_CURRENT))

            notify(Constants.NOTIFICATION_INFO_ID, builder.build())
        }
    }

    fun buildStatusNotification(vpnStatus: VpnStateMonitor.Status, trafficUpdate: TrafficUpdate?): Notification {
        val context = ProtonApplication.getAppContext()
        val disconnectIntent = Intent(DISCONNECT_ACTION)
        val disconnectPendingIntent = PendingIntent.getBroadcast(
            context, Constants.NOTIFICATION_ID, disconnectIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        val builder =
                NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(getIconForState(vpnStatus.state))
                        .setContentTitle(getStringFromState(vpnStatus))
                        .setContentText(trafficUpdate?.notificationString)
                        .setStyle(NotificationCompat.BigTextStyle())
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setCategory(NotificationCompat.CATEGORY_SERVICE)

        when (vpnStatus.state) {
            Disabled, CheckingAvailability, ScanningPorts, WaitingForNetwork, Reconnecting, Disconnecting ->
                builder.color = ContextCompat.getColor(context, R.color.orange)
            Connecting, Connected -> {
                builder.color = ContextCompat.getColor(context, R.color.greenBright)
                builder.addAction(NotificationCompat.Action(R.drawable.ic_close_white_24dp,
                        context.getString(R.string.disconnect), disconnectPendingIntent))
            }
            else -> builder.color = ContextCompat.getColor(context, R.color.red)
        }

        val intent = Intent(context, HomeActivity::class.java)
        intent.putExtra("OpenStatus", true)
        val pending =
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        builder.setContentIntent(pending)
        return builder.build()
    }

    fun updateStatusNotification(
        context: Context,
        vpnStatus: VpnStateMonitor.Status,
        trafficUpdate: TrafficUpdate?
    ) {
        with(NotificationManagerCompat.from(context)) {
            // On android < 10 first update the notification even when disabled. If foreground
            // service is still running, notification will stay after cancel() - let's at least show
            // correct "not connected" notification. However on Android 10+ this somehow can cause
            // notification cancel to have no effect.
            if (Build.VERSION.SDK_INT < 29 || vpnStatus.state != Disabled) {
                notify(Constants.NOTIFICATION_ID, buildStatusNotification(vpnStatus, trafficUpdate))
            }
            if (vpnStatus.state == Disabled) {
                cancel(Constants.NOTIFICATION_ID)
            }
        }
    }

    private fun getIconForState(state: VpnState): Int {
        return when (state) {
            Disabled, is Error -> R.drawable.ic_notification_disconnected
            Connecting, WaitingForNetwork, Disconnecting, CheckingAvailability, ScanningPorts, Reconnecting ->
                R.drawable.ic_notification_warning
            Connected -> R.drawable.ic_notification
        }
    }

    private fun getStringFromState(vpnStatus: VpnStateMonitor.Status): String {
        val context = ProtonApplication.getAppContext()
        return when (vpnStatus.state) {
            CheckingAvailability, ScanningPorts -> context.getString(R.string.loaderCheckingAvailability)
            Disabled -> context.getString(R.string.loaderNotConnected)
            Connecting -> context.getString(R.string.loaderConnectingTo, getServerName(context, vpnStatus))
            Connected -> context.getString(R.string.loaderConnectedTo, getServerName(context, vpnStatus))
            Disconnecting -> context.getString(R.string.state_disconnecting)
            Reconnecting -> context.getString(R.string.loaderReconnecting)
            WaitingForNetwork -> context.getString(R.string.loaderReconnectNoNetwork)
            is Error -> context.getString(R.string.state_error)
        }
    }

    private fun getServerName(context: Context, vpnStatus: VpnStateMonitor.Status): String {
        val profile = vpnStatus.profile!!
        val server = vpnStatus.server!!
        return if (profile.isPreBakedProfile || profile.getDisplayName(context).isEmpty())
            server.displayName else profile.getDisplayName(context)
    }
}
