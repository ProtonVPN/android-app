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

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.protonvpn.android.R

@Composable
fun UpsellOnboardingTablePanel(
    freeCountries: Int,
    plusCountriesRounded: Int,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.systemBars,
) {
    UpsellComparisonTablePanel(
        titleRes = R.string.upsell_panel_onboarding_title,
        imageRes = R.drawable.upsell_header_onboarding,
        windowInsets = windowInsets,
        modifier = modifier,
    ) {
        BenefitTableFreePlusHeader()
        BenefitTableRow(
            stringResource(R.string.upsell_panel_general_benefit_countries),
            "%d".format(freeCountries),
            stringResource(R.string.upsell_panel_general_benefit_countries_rounded, plusCountriesRounded),
        )
        BenefitTableRowNoYes(stringResource(R.string.upsell_panel_general_benefit_location))
        BenefitTableRowNoYes(stringResource(R.string.upsell_panel_general_benefit_shows))
        BenefitTableRowNoYes(
            stringResource(R.string.upsell_panel_general_benefit_block_ads),
            bottomSeparator = false,
            secondPlanBackgroundShape = BenefitTableRowDefaults.ShapeBottom
        )
    }
}
