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
class ShowUpgradeSuccess constructor(
    mainScope: CoroutineScope,
    foregroundActivityTracker: ForegroundActivityTracker,
    userPlanManager: UserPlanManager,
    private val currentUser: CurrentUser,
    private val upgradeTelemetry: UpgradeTelemetry,
    private val startUpgradeActivity: (Context, String, Boolean) -> Unit
) {
    private var doNotShowForPlan: String = ""

    @Inject
    constructor(
        mainScope: CoroutineScope,
        foregroundActivityTracker: ForegroundActivityTracker,
        userPlanManager: UserPlanManager,
        currentUser: CurrentUser,
        upgradeTelemetry: UpgradeTelemetry
    ) : this(
        mainScope,
        foregroundActivityTracker,
        userPlanManager,
        currentUser,
        upgradeTelemetry,
        { context, newPlan, refreshVpnInfo ->
            val intent = CongratsPlanActivity.createIntent(context, newPlan, refreshVpnInfo)
            context.startActivity(intent)
        }
    )

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
                } else {
                    doNotShowForPlan = ""
                }
            }
        }
    }

    private suspend fun shouldShowUpgradeSuccess(upgraded: VpnUser): Boolean {
        return currentUser.vpnUser()?.userId == upgraded.userId && !upgraded.isFreeUser && doNotShowForPlan != upgraded.userTierName
    }

    fun showPlanUpgradeSuccess(context: Context, newPlan: String, refreshVpnInfo: Boolean) {
        doNotShowForPlan = newPlan
        upgradeTelemetry.onUpgradeSuccess(newPlan)
        startUpgradeActivity(context, newPlan, refreshVpnInfo)
    }
}
