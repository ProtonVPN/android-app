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

package com.protonvpn.android.ui.planupgrade.comparison_table

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.protonvpn.android.R

@Composable
fun UpsellNetShieldTablePanel(
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.systemBars
) {
    UpsellComparisonTablePanel(
        titleRes = R.string.upsell_panel_netshield_title,
        imageRes = R.drawable.upsell_header_netshield,
        windowInsets = windowInsets,
        modifier = modifier
    ) {
        Column {
            BenefitTableFreePlusHeader()
            BenefitTableRowNoYes(stringResource(R.string.upsell_panel_netshield_benefit_ads))
            BenefitTableRowNoYes(stringResource(R.string.upsell_panel_netshield_benefit_trackers))
            BenefitTableRowNoYes(stringResource(R.string.upsell_panel_netshield_benefit_malware))
            BenefitTableRowNoYes(
                stringResource(R.string.upsell_panel_netshield_benefit_load_speed),
                secondPlanBackgroundShape = BenefitTableRowDefaults.ShapeBottom,
                bottomSeparator = false,
            )
        }
    }
}
