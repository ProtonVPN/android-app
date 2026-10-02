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

package com.protonvpn.android.tv.upsell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.ui.planupgrade.comparison_table.BenefitTableRow
import com.protonvpn.android.ui.planupgrade.comparison_table.BenefitTableRowDefaults
import com.protonvpn.android.ui.planupgrade.comparison_table.PlanPlusBadgeFrame
import me.proton.core.presentation.R as CoreR

// TV Material variants of the composables in BenefitsTable.kt.

@Composable
fun BenefitTableFreePlusHeaderTv(
    modifier: Modifier = Modifier
) {
    BenefitTableRow(
        benefitContent = null,
        firstPlanContent = {
            FirstPlanCellTv { Text(stringResource(R.string.upsell_panel_table_header_plan_free)) }
        },
        secondPlanContent = { SecondPlanCellTv { PlanPlusBadgeTv() } },
        modifier = modifier
            .clearAndSetSemantics {},
        bottomSeparator = false,
        secondPlanBackgroundShape = BenefitTableRowDefaults.ShapeTop,
        itemVerticalPadding = 8.dp,
    )
}

@Composable
fun BenefitTableRowNoYesTv(
    benefitText: String,
    modifier: Modifier = Modifier,
    bottomSeparator: Boolean = true,
    secondPlanBackgroundShape: Shape = RectangleShape,
) {
    BenefitTableRow(
        benefitContent = { BenefitCellTv { Text(benefitText) } },
        firstPlanContent = {
            FirstPlanCellTv {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_proton_cross),
                    contentDescription = stringResource(R.string.upsell_panel_content_description_no),
                )
            }
        },
        secondPlanContent = {
            SecondPlanCellTv {
                Icon(
                    painterResource(CoreR.drawable.ic_proton_checkmark_circle_filled),
                    stringResource(R.string.upsell_panel_content_description_yes),
                )
            }
        },
        modifier = modifier,
        bottomSeparator = bottomSeparator,
        secondPlanBackgroundShape = secondPlanBackgroundShape,
    )
}

@Composable
fun BenefitTableRowTv(
    benefitText: String?,
    firstPlanContentText: String,
    secondPlanContentText: String,
    firstPlanContentDescriptionPrefix: String = stringResource(R.string.upsell_panel_content_description_value_prefix_free),
    secondPlanContentDescriptionPrefix: String = stringResource(R.string.upsell_panel_content_description_value_prefix_plus),
    modifier: Modifier = Modifier,
    bottomSeparator: Boolean = true,
    secondPlanBackgroundShape: Shape = RectangleShape,
    itemVerticalPadding: Dp = 12.dp
) {
    BenefitTableRow(
        benefitContent = benefitText?.let { @Composable { BenefitCellTv { Text(benefitText) } } },
        firstPlanContent = { FirstPlanCellTv { Text(firstPlanContentText) } },
        secondPlanContent = { SecondPlanCellTv { Text(secondPlanContentText) } },
        firstPlanContentDescriptionPrefix,
        secondPlanContentDescriptionPrefix,
        modifier,
        bottomSeparator,
        secondPlanBackgroundShape,
        itemVerticalPadding,
    )
}

@Composable
fun PlanPlusBadgeTv(
    modifier: Modifier = Modifier,
) {
    PlanPlusBadgeFrame(modifier) {
        Text(stringResource(R.string.badge_plan_plus))
    }
}

@Composable
private fun BenefitCellTv(content: @Composable () -> Unit) = ProvideCellStyleTv(
    BenefitTableRowDefaults.benefitContentColor,
    BenefitTableRowDefaults.benefitTextStyle,
    content,
)

@Composable
private fun FirstPlanCellTv(content: @Composable () -> Unit) = ProvideCellStyleTv(
    BenefitTableRowDefaults.firstPlanContentColor,
    BenefitTableRowDefaults.firstPlanTextStyle,
    content,
)

@Composable
private fun SecondPlanCellTv(content: @Composable () -> Unit) = ProvideCellStyleTv(
    BenefitTableRowDefaults.secondPlanContentColor,
    BenefitTableRowDefaults.secondPlanTextStyle,
    content,
)

@Composable
private fun ProvideCellStyleTv(
    color: Color,
    textStyle: TextStyle,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalContentColor provides color,
        LocalTextStyle provides textStyle,
        content = content
    )
}
