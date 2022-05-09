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

import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.data.VpnUserDao
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UserPlanChanged
import com.protonvpn.android.logging.toLog
import com.protonvpn.android.models.login.toVpnUserEntity
import com.protonvpn.android.utils.AndroidUtils.whenNotNullNorEmpty
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPlanManager @Inject constructor(
    private val api: ProtonApiRetroFit,
    private val currentUser: CurrentUser,
    private val vpnUserDao: VpnUserDao,
) {
    sealed class InfoChange {
        sealed class PlanChange : InfoChange() {
            data class Downgrade(val fromPlan: String, val toPlan: String) : PlanChange()
            object Upgrade : PlanChange()
        }
        object UserBecameDelinquent : InfoChange()
        object VpnCredentials : InfoChange()

        override fun toString(): String = this.javaClass.simpleName
    }

    // Note: don't use CurrentUser.vpnUserCached in code observing this flow. The cached value is updated later.
    val infoChangeFlow = MutableSharedFlow<List<InfoChange>>()

    val planChangeFlow = infoChangeFlow.mapNotNull { changes ->
        changes.firstOrNull { it is InfoChange.PlanChange } as? InfoChange.PlanChange
    }

    // Will return list of changes (can be empty) or null if refresh failed.
    suspend fun refreshVpnInfo(): List<InfoChange>? {
        val changes = api.getVPNInfo().valueOrNull?.let { vpnInfoResponse ->
            val changes = mutableListOf<InfoChange>()
            currentUser.vpnUser()?.let { currentUserInfo ->
                val newUserInfo = vpnInfoResponse.toVpnUserEntity(currentUserInfo.userId, currentUserInfo.sessionId)
                vpnUserDao.insertOrUpdate(newUserInfo)

                if (newUserInfo.password != currentUserInfo.password || newUserInfo.name != currentUserInfo.name)
                    changes += InfoChange.VpnCredentials
                if (newUserInfo.isUserDelinquent && !currentUserInfo.isUserDelinquent)
                    changes += InfoChange.UserBecameDelinquent
                when {
                    newUserInfo.userTier < currentUserInfo.userTier -> {
                        changes +=
                            InfoChange.PlanChange.Downgrade(currentUserInfo.userTierName, newUserInfo.userTierName)
                    }
                    newUserInfo.userTier > currentUserInfo.userTier ->
                        changes += InfoChange.PlanChange.Upgrade
                }
                changes.whenNotNullNorEmpty {
                    ProtonLogger.log(UserPlanChanged, "change: $it, user: ${newUserInfo.toLog()}")
                }
            }
            changes
        }
        changes.whenNotNullNorEmpty {
            infoChangeFlow.emit(it)
        }
        return changes
    }
}
