/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.utils

import androidx.annotation.VisibleForTesting
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.periodicupdates.IsInForeground
import com.protonvpn.android.appconfig.periodicupdates.IsLoggedIn
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateSpec
import com.protonvpn.android.appconfig.periodicupdates.registerApiCall
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.SetVpnUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UserPlanChanged
import com.protonvpn.android.logging.toLog
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.models.login.toVpnUserEntity
import com.protonvpn.android.utils.AndroidUtils.whenNotNullNorEmpty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.mapNotNull
import me.proton.core.network.domain.ApiResult
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPlanManager @Inject constructor(
    private val api: ProtonApiRetroFit,
    private val currentUser: CurrentUser,
    private val setVpnUser: SetVpnUser,
    private val periodicUpdateManager: PeriodicUpdateManager,
    @WallClock private val wallClock: () -> Long,
    @IsLoggedIn loggedIn: Flow<Boolean>,
    @IsInForeground inForeground: Flow<Boolean>,
) {
    sealed class InfoChange {
        data class PlanChange(val oldUser: VpnUser, val newUser: VpnUser) : InfoChange() {
            val isDowngrade get() = oldUser.userTier > newUser.userTier
        }
        object UserBecameDelinquent : InfoChange()
        object VpnCredentials : InfoChange()

        override fun toString(): String = this.javaClass.simpleName
    }

    private val vpnInfoUpdate = periodicUpdateManager.registerApiCall(
        "vpn_info",
        ::refreshVpnInfoInternal,
        PeriodicUpdateSpec(
            TimeUnit.MINUTES.toMillis(Constants.VPN_INFO_REFRESH_INTERVAL_MINUTES),
            setOf(inForeground, loggedIn)
        )
    )

    // Note: don't use CurrentUser.vpnUserCached in code observing this flow. The cached value is updated later.
    val infoChangeFlow = MutableSharedFlow<List<InfoChange>>()

    val planChangeFlow = infoChangeFlow.mapNotNull { changes ->
        changes.firstOrNull { it is InfoChange.PlanChange } as? InfoChange.PlanChange
    }

    suspend fun refreshVpnInfo(): ApiResult<VpnInfoResponse> =
        periodicUpdateManager.executeNow(vpnInfoUpdate)

    @VisibleForTesting
    suspend fun refreshVpnInfoInternal(): ApiResult<VpnInfoResponse> {
        val result = api.getVPNInfo()
        val changes = result.valueOrNull?.let { vpnInfoResponse ->
            currentUser.vpnUser()?.let { currentUserInfo ->
                val newUserInfo =
                    with(currentUserInfo) { vpnInfoResponse.toVpnUserEntity(userId, sessionId, wallClock(), autoLoginName) }
                setVpnUser(newUserInfo)
                computeUserInfoChanges(currentUserInfo, newUserInfo)
            }
        }
        changes.whenNotNullNorEmpty {
            infoChangeFlow.emit(it)
        }
        return result
    }

    fun computeUserInfoChanges(currentUserInfo: VpnUser, newUserInfo: VpnUser): List<InfoChange> {
        val changes = mutableListOf<InfoChange>()
        if (newUserInfo.password != currentUserInfo.password || newUserInfo.name != currentUserInfo.name)
            changes += InfoChange.VpnCredentials
        if (newUserInfo.isUserDelinquent && !currentUserInfo.isUserDelinquent)
            changes += InfoChange.UserBecameDelinquent
        if (newUserInfo.userTier != currentUserInfo.userTier || newUserInfo.userTierName != currentUserInfo.userTierName)
            changes += InfoChange.PlanChange(currentUserInfo, newUserInfo)
        changes.whenNotNullNorEmpty {
            ProtonLogger.log(UserPlanChanged, "change: $it, user: ${newUserInfo.toLog()}")
        }
        return changes
    }
}
