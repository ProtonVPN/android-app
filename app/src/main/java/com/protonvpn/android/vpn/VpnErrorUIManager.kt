package com.protonvpn.android.vpn

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.components.NotificationHelper.ActionItem
import com.protonvpn.android.components.NotificationHelper.Companion.EXTRA_SWITCH_PROFILE
import com.protonvpn.android.components.NotificationHelper.Companion.SMART_PROTOCOL_ACTION
import com.protonvpn.android.components.NotificationHelper.FullScreenDialog
import com.protonvpn.android.components.NotificationHelper.ReconnectionNotification
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.ui.home.vpn.SwitchDialogActivity
import com.protonvpn.android.utils.AndroidUtils.launchActivity
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.UserPlanManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Singleton
class VpnErrorUIManager(
    scope: CoroutineScope,
    private val appContext: Context,
    private val appConfig: AppConfig,
    private val userData: UserData,
    private val userPlanManager: UserPlanManager,
    private val stateMonitor: VpnStateMonitor,
    private val notificationHelper: NotificationHelper
) {

    init {
        scope.launch {
            userPlanManager.planChangeFlow.collect {
                if (it is UserPlanManager.InfoChange.PlanChange.Downgrade && !stateMonitor.isEstablishingOrConnected) {
                    displayInformation(
                        ReconnectionNotification(
                            title = appContext.getString(R.string.notification_subscription_expired_title),
                            content = appContext.getString(R.string.notification_subscription_expired_no_reconnection_content),
                            reconnectionInformation = null,
                            action = ActionItem(
                                title = appContext.getString(R.string.upgrade),
                                pendingIntent = getPendingIntentForDashboard()
                            ),
                            fullScreenDialog = FullScreenDialog(null, true, null)
                        )
                    )
                }
            }
        }
        scope.launch {
            stateMonitor.fallbackConnectionFlow.collect { switch ->
                if (switch is VpnFallbackResult.Switch.SwitchServer && !switch.compatibleProtocol) {
                    notificationHelper.showInformationNotification(
                        context = appContext,
                        content = appContext.getString(R.string.notification_smart_protocol_disabled_content),
                        title = appContext.getString(R.string.notification_smart_protocol_disabled_title),
                        icon = R.drawable.ic_proton_green,
                        action = ActionItem(
                            title = appContext.getString(R.string.enable),
                            pendingIntent = PendingIntent.getBroadcast(
                                appContext,
                                Constants.NOTIFICATION_INFO_ID,
                                Intent(SMART_PROTOCOL_ACTION).apply {
                                    putExtra(EXTRA_SWITCH_PROFILE, switch.toProfile) },
                                PendingIntent.FLAG_UPDATE_CURRENT
                            )))
                } else if (userData.showSmartReconnectNotifications()) {
                    buildNotificationInformation(switch)?.let {
                        displayInformation(it)
                    }
                }
            }
        }
    }

    private fun displayInformation(reconnectionNotification: ReconnectionNotification) {
        if ((appContext as ProtonApplication).isForeground && reconnectionNotification.fullScreenDialog != null) {
            appContext.launchActivity<SwitchDialogActivity>(init = {
                putExtra(SwitchDialogActivity.EXTRA_NOTIFICATION_DETAILS, reconnectionNotification)
                flags = FLAG_ACTIVITY_NEW_TASK
            })
        } else {
            notificationHelper.buildSwitchNotification(reconnectionNotification)
        }
    }

    private fun buildNotificationInformation(switch: VpnFallbackResult): ReconnectionNotification? {
        return when (switch) {
            is VpnFallbackResult.Switch -> {
                switch.notificationReason?.let {
                    ReconnectionNotification(
                        title = notificationHelper.getContentTitle(it),
                        content = notificationHelper.getContentString(it),
                        reconnectionInformation = buildReconnectionInfo(switch),
                        action = if (it is SwitchServerReason.Downgrade || it is SwitchServerReason.UserBecameDelinquent) ActionItem(
                            title = appContext.getString(R.string.upgrade),
                            pendingIntent = getPendingIntentForDashboard()
                        ) else null,
                        fullScreenDialog = if (it is SwitchServerReason.Downgrade || it is SwitchServerReason.UserBecameDelinquent) FullScreenDialog(
                            hasUpsellLayout = true, cancelToastMessage = getCancelToastMessage(it)
                        ) else null
                    )
                }
            }
            is VpnFallbackResult.Error -> {
                if (switch.type == ErrorType.MAX_SESSIONS) {
                    ReconnectionNotification(
                        title = appContext.getString(R.string.notification_max_sessions_title),
                        content = appContext.getString(
                            if (userData.isUserPlusOrAbove) R.string.notification_max_sessions_content
                            else R.string.notification_max_sessions_upsell_content
                        ),
                        action = if (!userData.isUserPlusOrAbove) ActionItem(
                            appContext.getString(R.string.upgrade), getPendingIntentForDashboard()
                        ) else null,
                        fullScreenDialog = FullScreenDialog(
                            fullScreenIcon = if (userData.isUserPlusOrAbove)
                                R.drawable.ic_exclamation_tunnel_illustration
                            else
                                R.drawable.ic_upsell_tunnel_illustration
                        )
                    )
                } else null
            }
        }
    }

    private fun getPendingIntentForDashboard(): PendingIntent = PendingIntent.getActivity(
        appContext,
        Constants.NOTIFICATION_INFO_ID,
        Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(Constants.DASHBOARD_URL) },
        PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildReconnectionInfo(switch: VpnFallbackResult.Switch): NotificationHelper.ReconnectionInformation? {
        val toServer = switch.toProfile.server
        val fromServer = switch.fromServer
        return if (toServer != null && fromServer != null) {
            NotificationHelper.ReconnectionInformation(
                fromServerName = fromServer.serverName,
                fromCountry = fromServer.exitCountry,
                fromCountrySecureCore = if (fromServer.isSecureCoreServer) fromServer.entryCountry else null,
                toServerName = toServer.serverName,
                toCountry = toServer.exitCountry,
                toCountrySecureCore = if (toServer.isSecureCoreServer) toServer.entryCountry else null
            )
        } else null
    }

    private fun getCancelToastMessage(reason: SwitchServerReason) = when (reason) {
        is SwitchServerReason.Downgrade -> appContext.getString(
            if (reason.toTier == "free") R.string.notification_cancel_to_free
            else R.string.notification_cancel_to_basic
        )
        SwitchServerReason.UserBecameDelinquent -> appContext.getString(R.string.notification_cancel_to_delinquent)
        else -> null
    }
}