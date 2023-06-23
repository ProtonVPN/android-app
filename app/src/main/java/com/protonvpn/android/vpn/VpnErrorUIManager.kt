package com.protonvpn.android.vpn

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.notifications.NotificationActionReceiver
import com.protonvpn.android.notifications.NotificationHelper
import com.protonvpn.android.notifications.NotificationHelper.ActionItem
import com.protonvpn.android.notifications.NotificationHelper.FullScreenDialog
import com.protonvpn.android.notifications.NotificationHelper.InformationNotification
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.home.vpn.SwitchDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesHighlightsFragment
import com.protonvpn.android.utils.AndroidUtils.launchActivity
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.UserPlanManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
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
                if (it.isDowngrade && !stateMonitor.isEstablishingOrConnected) {
                    displayInformation(
                        InformationNotification(
                            title = appContext.getString(R.string.notification_subscription_expired_title),
                            content = appContext.getString(R.string.notification_subscription_expired_no_reconnection_content),
                            reconnectionInformation = null,
                            action = createPlanUpgradeAction(UpgradeSource.DOWNGRADE),
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
                        putExtra(NotificationActionReceiver.EXTRA_SWITCH_INTENT, switch.connectIntent.toData())
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
                } else if (shouldAlwaysInform(switch)) {
                    buildNotificationInformation(switch)?.let {
                        displayInformation(it)
                    }
                }
            }
        }
    }

    private fun displayInformation(informationNotification: InformationNotification) {
        val foregroundActivity = foregroundActivityTracker.foregroundActivity
        if (foregroundActivity != null && informationNotification.fullScreenDialog != null) {
            foregroundActivity.launchActivity<SwitchDialogActivity>(init = {
                putExtra(SwitchDialogActivity.EXTRA_NOTIFICATION_DETAILS, informationNotification)
            })
        } else {
            notificationHelper.buildSwitchNotification(informationNotification)
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

    private fun buildNotificationInformation(switch: VpnFallbackResult): InformationNotification? {
        return when (switch) {
            is VpnFallbackResult.Switch -> {
                when (val reason = switch.reason) {
                    is SwitchServerReason.Downgrade -> {
                        InformationNotification(
                            title = appContext.getString(R.string.notification_subscription_expired_title),
                            content = appContext.getString(R.string.notification_subscription_expired_content),
                            reconnectionInformation = switch.fromServer?.let {
                                NotificationHelper.ReconnectionInformation(
                                    fromServerName = it.serverName,
                                    fromCountry = it.exitCountry,
                                    fromCountrySecureCore = if (it.isSecureCoreServer) it.entryCountry else null,
                                    toServerName = switch.toServer.serverName,
                                    toCountry = switch.toServer.exitCountry,
                                    toCountrySecureCore = if (switch.toServer.isSecureCoreServer) switch.toServer.entryCountry else null
                                )
                            },
                            action = createPlanUpgradeAction(UpgradeSource.DOWNGRADE),
                            fullScreenDialog = FullScreenDialog(hasUpsellLayout = true, cancelToastMessage = getCancelToastMessage(reason))
                        )
                    }
                    is SwitchServerReason.UserBecameDelinquent -> {
                        InformationNotification(
                            title = appContext.getString(R.string.notification_delinquent_title),
                            content = appContext.getString(R.string.notification_delinquent_content),
                            action = createPlanUpgradeAction(UpgradeSource.DOWNGRADE),
                            fullScreenDialog = FullScreenDialog(hasUpsellLayout = true, cancelToastMessage = getCancelToastMessage(reason))
                        )
                    }
                    SwitchServerReason.ServerInMaintenance, SwitchServerReason.ServerUnavailable, SwitchServerReason.ServerUnreachable, SwitchServerReason.UnknownAuthFailure, null -> {
                        null
                    }
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
                    val action =
                        if (!isUserPlusOrAbove) createPlanUpgradeAction(UpgradeSource.MAX_CONNECTIONS) else null
                    InformationNotification(
                        title = appContext.getString(R.string.notification_max_sessions_title),
                        content = content,
                        action = action,
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

    private fun createPlanUpgradeAction(upgradeSource: UpgradeSource?): ActionItem = ActionItem.Activity(
        appContext.getString(R.string.upgrade),
        UpgradeDialogActivity.createIntent<UpgradeDialogActivity, UpgradePlusCountriesHighlightsFragment>(appContext),
        true,
        upgradeSource
    )

    private fun getCancelToastMessage(reason: SwitchServerReason) = when (reason) {
        is SwitchServerReason.Downgrade -> appContext.getString(
            if (reason.toTier == "free") R.string.notification_cancel_to_free
            else R.string.notification_cancel_to_basic
        )
        SwitchServerReason.UserBecameDelinquent -> appContext.getString(R.string.notification_cancel_to_delinquent)
        else -> null
    }
}
