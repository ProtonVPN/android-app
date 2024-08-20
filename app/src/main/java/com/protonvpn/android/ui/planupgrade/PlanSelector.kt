/*
 * Copyright (c) 2024. Proton AG
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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.theme.LightAndDarkPreview
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun PlanSelector(
    plans: List<PlanModel>,
    selectedPlan: PlanModel,
    onPlanSelected: (PlanModel) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .selectableGroup()
            .background(ProtonTheme.colors.backgroundNorm.copy(alpha = 0.4f), CircleShape)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProvideTextStyle(ProtonTheme.typography.body2Medium) {
            plans.forEach { plan ->
                PlanSelectorButton(
                    text = plan.displayName,
                    onClick = { onPlanSelected(plan) },
                    selected = plan == selectedPlan,
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun PlanSelectorButton(
    text: String,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val surfaceColor = if (selected) ProtonTheme.colors.interactionStrongNorm else Color.Transparent
    val contentColor = if (selected) ProtonTheme.colors.textInverted else ProtonTheme.colors.textNorm
    Surface(
        onClick = onClick,
        selected = selected,
        enabled = enabled,
        color = surfaceColor,
        contentColor = contentColor,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Text(
            text,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .padding(horizontal = 15.dp, vertical = 10.dp)
        )
    }
}

@Preview
@Composable
fun PlanSelectorPreview() {
    LightAndDarkPreview(
        surfaceColor = { ProtonTheme.colors.shade60 }
    ) {
        val plans = listOf(
            PlanModel("VPN Plus", "vpn", emptyList()),
            PlanModel("Proton Unlimited", "bundle", emptyList())
        )
        PlanSelector(
            plans = plans,
            selectedPlan = plans.first(),
            onPlanSelected = {},
        )
    }
}
