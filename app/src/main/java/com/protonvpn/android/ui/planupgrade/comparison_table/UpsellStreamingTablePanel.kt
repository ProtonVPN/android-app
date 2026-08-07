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
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.utils.Constants
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun UpsellStreamingBlockTablePanel(
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.systemBars
) {
    UpsellComparisonTablePanel(
        titleRes = R.string.upsell_panel_streaming_block_title,
        descriptionRes = R.string.upsell_panel_streaming_block_description,
        imageRes = R.drawable.upsell_header_streaming_block,
        windowInsets = windowInsets,
        modifier = modifier
    ) {
        StreamingBenefitsColumn()
    }
}

@Composable
fun UpsellStreamingTablePanel(
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.systemBars
) {
    UpsellComparisonTablePanel(
        titleRes = R.string.upsell_panel_streaming_title,
        imageRes = R.drawable.upsell_header_streaming,
        windowInsets = windowInsets,
        modifier = modifier
    ) {
        StreamingBenefitsColumn()
    }
}

@Composable
private fun StreamingBenefitsColumn(
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        BenefitTableFreePlusHeader()
        BenefitTableRowNoYes(stringResource(R.string.upsell_panel_streaming_benefit_shows))
        BenefitTableRowNoYes(stringResource(R.string.upsell_panel_streaming_benefit_platforms))
        BenefitTableRowNoYes(stringResource(R.string.upsell_panel_streaming_benefit_hd))
        BenefitTableRow(
            stringResource(R.string.upsell_panel_streaming_benefit_devices),
            { Text("%d".format(1)) },
            { Text("%d".format(Constants.MAX_CONNECTIONS_IN_PLUS_PLAN)) },
            secondPlanBackgroundShape = BenefitTableRowDefaults.ShapeBottom,
            bottomSeparator = false,
        )

        VerticalSpacer(height = 12.dp)
        Text(
            stringResource(R.string.upsell_panel_streaming_footer),
            style = ProtonTheme.typography.captionRegular,
            color = ProtonTheme.colors.textWeak,
        )
    }
}
