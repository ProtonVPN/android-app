/*
 * Copyright (c) 2023 Proton AG
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
package com.protonvpn.android.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import com.protonvpn.android.telemetry.UpgradeAbTest
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.telemetry.UpgradeTrigger
import com.protonvpn.android.ui.planupgrade.UpgradeDialogLauncher
import com.protonvpn.android.ui.planupgrade.comparison_table.IsUpsellComparisonTableEnabled
import com.protonvpn.android.utils.ServerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FreeConnectionsInfoViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val upgradeTelemetry: UpgradeTelemetry,
    private val upgradeDialogLauncher: UpgradeDialogLauncher,
    private val isUpsellComparisonTableEnabled: dagger.Lazy<IsUpsellComparisonTableEnabled>,
) : ViewModel() {

    val freeCountriesCodes get() = serverManager.freeCountries.map { it.flag }

    suspend fun reportUpgradeFlowStart(upgradeSource: UpgradeSource, upgradeTrigger: UpgradeTrigger) {
        val abGroup = if (isUpsellComparisonTableEnabled.get().invoke()) {
            UpgradeAbTest.COMPARISON_TABLE
        } else {
            UpgradeAbTest.CONTROL
        }
        upgradeTelemetry.onUpgradeFlowStarted(upgradeSource, upgradeTrigger, abGroup)
    }

    fun launchUpgradeDialog(context: Context) {
        upgradeDialogLauncher.launchCountries(context, UpgradeTrigger.HOME, null)
    }
}
