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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.protonvpn.android.R

@Composable
fun UpsellSpeedTablePanel(
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.systemBars
) {
    UpsellComparisonTablePanel(
        titleRes = R.string.upsell_panel_speed_title,
        imageRes = R.drawable.upsell_header_speed,
        windowInsets = windowInsets,
        modifier = modifier
    ) {
        Column {
            BenefitTableFreePlusHeader()
            BenefitTableRowNoYes(stringResource(R.string.upsell_panel_speed_benefit_servers))
            BenefitTableRow(
                stringResource(R.string.upsell_panel_speed_benefit_gaming),
                firstPlanContent = {
                    Text(stringResource(R.string.upsell_panel_speed_benefit_gaming_slow))
                },
                secondPlanContent = {
                    Text(stringResource(R.string.upsell_panel_speed_benefit_gaming_fast))
                },
            )
            BenefitTableRowNoYes(stringResource(R.string.upsell_panel_speed_benefit_video))
            BenefitTableRowNoYes(
                stringResource(R.string.upsell_panel_speed_benefit_p2p),
                secondPlanBackgroundShape = BenefitTableRowDefaults.ShapeBottom,
                bottomSeparator = false,
            )
        }
    }
}
