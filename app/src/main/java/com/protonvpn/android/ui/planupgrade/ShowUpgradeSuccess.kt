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
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.utils.UserPlanManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShowUpgradeSuccess @Inject constructor(
    mainScope: CoroutineScope,
    foregroundActivityTracker: ForegroundActivityTracker,
    userPlanManager: UserPlanManager,
    private val appFeaturesPrefs: AppFeaturesPrefs,
    private val upgradeTelemetry: UpgradeTelemetry,
) {
    init {
        combine(
            userPlanManager.planChangeFlow,
            foregroundActivityTracker.foregroundActivityFlow
        ) { planUpgrade, foregroundActivity ->
            val upgraded = planUpgrade.newUser
            if (foregroundActivity != null && upgraded.userTierName != appFeaturesPrefs.hasShownUpgradeSuccessForPlan) {
                if (!upgraded.isFreeUser) {
                    showPlanUpgradeSuccess(foregroundActivity, upgraded.userTierName, refreshVpnInfo = false)
                } else {
                    appFeaturesPrefs.hasShownUpgradeSuccessForPlan = upgraded.userTierName
                }
            }
        }.launchIn(mainScope)
    }

    fun showPlanUpgradeSuccess(context: Context, newPlan: String, refreshVpnInfo: Boolean) {
        upgradeTelemetry.onUpgradeSuccess(newPlan)
        appFeaturesPrefs.hasShownUpgradeSuccessForPlan = newPlan
        context.startActivity(CongratsPlanActivity.createIntent(context, newPlan, refreshVpnInfo = refreshVpnInfo))
    }
}
