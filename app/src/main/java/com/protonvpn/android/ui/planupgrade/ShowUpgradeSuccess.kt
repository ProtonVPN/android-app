/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.ui.planupgrade

import android.content.Context
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.utils.UserPlanManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShowUpgradeSuccess @Inject constructor(
    mainScope: CoroutineScope,
    foregroundActivityTracker: ForegroundActivityTracker,
    userPlanManager: UserPlanManager,
    private val currentUser: CurrentUser,
    private val upgradeTelemetry: UpgradeTelemetry,
) {
    init {
        mainScope.launch {
            userPlanManager.planChangeFlow.collectLatest { planUpgrade ->
                val activity =
                    foregroundActivityTracker.foregroundActivityFlow.filterNotNull().first()
                val upgradedUser = planUpgrade.newUser
                if (shouldShowUpgradeSuccess(upgradedUser)) {
                    showPlanUpgradeSuccess(
                        activity,
                        upgradedUser.userTierName,
                        refreshVpnInfo = false
                    )
                }
            }
        }
    }

    private suspend fun shouldShowUpgradeSuccess(upgraded: VpnUser): Boolean {
        return currentUser.vpnUser()?.userId == upgraded.userId && !upgraded.isFreeUser
    }

    fun showPlanUpgradeSuccess(context: Context, newPlan: String, refreshVpnInfo: Boolean) {
        upgradeTelemetry.onUpgradeSuccess(newPlan)
        context.startActivity(CongratsPlanActivity.createIntent(context, newPlan, refreshVpnInfo = refreshVpnInfo))
    }
}
