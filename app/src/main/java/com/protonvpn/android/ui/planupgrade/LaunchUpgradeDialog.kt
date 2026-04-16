/*
 * Copyright (c) 2026. Proton AG
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
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.ui.planupgrade.comparison_table.ComparisonTableUpsells
import com.protonvpn.android.ui.planupgrade.comparison_table.IsUpsellComparisonTableEnabled
import com.protonvpn.android.ui.planupgrade.comparison_table.UpgradeDialogActivityV2
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@Reusable
class LaunchUpgradeDialog @Inject constructor(
    private val mainScope: CoroutineScope,
    private val isUpsellComparisonTableEnabled: dagger.Lazy<IsUpsellComparisonTableEnabled>,
) {
    operator fun invoke(context: Context, upgradeSource: UpgradeSource, legacyLaunch: () -> Unit) {
        mainScope.launch {
            if (upgradeSource in ComparisonTableUpsells &&
                isUpsellComparisonTableEnabled.get().invoke()
            ) {
                UpgradeDialogActivityV2.launch(context, upgradeSource)
            } else {
                legacyLaunch()
            }
        }
    }
}