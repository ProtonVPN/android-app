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

import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.UserPlanManager.InfoChange.PlanChange
import com.protonvpn.android.utils.UserPlanManager.InfoChange.VpnCredentials
import com.protonvpn.android.utils.UserPlanManager.InfoChange.UserBecameDelinquent
import kotlinx.coroutines.flow.mapNotNull

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
    private val api: ProtonApiRetroFit,
    private val userData: UserData,
    private val userPlanManager: UserPlanManager,
    private val serverManager: ServerManager,
    private val maintenanceTracker: MaintenanceTracker
) {
    var handlingAuthError = false

    val switchConnectionFlow = userPlanManager.infoChangeFlow
        .mapNotNull { changes ->
            if (!handlingAuthError)
                getCommonFallbackForInfoChanges(changes)
            else
                null
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

            val maintenanceProfile = maintenanceTracker.getMaintenanceFallbackProfile()
            return if (maintenanceProfile != null)
                VpnFallbackResult.SwitchProfile(maintenanceProfile, SwitchServerReason.ServerInMaintenance)
            else
                VpnFallbackResult.Error(ErrorType.AUTH_FAILED)
        } finally {
            handlingAuthError = false
        }
    }
}
