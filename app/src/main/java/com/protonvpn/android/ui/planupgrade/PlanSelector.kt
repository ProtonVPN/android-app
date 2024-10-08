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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun PlanSelector(
    plans: List<PlanModel>,
    selectedPlan: PlanModel,
    onPlanSelected: (PlanModel) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val selectorContentDescription = stringResource(R.string.upgrade_plan_selection_content_description)
    Row(
        modifier = modifier
            .selectableGroup()
            .semantics { contentDescription = selectorContentDescription }
            .background(ProtonTheme.colors.backgroundNorm.copy(alpha = 0.4f), CircleShape)
            .padding(4.dp)
            .height(IntrinsicSize.Max),
        verticalAlignment = Alignment.CenterVertically,

    ) {
        ProvideTextStyle(ProtonTheme.typography.body2Medium) {
            plans.forEach { plan ->
                PlanSelectorButton(
                    text = plan.displayName,
                    onClick = { onPlanSelected(plan) },
                    selected = plan == selectedPlan,
                    enabled = enabled,
                    modifier = Modifier.fillMaxHeight(),
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
        color = surfaceColor,
        contentColor = contentColor,
        shape = CircleShape,
        modifier = modifier
            .clip(CircleShape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
    ) {
        Box {
            Text(
                text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp).align(Alignment.Center)
            )
        }
    }
}

@Preview
@Composable
fun PlanSelectorPreview() {
    ProtonVpnPreview(
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
