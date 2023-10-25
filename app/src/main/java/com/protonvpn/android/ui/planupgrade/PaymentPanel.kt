/*
 * Copyright (c) 2023. Proton AG
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

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.PlaceholderRect
import com.protonvpn.android.base.ui.ProtonSolidButton
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.redesign.base.ui.vpnGreen
import me.proton.core.compose.theme.ProtonDimens
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionStrongUnspecified
import me.proton.core.compose.theme.captionWeak
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultSmallWeak
import me.proton.core.compose.theme.defaultStrongNorm
import me.proton.core.plan.presentation.entity.PlanCycle

sealed class ViewState(val inProgress: Boolean) {
    object Initializing : ViewState(false)
    object UpgradeDisabled : ViewState(false)
    object LoadingPlans : ViewState(true)
    data class CycleViewInfo(
        val cycle: PlanCycle,
        @StringRes val perCycleResId: Int,
        @StringRes val cycleLabelResId: Int,
        val priceInfo: CommonUpgradeDialogViewModel.PriceInfo
    )
    class PlanReady(
        val planName: String,
        val cycles: List<CycleViewInfo>,
        inProgress: Boolean
    ) : ViewState(inProgress)
    object FallbackFlowReady : ViewState(false)
    object Error : ViewState(false)
}

@Composable
fun PaymentPanel(
    viewState: ViewState,
    selectedCycle: PlanCycle?,
    onPayClicked: () -> Unit,
    onStartFallback: () -> Unit,
    onErrorButtonClicked: () -> Unit,
    onCloseButtonClicked: () -> Unit,
    onCycleSelected: (PlanCycle) -> Unit,
) {
    if (viewState == ViewState.Initializing)
        return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        val selectPlanText = @Composable {
            Text(
                modifier = Modifier.padding(bottom = 4.dp),
                text = stringResource(R.string.payment_select_your_plan),
                style = ProtonTheme.typography.defaultSmallWeak
            )
        }

        Row(
            modifier = Modifier
                .padding(top = 16.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (viewState) {
                is ViewState.Initializing -> {}
                is ViewState.LoadingPlans -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        selectPlanText()
                        repeat(2) { CycleSelectionPlaceholderRow() }
                    }
                }
                is ViewState.PlanReady -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (viewState.cycles.size > 1) {
                            selectPlanText()
                        }
                        viewState.cycles.forEach { cycle ->
                            CycleComposable(cycle, cycle.cycle == selectedCycle, onCycleSelected)
                        }
                    }
                }
                is ViewState.Error,
                is ViewState.UpgradeDisabled,
                is ViewState.FallbackFlowReady -> Unit
            }
        }

        val onClick: () -> Unit = when(viewState) {
            ViewState.Initializing, ViewState.LoadingPlans -> { {} }
            is ViewState.PlanReady -> onPayClicked
            ViewState.FallbackFlowReady -> onStartFallback
            ViewState.Error -> onErrorButtonClicked
            ViewState.UpgradeDisabled -> onCloseButtonClicked
        }
        ProtonSolidButton(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(top = 16.dp, bottom = 8.dp),
            contained = false,
            loading = viewState.inProgress,
            onClick = onClick
        ) {
            when (viewState) {
                is ViewState.Initializing,
                is ViewState.LoadingPlans,
                is ViewState.FallbackFlowReady,
                is ViewState.PlanReady ->
                    Text(stringResource(R.string.payment_button_get_vpn_plus))
                is ViewState.Error ->
                    Text(stringResource(R.string.try_again))
                is ViewState.UpgradeDisabled ->
                    Text(stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun CycleComposable(
    cycle: ViewState.CycleViewInfo,
    isSelected: Boolean,
    onCycleSelected: (PlanCycle) -> Unit,
    modifier: Modifier = Modifier
) {
    CycleSelectionRow(
        isSelected,
        onSelected = { onCycleSelected(cycle.cycle) },
        modifier
    ) {
        Text(
            stringResource(id = cycle.cycleLabelResId),
            style = ProtonTheme.typography.defaultNorm
        )
        cycle.priceInfo.savePercent?.let {
            Text(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .background(
                        ProtonTheme.colors.vpnGreen,
                        RoundedCornerShape(size = ProtonDimens.DefaultCornerRadius)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                text = "$it%",
                style = ProtonTheme.typography.captionStrongUnspecified.copy(color = Color.Black)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        with(cycle) {
            PricingCycleInfo(priceInfo.formattedPrice, perCycleResId, priceInfo.formattedPerMonthPrice)
        }
    }
}

@Composable
private inline fun CycleSelectionPlaceholderRow(
    modifier: Modifier = Modifier
) {
    CycleSelectionRow(isSelected = false, onSelected = null, modifier = modifier) {
        PlaceholderRect(width = 120.dp)
        Spacer(modifier = Modifier.weight(1f))
        PlaceholderRect()
    }
}


@Composable
private fun CycleSelectionRow(
    isSelected: Boolean,
    onSelected: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(size = ProtonDimens.LargeCornerRadius)
    val borderModifier = if (isSelected) {
        Modifier
            .border(2.dp, ProtonTheme.colors.textNorm, shape)
            .background(ProtonTheme.colors.backgroundSecondary, shape)
    } else {
        Modifier.border(1.dp, ProtonTheme.colors.separatorNorm, shape)
    }
    val clickModifier = if (onSelected != null) Modifier.clickable(onClick = onSelected) else Modifier

    WithMinHeightOf(
        minHeightContent = {
            PricingCycleInfo("123", R.string.payment_price_per_year, "123")
        },
        modifier = modifier
            .then(borderModifier)
            .clip(shape)
            .then(clickModifier)
            .padding(vertical = 12.dp, horizontal = 16.dp)
            .height(IntrinsicSize.Max),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxHeight(),
            content = content
        )
    }
}

@Composable
private fun PricingCycleInfo(
    formattedPrice: String,
    @StringRes perCycleResId: Int,
    formattedPerMonthPrice: String?,
    modifier: Modifier = Modifier
) {
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        Text(
            getPriceAndCycleString(formattedPrice, perCycleResId),
            style = ProtonTheme.typography.captionWeak
        )
        if (formattedPerMonthPrice != null) {
            val perMonth = stringResource(R.string.payment_price_per_month)
            Text(
                stringResource(id = R.string.payment_price_with_period, formattedPerMonthPrice, perMonth),
                style = ProtonTheme.typography.captionWeak
            )
        }
    }
}


@Composable
private fun getPriceAndCycleString(formattedPrice: String, @StringRes cycleResId: Int): AnnotatedString {
    val text = stringResource(id = R.string.payment_price_with_period, formattedPrice, stringResource(cycleResId))
    val priceIndex = text.indexOf(formattedPrice)
    return AnnotatedString.Builder(text).apply {
        addStyle(ProtonTheme.typography.defaultStrongNorm .toSpanStyle(), priceIndex, priceIndex + formattedPrice.length)
    }.toAnnotatedString()
}

@Composable
private fun WithMinHeightOf(
    minHeightContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier) {
        Box(modifier = Modifier.alpha(0f)) {
            minHeightContent()
        }
        content()
    }
}

@Preview(showBackground = true, backgroundColor = 0xff000000)
@Composable
private fun PreviewPlan() {
    VpnTheme(isDark = true) {
        PaymentPanel(
            viewState = ViewState.PlanReady(
                "VPN Plus",
                listOf(
                    ViewState.CycleViewInfo(
                        PlanCycle.YEARLY,
                        R.string.payment_price_per_year,
                        R.string.payment_price_cycle_year_label,
                        CommonUpgradeDialogViewModel.PriceInfo("$120.00", formattedPerMonthPrice = "$10.00", savePercent = -37)
                    ),
                    ViewState.CycleViewInfo(
                        PlanCycle.MONTHLY,
                        R.string.payment_price_per_month,
                        R.string.payment_price_cycle_month_label,
                        CommonUpgradeDialogViewModel.PriceInfo("$15.99")
                    ),
                ),
                false),
            selectedCycle = PlanCycle.YEARLY,
            {}, {}, {}, {}, {}
        )
    }
}

@Preview
@Composable
private fun PreviewLoadingPlans() {
    VpnTheme(isDark = false) {
        PaymentPanel(viewState = ViewState.LoadingPlans, null, {}, {}, {}, {}, {})
    }
}
