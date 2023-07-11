package com.protonvpn.android.vpn

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.notifications.NotificationActionReceiver
import com.protonvpn.android.notifications.NotificationHelper
import com.protonvpn.android.notifications.NotificationHelper.ActionItem
import com.protonvpn.android.notifications.NotificationHelper.Companion.EXTRA_SWITCH_PROFILE
import com.protonvpn.android.notifications.NotificationHelper.FullScreenDialog
import com.protonvpn.android.notifications.NotificationHelper.ReconnectionNotification
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.home.vpn.SwitchDialogActivity
import com.protonvpn.android.ui.planupgrade.EmptyUpgradeDialogActivity
import com.protonvpn.android.utils.AndroidUtils.launchActivity
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.UserPlanManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnErrorUIManager @Inject constructor(
    scope: CoroutineScope,
    @ApplicationContext private val appContext: Context,
    private val userSettings: EffectiveCurrentUserSettings,
    private val currentUser: CurrentUser,
    private val userPlanManager: UserPlanManager,
    private val stateMonitor: VpnStateMonitor,
    private val notificationHelper: NotificationHelper,
    private val foregroundActivityTracker: ForegroundActivityTracker
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
                            action = createPlanUpgradeAction(),
                            fullScreenDialog = FullScreenDialog(null, true, null)
                        )
                    )
                }
            }
        }
        scope.launch {
            stateMonitor.vpnConnectionNotificationFlow.collect { switch ->
                if (switch is VpnFallbackResult.Switch.SwitchServer && !switch.compatibleProtocol) {
                    val actionIntent = NotificationActionReceiver.createIntent(
                        appContext,
                        NotificationActionReceiver.SMART_PROTOCOL_ACTION
                    ).apply {
                        putExtra(EXTRA_SWITCH_PROFILE, switch.toProfile)
                    }
                    notificationHelper.showInformationNotification(
                        content = R.string.notification_smart_protocol_disabled_content,
                        title = R.string.notification_smart_protocol_disabled_title,
                        icon = R.drawable.ic_vpn_status_information,
                        action = ActionItem.BgAction(
                            title = appContext.getString(R.string.enable),
                            pendingIntent = PendingIntent.getBroadcast(
                                appContext,
                                Constants.NOTIFICATION_INFO_ID,
                                actionIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                        )
                    )
                } else if (shouldAlwaysInform(switch) || userSettings.vpnAcceleratorNotifications.first()) {
                    buildNotificationInformation(switch)?.let {
                        displayInformation(it)
                    }
                }
            }
        }
    }

    private fun displayInformation(reconnectionNotification: ReconnectionNotification) {
        val foregroundActivity = foregroundActivityTracker.foregroundActivity
        if (foregroundActivity != null && reconnectionNotification.fullScreenDialog != null) {
            foregroundActivity.launchActivity<SwitchDialogActivity>(init = {
                putExtra(SwitchDialogActivity.EXTRA_NOTIFICATION_DETAILS, reconnectionNotification)
            })
        } else {
            notificationHelper.buildSwitchNotification(reconnectionNotification)
        }
    }

    private fun shouldAlwaysInform(switch: VpnFallbackResult): Boolean {
        return when (switch) {
            is VpnFallbackResult.Switch ->
                switch.reason is SwitchServerReason.Downgrade ||
                        switch.reason is SwitchServerReason.UserBecameDelinquent
            is VpnFallbackResult.Error -> switch.type == ErrorType.MAX_SESSIONS
        }
    }

    private fun buildNotificationInformation(switch: VpnFallbackResult): ReconnectionNotification? {
        return when (switch) {
            is VpnFallbackResult.Switch -> {
                switch.reason?.let {
                    ReconnectionNotification(
                        title = notificationHelper.getContentTitle(it),
                        content = notificationHelper.getContentString(it),
                        reconnectionInformation = buildReconnectionInfo(switch),
                        action = if (it is SwitchServerReason.Downgrade || it is SwitchServerReason.UserBecameDelinquent)
                            createPlanUpgradeAction()
                        else null,
                        fullScreenDialog = if (it is SwitchServerReason.Downgrade || it is SwitchServerReason.UserBecameDelinquent)
                            FullScreenDialog(hasUpsellLayout = true, cancelToastMessage = getCancelToastMessage(it))
                        else
                            null
                    )
                }
            }
            is VpnFallbackResult.Error -> {
                if (switch.type == ErrorType.MAX_SESSIONS) {
                    val isUserPlusOrAbove = currentUser.vpnUserCached()?.isUserPlusOrAbove == true
                    val content = if (isUserPlusOrAbove) {
                        appContext.getString(R.string.notification_max_sessions_content)
                    } else {
                        appContext.resources.getQuantityString(
                            R.plurals.notification_max_sessions_upsell_content,
                            Constants.MAX_CONNECTIONS_IN_PLUS_PLAN,
                            Constants.MAX_CONNECTIONS_IN_PLUS_PLAN,
                        )
                    }
                    ReconnectionNotification(
                        title = appContext.getString(R.string.notification_max_sessions_title),
                        content = content,
                        action = if (!isUserPlusOrAbove) createPlanUpgradeAction() else null,
                        fullScreenDialog = FullScreenDialog(
                            fullScreenIcon = if (isUserPlusOrAbove)
                                R.drawable.maximum_device_limit_warning
                            else
                                R.drawable.maximum_device_limit_upsell
                        )
                    )
                } else null
            }
        }
    }

    private fun createPlanUpgradeAction(): ActionItem = ActionItem.Activity(
        appContext.getString(R.string.upgrade),
        Intent(appContext, EmptyUpgradeDialogActivity::class.java),
        true
    )

    private fun buildReconnectionInfo(switch: VpnFallbackResult.Switch): NotificationHelper.ReconnectionInformation? {
        val toServer = switch.toServer
        val fromServer = switch.fromServer
        return if (fromServer != null && switch.reason !is SwitchServerReason.UserBecameDelinquent) {
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
