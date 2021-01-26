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

import android.content.Context
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow

class UserPlanManager(
    private val api: ProtonApiRetroFit,
    private val userData: UserData,
    private val vpnStateMonitor: VpnStateMonitor,
) {

    sealed class InfoChange {
        sealed class PlanChange : InfoChange() {
            object TrialEnded : PlanChange()
            object Downgrade : PlanChange()
            object Upgrade : PlanChange()
        }

        object VpnCredentials : InfoChange()
    }

    fun isTrialUser() = "trial" == userData.vpnInfoResponse?.userTierName

    val planChangeFlow = MutableSharedFlow<InfoChange>()

    suspend fun refreshVpnInfo() {
        api.getVPNInfo().valueOrNull?.let { newInfo ->
            val oldInfo = userData.vpnInfoResponse
            userData.vpnInfoResponse = newInfo
            if (oldInfo != null) {
                if (newInfo.password != oldInfo.password || newInfo.vpnUserName != oldInfo.vpnUserName)
                    planChangeFlow.emit(InfoChange.VpnCredentials)
                when {
                    newInfo.userTier < oldInfo.userTier ->
                        planChangeFlow.emit(if (oldInfo.userTierName == "trial") {
                            InfoChange.PlanChange.TrialEnded
                        } else {
                            InfoChange.PlanChange.Downgrade
                        })
                    newInfo.userTier > oldInfo.userTier ->
                        planChangeFlow.emit(InfoChange.PlanChange.Upgrade)
                }
            }
        }
    }

    fun getTrialPeriodFlow(context: Context) = flow {
        while (userData.isTrialUser) {
            if (userData.vpnInfoResponse?.vpnInfo?.isRemainingTimeAccessible == true &&
                userData.vpnInfoResponse?.isTrialExpired == true) {
                refreshVpnInfo()
                return@flow
            } else {
                if (userData.vpnInfoResponse?.vpnInfo?.isRemainingTimeAccessible == false &&
                    vpnStateMonitor.isConnected) refreshVpnInfo()
            }
            emit(userData.vpnInfoResponse?.getTrialRemainingTimeString(context))
            delay(TRIAL_UPDATE_DELAY_MILLIS)
        }
    }

    companion object {
        private const val TRIAL_UPDATE_DELAY_MILLIS: Long = 1000
    }
}
