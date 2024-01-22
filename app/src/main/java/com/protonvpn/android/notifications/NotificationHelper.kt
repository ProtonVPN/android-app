/*
 * Copyright (c) 2022. Proton AG
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
package com.protonvpn.android.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.os.Build
import android.os.Parcelable
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.R
import com.protonvpn.android.bus.TrafficUpdate
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.ui.home.vpn.SwitchDialogActivity.Companion.EXTRA_NOTIFICATION_DETAILS
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.utils.getThemeColor
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnState.CheckingAvailability
import com.protonvpn.android.vpn.VpnState.Connected
import com.protonvpn.android.vpn.VpnState.Connecting
import com.protonvpn.android.vpn.VpnState.Disabled
import com.protonvpn.android.vpn.VpnState.Disconnecting
import com.protonvpn.android.vpn.VpnState.Error
import com.protonvpn.android.vpn.VpnState.Reconnecting
import com.protonvpn.android.vpn.VpnState.ScanningPorts
import com.protonvpn.android.vpn.VpnState.WaitingForNetwork
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import javax.inject.Inject
import javax.inject.Singleton
import me.proton.core.presentation.R as CoreR

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val appContext: Context,
    scope: CoroutineScope,
    private val vpnStateMonitor: VpnStateMonitor,
    private val trafficMonitor: TrafficMonitor,
    private val isTv: IsTvCheck,
) {

    init {
        scope.launch {
            vpnStateMonitor.status.collect {
                updateNotification()
            }
        }
        scope.launch {
            trafficMonitor.trafficStatus.observeForever {
                updateNotification()
            }
        }
    }

    @Parcelize
    data class InformationNotification(
        val title: String,
        val content: String,
        val reconnectionInformation: ReconnectionInformation? = null,
        val action: ActionItem? = null,
        val fullScreenDialog: FullScreenDialog? = null,
    ) : Parcelable

    @Parcelize
    data class ReconnectionInformation(
        val fromServerName: String,
        val toServerName: String,
        val fromCountry: String,
        val fromCountrySecureCore: String? = null,
        val toCountry: String,
        val toCountrySecureCore: String? = null
    ) : Parcelable

    @Parcelize
    data class FullScreenDialog(
        val fullScreenIcon: Int? = null,
        val hasUpsellLayout: Boolean = false,
        val cancelToastMessage: String? = null
    ) : Parcelable

    sealed class ActionItem : Parcelable {
        abstract val title: String

        @Parcelize
        class Activity(
            override val title: String,
            val activityIntent: Intent,
            val closeAfterSuccess: Boolean,
            val upgradeSource: UpgradeSource?
        ) : ActionItem()

        @Parcelize
        class BgAction(override val title: String, val pendingIntent: PendingIntent) : ActionItem()
    }

    fun buildSwitchNotification(notificationInfo: InformationNotification) {
        val notificationBuilder =
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_vpn_status_information)
                .setColor(appContext.getThemeColor(CoreR.attr.colorAccent))

        // Build complex notification with custom UI for reconnection information
        if (notificationInfo.reconnectionInformation != null) {
            val reconnectionInfo = notificationInfo.reconnectionInformation
            val collapsedLayout = RemoteViews(appContext.packageName, R.layout.notification_reconnect_small)
            val expandedLayout = RemoteViews(appContext.packageName, R.layout.notification_reconnect_expanded)

            collapsedLayout.setTextViewText(R.id.content_title, notificationInfo.title)
            collapsedLayout.setTextViewText(
                R.id.content_text, notificationInfo.content
            )

            expandedLayout.setTextViewText(R.id.content_title, notificationInfo.title)
            expandedLayout.setTextViewText(R.id.content_text, notificationInfo.content)
            expandedLayout.setTextViewText(R.id.textTo, appContext.getString(R.string.reconnect_to_server))
            expandedLayout.setTextViewText(
                R.id.textFrom, appContext.getString(R.string.reconnect_from_server)
            )
            expandedLayout.setTextViewText(R.id.textFromServer, reconnectionInfo.fromServerName)
            expandedLayout.setTextViewText(R.id.textToServer, reconnectionInfo.toServerName)
            reconnectionInfo.toCountrySecureCore?.let {
                expandedLayout.setImageViewResource(
                    R.id.imageToCountrySc,
                    CountryTools.getFlagResource(appContext, it)
                )
                expandedLayout.setViewVisibility(R.id.imageToCountrySc, View.VISIBLE)
                expandedLayout.setViewVisibility(R.id.arrowToSc, View.VISIBLE)
            }
            reconnectionInfo.fromCountrySecureCore?.let {
                expandedLayout.setImageViewResource(
                    R.id.imageFromCountrySc,
                    CountryTools.getFlagResource(appContext, it)
                )
                expandedLayout.setViewVisibility(R.id.imageFromCountrySc, View.VISIBLE)
                expandedLayout.setViewVisibility(R.id.arrowFromSc, View.VISIBLE)
            }
            expandedLayout.setImageViewResource(
                R.id.imageFromCountry, CountryTools.getFlagResource(appContext, reconnectionInfo.fromCountry)
            )
            expandedLayout.setImageViewResource(
                R.id.imageToCountry, CountryTools.getFlagResource(appContext, reconnectionInfo.toCountry)
            )

            notificationBuilder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
            notificationBuilder.setCustomContentView(collapsedLayout)
            notificationBuilder.setCustomBigContentView(expandedLayout)
        } else {
            // Build classic simple notification since there is no reconnection information
            notificationBuilder.setContentTitle(notificationInfo.title)
            notificationBuilder.setContentText(notificationInfo.content)
            notificationBuilder.setStyle(NotificationCompat.BigTextStyle())
        }

        notificationInfo.action?.let {
            notificationBuilder.addAction(
                NotificationCompat.Action(R.drawable.ic_vpn_status_information, it.title, getPendingIntent(it))
            )
        }

        if (notificationInfo.fullScreenDialog != null) {
            val intent = createMainActivityIntent(appContext, isTv())
            intent.putExtra(EXTRA_NOTIFICATION_DETAILS, notificationInfo)
            val pending = PendingIntent.getActivity(appContext, PENDING_REQUEST_OTHER, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            notificationBuilder.setContentIntent(pending)
            notificationBuilder.setAutoCancel(true)
        }
        NotificationManagerCompat.from(appContext)
            .notifyWithPermission(Constants.NOTIFICATION_INFO_ID, notificationBuilder.build())
    }

    fun cancelInformationNotification(notificationId: Int = Constants.NOTIFICATION_INFO_ID) =
        NotificationManagerCompat.from(appContext).cancel(notificationId)

    private fun buildStatusNotification(
        vpnStatus: VpnStateMonitor.Status,
        trafficUpdate: TrafficUpdate?
    ): Notification {
        val context = ProtonApplication.getAppContext()
        val disconnectIntent =
            NotificationActionReceiver.createIntent(context, NotificationActionReceiver.DISCONNECT_ACTION)
        val disconnectPendingIntent = PendingIntent.getBroadcast(
            context, Constants.NOTIFICATION_ID, disconnectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val state = vpnStatus.state

        val notificationContentString =
            if (state is Error)
                HtmlTools.fromHtml(state.type.mapToErrorMessage(appContext, state.description))
            else
                trafficUpdate?.notificationString

        val builder =
                NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(getIconForState(state))
                        .setContentTitle(getStringFromState(vpnStatus))
                        .setContentText(notificationContentString)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setCategory(NotificationCompat.CATEGORY_SERVICE)
                        .setShowWhen(false)

        if (trafficUpdate != null) {
            builder.setWhen(trafficUpdate.sessionStartTimestampMs)
        }

        when (vpnStatus.state) {
            Connecting, Connected -> {
                val disconnectAction = NotificationCompat.Action(
                    CoreR.drawable.ic_proton_cross,
                    context.getString(R.string.disconnect),
                    disconnectPendingIntent
                )
                builder.addAction(disconnectAction)
            }
            else -> { /* Nothing */ }
        }

        val intent = createMainActivityIntent(context, isTv())
        intent.putExtra("OpenStatus", true)
        val pending =
            PendingIntent.getActivity(
                context,
                PENDING_REQUEST_STATUS,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        builder.setContentIntent(pending)
        return builder.build()
    }

    private fun updateStatusNotification(
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
                notifyWithPermission(Constants.NOTIFICATION_ID, buildStatusNotification(vpnStatus, trafficUpdate))
            }
            if (vpnStatus.state == Disabled) {
                cancel(Constants.NOTIFICATION_ID)
            }
        }
    }

    private fun getIconForState(state: VpnState): Int {
        return when (state) {
            Disabled, is Error -> R.drawable.ic_vpn_status_disconnected
            Connecting, WaitingForNetwork, Disconnecting, CheckingAvailability, ScanningPorts, Reconnecting ->
                R.drawable.ic_vpn_status_connecting
            Connected -> R.drawable.ic_vpn_status_connected
        }
    }

    private fun getStringFromState(vpnStatus: VpnStateMonitor.Status): String {
        val context = ProtonApplication.getAppContext()
        return when (vpnStatus.state) {
            CheckingAvailability, ScanningPorts -> context.getString(R.string.loaderCheckingAvailability)
            Disabled -> context.getString(R.string.loaderNotConnected)
            Connecting -> context.getString(R.string.loaderConnectingTo, getServerName(vpnStatus))
            Connected -> context.getString(R.string.loaderConnectedTo, getServerName(vpnStatus))
            Disconnecting -> context.getString(R.string.state_disconnecting)
            Reconnecting -> context.getString(R.string.loaderReconnecting)
            WaitingForNetwork -> context.getString(R.string.loaderReconnectNoNetwork)
            is Error -> context.getString(R.string.state_error)
        }
    }

    private fun getServerName(vpnStatus: VpnStateMonitor.Status): String {
        val server = vpnStatus.server!!
        // TODO: implement notification content
        return server.displayName
    }

    fun buildNotification() =
        buildStatusNotification(vpnStateMonitor.status.value, null)

    private fun updateNotification() {
        updateStatusNotification(
            appContext, vpnStateMonitor.status.value, trafficMonitor.trafficStatus.value)
    }

    fun showInformationNotification(
        @StringRes content: Int,
        @StringRes title: Int? = null,
        @DrawableRes icon: Int = R.drawable.ic_vpn_status_information,
        action: ActionItem? = null,
        notificationId: Int = Constants.NOTIFICATION_INFO_ID
    ) {
        with(NotificationManagerCompat.from(appContext)) {
            val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentText(appContext.getString(content))
                .setColor(appContext.getThemeColor(CoreR.attr.colorAccent))
                .setStyle(NotificationCompat.BigTextStyle())
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
            if (title != null)
                builder.setContentTitle(appContext.getString(title))

            builder.setContentIntent(
                PendingIntent.getActivity(
                    appContext, 0,
                    createMainActivityIntent(appContext, isTv()),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

            action?.let {
                builder.addAction(
                    NotificationCompat.Action(R.drawable.ic_vpn_status_information, it.title, getPendingIntent(it))
                )
            }
            notifyWithPermission(notificationId, builder.build())
        }
    }

    @SuppressLint("MissingPermission")
    private fun NotificationManagerCompat.notifyWithPermission(id: Int, notification: Notification) {
        if (appContext.isNotificationPermissionGranted(isTv)) {
            this.notify(id, notification)
        }
    }

    private fun getPendingIntent(action: ActionItem) = when (action) {
        is ActionItem.Activity -> PendingIntent.getActivity(
            appContext,
            Constants.NOTIFICATION_INFO_ID,
            action.activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        is ActionItem.BgAction -> action.pendingIntent
    }


    companion object {
        const val CHANNEL_ID = "com.protonvpn.android"
        const val PENDING_REQUEST_STATUS = 0
        const val PENDING_REQUEST_OTHER = 1

        fun initNotificationChannel(context: Context) {
            val channelOneName = "ProtonChannel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationChannel = NotificationChannel(
                    CHANNEL_ID, channelOneName,
                    NotificationManager.IMPORTANCE_LOW)
                notificationChannel.enableLights(true)
                notificationChannel.setShowBadge(true)
                notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                val manager =
                    context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(notificationChannel)
            }
        }

        // Use NEW_TASK flag to bring back the existing task to foreground.
        fun createMainActivityIntent(context: Context, isTv: Boolean): Intent {
            val mainActivityClass = if (isTv) Constants.TV_MAIN_ACTIVITY_CLASS else Constants.MOBILE_MAIN_ACTIVITY_CLASS
            return Intent(context, mainActivityClass).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        }
    }
}
