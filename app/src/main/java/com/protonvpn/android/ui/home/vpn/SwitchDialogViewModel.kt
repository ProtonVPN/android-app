/*
 * Copyright (c) 2022 Proton AG
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

package com.protonvpn.android.ui.home.vpn

import androidx.lifecycle.ViewModel
import com.protonvpn.android.notifications.NotificationHelper
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.ui.planupgrade.IsInAppUpgradeAllowedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SwitchDialogViewModel @Inject constructor(
    private val isInAppUpgradeAllowed: IsInAppUpgradeAllowedUseCase,
    private val upgradeTelemetry: UpgradeTelemetry
) : ViewModel() {

    fun onInit(activityItem: NotificationHelper.ActionItem?) {
        if (activityItem is NotificationHelper.ActionItem.Activity && activityItem.upgradeSource != null) {
            upgradeTelemetry.onUpgradeFlowStarted(activityItem.upgradeSource)
        }
    }

    fun showUpgrade() = isInAppUpgradeAllowed()
}
