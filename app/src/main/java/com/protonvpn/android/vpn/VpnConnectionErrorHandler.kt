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

package com.protonvpn.android.vpn

import android.content.Context
import com.protonvpn.android.R
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.ProtonLogger
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.UserPlanManager.InfoChange.PlanChange
import com.protonvpn.android.utils.UserPlanManager.InfoChange.VpnCredentials
import com.protonvpn.android.utils.UserPlanManager.InfoChange.UserBecameDelinquent
import io.sentry.event.EventBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.proton.core.network.domain.ApiResult

enum class SwitchServerReason {
    DowngradeToBasic,
    DowngradeToFree,
    Downgrade,
    TrialEnded,
    UserBecameDelinquent,
    ServerInMaintenance
}

sealed class VpnFallbackResult {

    data class SwitchProfile(
        val profile: Profile,

        // null means change should be transparent for the user (no notification)
        val notificationReason: SwitchServerReason? = null
    ) : VpnFallbackResult()

    data class Error(val type: ErrorType) : VpnFallbackResult()
}

class VpnConnectionErrorHandler(
    scope: CoroutineScope,
    private val appContext: Context,
    private val api: ProtonApiRetroFit,
    private val appConfig: AppConfig,
    private val userData: UserData,
    private val userPlanManager: UserPlanManager,
    private val serverManager: ServerManager,
    private val stateMonitor: VpnStateMonitor,
    private val serverListUpdater: ServerListUpdater,
    private val notificationHelper: NotificationHelper
) {
    var handlingAuthError = false

    val switchConnectionFlow = MutableSharedFlow<VpnFallbackResult.SwitchProfile>()

    init {
        scope.launch {
            userPlanManager.infoChangeFlow.collect { changes ->
                if (!handlingAuthError) {
                    getCommonFallbackForInfoChanges(changes)?.let {
                        switchConnectionFlow.emit(it)
                    }
                }
            }
        }
    }

    @SuppressWarnings("ReturnCount")
    private fun getCommonFallbackForInfoChanges(
        changes: List<UserPlanManager.InfoChange>
    ): VpnFallbackResult.SwitchProfile? {
        for (change in changes) when (change) {
            PlanChange.TrialEnded ->
                return VpnFallbackResult.SwitchProfile(
                    serverManager.defaultFallbackConnection,
                    SwitchServerReason.TrialEnded)
            PlanChange.Downgrade -> {
                val reason = when {
                    userData.isFreeUser -> SwitchServerReason.DowngradeToFree
                    userData.isBasicUser -> SwitchServerReason.DowngradeToBasic
                    else -> SwitchServerReason.Downgrade
                }
                return VpnFallbackResult.SwitchProfile(
                    serverManager.defaultFallbackConnection,
                    reason)
            }
            UserBecameDelinquent ->
                return VpnFallbackResult.SwitchProfile(
                    serverManager.defaultFallbackConnection,
                    SwitchServerReason.UserBecameDelinquent)
            else -> {}
        }
        return null
    }

    @SuppressWarnings("ReturnCount")
    suspend fun onAuthError(profile: Profile): VpnFallbackResult {
        try {
            handlingAuthError = true
            userPlanManager.refreshVpnInfo()?.let { infoChanges ->
                getCommonFallbackForInfoChanges(infoChanges)?.let {
                    return it
                }

                if (VpnCredentials in infoChanges)
                    // Now that credentials are refreshed we can try reconnecting.
                    return VpnFallbackResult.SwitchProfile(profile)

                val vpnInfo = requireNotNull(userData.vpnInfoResponse)
                val sessionCount = api.getSession().valueOrNull?.sessionList?.size ?: 0
                if (vpnInfo.maxSessionCount <= sessionCount)
                    return VpnFallbackResult.Error(ErrorType.MAX_SESSIONS)
            }

            val maintenanceProfile = getMaintenanceFallbackProfile()
            return if (maintenanceProfile != null)
                VpnFallbackResult.SwitchProfile(maintenanceProfile, SwitchServerReason.ServerInMaintenance)
            else
                VpnFallbackResult.Error(ErrorType.AUTH_FAILED)
        } finally {
            handlingAuthError = false
        }
    }

    private suspend fun getMaintenanceFallbackProfile(): Profile? {
        if (!appConfig.isMaintenanceTrackerEnabled())
            return null

        ProtonLogger.log("Check if server is not in maintenance")
        val domainId = stateMonitor.connectionParams!!.connectingDomain?.id ?: return null
        val result = api.getConnectingDomain(domainId)
        if (result is ApiResult.Success) {
            val connectingDomain = result.value.connectingDomain
            if (!connectingDomain.isOnline) {
                serverManager.updateServerDomainStatus(connectingDomain)
                serverListUpdater.updateServerList()
                val sentryEvent = EventBuilder()
                    .withMessage("Maintenance detected")
                    .withExtra("Server", result.value.connectingDomain.entryDomain)
                    .build()
                ProtonLogger.logSentryEvent(sentryEvent)
                notificationHelper.showInformationNotification(
                    appContext, appContext.getString(R.string.onMaintenanceDetected)
                )
                return serverManager.defaultFallbackConnection
            }
        }
        return null
    }

    suspend fun maintenanceCheck() {
        getMaintenanceFallbackProfile()?.let {
            switchConnectionFlow.emit(VpnFallbackResult.SwitchProfile(it, SwitchServerReason.ServerInMaintenance))
        }
    }
}
