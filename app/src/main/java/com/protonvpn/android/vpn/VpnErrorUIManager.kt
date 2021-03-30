package com.protonvpn.android.vpn

import android.content.Context
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.tv.TvUpgradeActivity
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
    private val notificationHelper: NotificationHelper)
{

    init {
        scope.launch {
            stateMonitor.fallbackConnectionFlow.collect { switch ->
                if (userData.showSmartReconnectNotifications()) {
                    buildNotificationInformation(switch)?.let {
                        if ((appContext as ProtonApplication).isForeground && it.fullScreenDialog?.hasUpsellLayout == true) {
                            appContext.launchActivity<SwitchDialogActivity>(init = {
                                putExtra(SwitchDialogActivity.EXTRA_NOTIFICATION_DETAILS, it)
                                flags = FLAG_ACTIVITY_NEW_TASK
                            })
                        } else {
                            notificationHelper.buildSwitchNotification(it)
                        }
                    }
                }
            }
        }
    }

    private fun buildNotificationInformation(switch: VpnFallbackResult): NotificationHelper.ReconnectionNotification? {
        return when (switch) {
            is VpnFallbackResult.Switch -> {
                switch.notificationReason?.let {
                    NotificationHelper.ReconnectionNotification(
                        title = notificationHelper.getContentTitle(it),
                        content = notificationHelper.getContentString(it),
                        reconnectionInformation = switch,
                        action = if (it is SwitchServerReason.Downgrade || it is SwitchServerReason.UserBecameDelinquent)
                            NotificationHelper.ActionItem(
                                appContext.getString(R.string.upgrade), Constants.DASHBOARD_URL
                            ) else null,
                        fullScreenDialog = if (it is SwitchServerReason.Downgrade || it is SwitchServerReason.UserBecameDelinquent)
                            NotificationHelper.FullScreenDialog(
                                hasUpsellLayout = true,
                                cancelToastMessage = getCancelToastMessage(it)
                            ) else null
                    )
                }
            }
            is VpnFallbackResult.Error -> {
                if (switch.type == ErrorType.MAX_SESSIONS) {
                    NotificationHelper.ReconnectionNotification(
                        title = appContext.getString(R.string.notification_max_sessions_title),
                        content = appContext.getString(
                            if (userData.isUserPlusOrAbove) R.string.notification_max_sessions_content
                            else R.string.notification_max_sessions_upsell_content
                        ),
                        action = if (!userData.isUserPlusOrAbove) NotificationHelper.ActionItem(
                            appContext.getString(R.string.upgrade), Constants.DASHBOARD_URL
                        ) else null,
                        fullScreenDialog = NotificationHelper.FullScreenDialog(R.drawable.ic_exclamation_tunnel_illustration)
                    )
                } else null
            }
        }
    }

    private fun getCancelToastMessage(reason: SwitchServerReason) = when (reason) {
        is SwitchServerReason.Downgrade -> appContext.getString(
            if (reason.toTier == "free")
                R.string.notification_cancel_to_free
            else
                R.string.notification_cancel_to_basic
        )
        SwitchServerReason.UserBecameDelinquent -> appContext.getString(R.string.notification_cancel_to_delinquent)
        else -> null
    }
}