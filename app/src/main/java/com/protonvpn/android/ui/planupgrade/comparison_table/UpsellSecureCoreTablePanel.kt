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
import com.protonvpn.android.utils.Constants

@Composable
fun UpsellSecureCoreTablePanel(
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.systemBars,
) {
    UpsellComparisonTablePanel(
        titleRes = R.string.upsell_panel_secure_core_title,
        descriptionRes = R.string.upsell_panel_secure_core_description,
        imageRes = R.drawable.upsell_header_secure_core,
        windowInsets = windowInsets,
        modifier = modifier,
    ) {
        Column {
            BenefitTableFreePlusHeader()
            BenefitTableRowNoYes(stringResource(R.string.upsell_panel_secure_core_benefit_secure_core))
            BenefitTableRowNoYes(stringResource(R.string.upsell_panel_general_benefit_server))
            BenefitTableRow(
                stringResource(R.string.upsell_panel_general_benefit_devices),
                "%d".format(1),
                ("%d".format(Constants.MAX_CONNECTIONS_IN_PLUS_PLAN)),
            )
            BenefitTableRow(
                stringResource(R.string.upsell_panel_general_benefit_speed),
                stringResource(R.string.upsell_panel_general_benefit_speed_standard),
                stringResource(R.string.upsell_panel_general_benefit_speed_highest),

                bottomSeparator = false,
                secondPlanBackgroundShape = BenefitTableRowDefaults.ShapeBottom,
            )
        }
    }
}
