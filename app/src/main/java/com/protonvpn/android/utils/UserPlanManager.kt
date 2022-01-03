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
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull

class UserPlanManager(
    private val api: ProtonApiRetroFit,
    private val vpnStateMonitor: VpnStateMonitor,
    private val currentUser: CurrentUser,
    private val vpnUserDao: VpnUserDao,
    private val wallClock: () -> Long
) {
    sealed class InfoChange {
        sealed class PlanChange : InfoChange() {
            object TrialEnded : PlanChange()
            data class Downgrade(val fromPlan: String, val toPlan: String) : PlanChange()
            object Upgrade : PlanChange()
        }
        object UserBecameDelinquent : InfoChange()
        object VpnCredentials : InfoChange()

        override fun toString(): String = this.javaClass.simpleName
    }

    fun isTrialUser() = currentUser.vpnUserCached()?.isTrialUser == true

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
                        changes += if (currentUserInfo.isTrialUser) {
                            Storage.saveBoolean(PREF_EXPIRATION_DIALOG_DUE, true)
                            InfoChange.PlanChange.TrialEnded
                        } else {
                            InfoChange.PlanChange.Downgrade(currentUserInfo.userTierName, newUserInfo.userTierName)
                        }
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

    fun getTrialPeriodFlow() = flow {
        do {
            val user = currentUser.vpnUser()
            if (user == null || !user.isTrialUser)
                break

            if (user.isRemainingTimeAccessible && user.isTrialExpired(wallClock())) {
                refreshVpnInfo()
                break
            }
            if (!user.isRemainingTimeAccessible && vpnStateMonitor.isConnected)
                refreshVpnInfo()

            currentUser.vpnUser()?.let { emit(it.trialRemainingTime(wallClock())) }
            delay(TRIAL_UPDATE_DELAY_MILLIS)
        } while (true)
    }

    companion object {
        private const val TRIAL_UPDATE_DELAY_MILLIS: Long = 1000
        const val PREF_EXPIRATION_DIALOG_DUE = "ProtonApplication.EXPIRATION_DIALOG_DUE"
    }
}
