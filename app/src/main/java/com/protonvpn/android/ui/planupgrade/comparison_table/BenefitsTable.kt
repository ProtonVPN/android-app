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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.vpnGreen
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

private const val BENEFIT_TEXT_COLUMN_WEIGHT = 17f
private const val BENEFIT_FIRST_COLUMN_WEIGHT = 7f
private const val BENEFIT_SECOND_COLUMN_WEIGHT = 9f

object BenefitTableRowDefaults {
    val ShapeTop =  RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    val ShapeBottom =  RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
}

@Composable
fun BenefitTableFreePlusHeader(
    modifier: Modifier = Modifier
) {
    BenefitTableRow(
        null,
        { Text(stringResource(R.string.upsell_panel_table_header_plan_free)) },
        { PlanPlusBadge() },
        modifier = modifier,
        bottomSeparator = false,
        secondPlanBackgroundShape = BenefitTableRowDefaults.ShapeTop,
        itemVerticalPadding = 8.dp,
    )
}

@Composable
fun BenefitTableRowNoYes(
    benefitText: String?,
    modifier: Modifier = Modifier,
    bottomSeparator: Boolean = true,
    secondPlanBackgroundShape: Shape = RectangleShape,
) {
    BenefitTableRow(
        benefitText = benefitText,
        firstPlanContent = {
            Icon(
                painterResource(CoreR.drawable.ic_proton_cross),
                contentDescription = null
            )
        },
        secondPlanContent = {
            Icon(
                painterResource(CoreR.drawable.ic_proton_checkmark_circle_filled),
                contentDescription = null
            )
        },
        modifier = modifier,
        bottomSeparator = bottomSeparator,
        secondPlanBackgroundShape = secondPlanBackgroundShape,
    )
}

@Composable
fun BenefitTableRow(
    benefitText: String?,
    firstPlanContent: @Composable () -> Unit,
    secondPlanContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    bottomSeparator: Boolean = true,
    secondPlanBackgroundShape: Shape = RectangleShape,
    itemVerticalPadding: Dp = 12.dp
    // TODO: content description
) {
    val itemVerticalPadding = PaddingValues(vertical = itemVerticalPadding)
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(IntrinsicSize.Max)
        ) {
            if (benefitText != null) {
                Text(
                    benefitText,
                    style = ProtonTheme.typography.body2Medium,
                    modifier = Modifier
                        .weight(BENEFIT_TEXT_COLUMN_WEIGHT)
                        .padding(itemVerticalPadding)
                )
            } else {
                Spacer(Modifier.weight(BENEFIT_TEXT_COLUMN_WEIGHT))
            }
            Box(
                modifier = Modifier
                    .weight(BENEFIT_FIRST_COLUMN_WEIGHT)
                    .padding(itemVerticalPadding)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides ProtonTheme.colors.textWeak,
                    LocalTextStyle provides ProtonTheme.typography.body2Regular,
                    content = firstPlanContent
                )
            }
            Box(
                modifier = Modifier
                    .weight(BENEFIT_SECOND_COLUMN_WEIGHT)
                    .fillMaxHeight()
                    .background(ProtonTheme.colors.vpnGreen.copy(alpha = 0.2f), secondPlanBackgroundShape)
                    .padding(itemVerticalPadding)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides ProtonTheme.colors.vpnGreen,
                    LocalTextStyle provides ProtonTheme.typography.body2Medium,
                    content = secondPlanContent
                )
            }
        }
        if (bottomSeparator) {
            HorizontalDivider(thickness = Dp.Hairline, color = ProtonTheme.colors.shade100.copy(alpha = 0.1f))
        }
    }
}