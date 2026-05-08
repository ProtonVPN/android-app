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
import androidx.annotation.VisibleForTesting
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.PlaceholderRect
import com.protonvpn.android.base.ui.ProtonSolidButton
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.vpnGreen
import com.protonvpn.android.utils.Constants
import me.proton.core.compose.theme.ProtonDimens
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionStrongUnspecified
import me.proton.core.compose.theme.captionWeak
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultSmallWeak
import me.proton.core.compose.theme.defaultStrongNorm
import me.proton.core.plan.presentation.entity.PlanCycle

@Composable
fun PaymentPanel(
    viewState: CommonUpgradeDialogViewModel.State,
    selectedCycle: PlanCycle?,
    onPayClicked: () -> Unit,
    onStartFallback: () -> Unit,
    onErrorButtonClicked: () -> Unit,
    onCloseButtonClicked: () -> Unit,
    onCycleSelected: (PlanCycle) -> Unit,
) {
    if (viewState == CommonUpgradeDialogViewModel.State.Initializing)
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
            val renewInfoModifier = Modifier
                .padding(top = 4.dp)
            when (viewState) {
                is CommonUpgradeDialogViewModel.State.Initializing -> {}
                is CommonUpgradeDialogViewModel.State.LoadingPlans -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (viewState.expectedCycleCount > 1) {
                            selectPlanText()
                        }
                        repeat(viewState.expectedCycleCount) { CycleSelectionPlaceholderRow() }
                        RenewInfoText(
                            "",
                            modifier = renewInfoModifier.alpha(0f)
                        )
                    }
                }
                is CommonUpgradeDialogViewModel.State.PurchaseReady -> {
                    val cycles = viewState.selectedPlan.cycles
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (cycles.size > 1) {
                            selectPlanText()
                        }
                        cycles.forEach { cycle ->
                            CycleComposable(cycle, cycle.cycle == selectedCycle, onCycleSelected)
                        }

                        val selectedCycleInfo = cycles.firstOrNull { it.cycle == selectedCycle }
                        if (selectedCycleInfo != null) {
                            RenewInfo(
                                selectedCycleInfo = selectedCycleInfo,
                                modifier = renewInfoModifier
                                    .align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
                is CommonUpgradeDialogViewModel.State.LoadError,
                is CommonUpgradeDialogViewModel.State.UpgradeDisabled,
                is CommonUpgradeDialogViewModel.State.PlansFallback,
                is CommonUpgradeDialogViewModel.State.PurchaseSuccess-> Unit
            }
        }

        val onClick: () -> Unit = when(viewState) {
            is CommonUpgradeDialogViewModel.State.Initializing,
            is CommonUpgradeDialogViewModel.State.LoadingPlans,
            is CommonUpgradeDialogViewModel.State.PurchaseSuccess -> { {} }

            is CommonUpgradeDialogViewModel.State.PurchaseReady -> onPayClicked
            is CommonUpgradeDialogViewModel.State.PlansFallback -> onStartFallback
            is CommonUpgradeDialogViewModel.State.LoadError -> onErrorButtonClicked
            is CommonUpgradeDialogViewModel.State.UpgradeDisabled -> onCloseButtonClicked
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
                is CommonUpgradeDialogViewModel.State.PurchaseSuccess,
                is CommonUpgradeDialogViewModel.State.Initializing -> {
                    /* empty button */
                }
                is CommonUpgradeDialogViewModel.State.PlansFallback ->
                    Text(stringResource(R.string.payment_button_get_plan, Constants.CURRENT_PLUS_PLAN_LABEL))
                is CommonUpgradeDialogViewModel.State.LoadingPlans -> {
                    val buttonText = viewState.buttonLabelOverride
                        ?: stringResource(R.string.payment_button_get_plan, Constants.CURRENT_PLUS_PLAN_LABEL)
                    Text(buttonText)
                }
                is CommonUpgradeDialogViewModel.State.PurchaseReady -> {
                    val buttonText = viewState.buttonLabelOverride
                        ?: stringResource(R.string.payment_button_get_plan, viewState.selectedPlan.displayName)
                    Text(buttonText)
                }
                is CommonUpgradeDialogViewModel.State.LoadError ->
                    Text(stringResource(R.string.try_again))
                is CommonUpgradeDialogViewModel.State.UpgradeDisabled ->
                    Text(stringResource(R.string.close))
            }
        }
    }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
@Composable
fun RenewInfo(
    selectedCycleInfo: CommonUpgradeDialogViewModel.CycleViewInfo,
    modifier: Modifier = Modifier
) {
    val priceInfo = selectedCycleInfo.priceInfo
    val price = priceInfo.formattedPrice
    val renewPrice = priceInfo.formattedRenewPrice
    val renewInfoText = when (selectedCycleInfo.cycle) {
        PlanCycle.MONTHLY -> when {
            priceInfo.hasIntroPrice ->
                stringResource(R.string.payment_welcome_price_message_monthly, renewPrice)
            else ->
                stringResource(R.string.payment_auto_renew_message_monthly, price)
        }
        PlanCycle.YEARLY -> when {
            priceInfo.hasIntroPrice ->
                stringResource(R.string.payment_welcome_price_message_annual, renewPrice)
            else ->
                stringResource(R.string.payment_auto_renew_message_annual, price)
        }
        else -> return
    }
    RenewInfoText(renewInfoText, modifier)
}

@Composable
private fun RenewInfoText(
    renewInfoText: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = renewInfoText,
        style = ProtonTheme.typography.captionWeak,
        modifier = modifier
    )
}

@Composable
private fun CycleComposable(
    cycle: CommonUpgradeDialogViewModel.CycleViewInfo,
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
                style = ProtonTheme.typography.captionStrongUnspecified,
                color = ProtonTheme.colors.textInverted,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        with(cycle) {
            PricingCycleInfo(priceInfo.formattedPrice, perCycleResId, priceInfo.formattedPerMonthPrice) { text, style -> Text(text, style = style) }
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
            PricingCycleInfo("123", R.string.payment_price_per_year, "123") { text, style -> Text(text, style = style) }
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
    @StringRes perCycleResId: Int?,
    formattedPerMonthPrice: String?,
    modifier: Modifier = Modifier,
    text: @Composable (AnnotatedString, TextStyle) -> Unit,
) {
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        text(
            getPriceAndCycleString(formattedPrice, perCycleResId),
            ProtonTheme.typography.captionWeak
        )
        if (formattedPerMonthPrice != null) {
            val perMonth = stringResource(R.string.payment_price_per_month)
            text(
                AnnotatedString(stringResource(id = R.string.payment_price_with_period, formattedPerMonthPrice, perMonth)),
                ProtonTheme.typography.captionWeak
            )
        }
    }
}

@Composable
private fun getPriceAndCycleString(formattedPrice: String, @StringRes cycleResId: Int?): AnnotatedString {
    val text = if (cycleResId != null) {
        stringResource(id = R.string.payment_price_with_period, formattedPrice, stringResource(cycleResId))
    } else {
        formattedPrice
    }
    val priceIndex = text.indexOf(formattedPrice)
    return AnnotatedString.Builder(text).apply {
        addStyle(
            ProtonTheme.typography.defaultStrongNorm.toSpanStyle(),
            priceIndex,
            priceIndex + formattedPrice.length
        )
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

@ProtonVpnPreview
@Composable
private fun PreviewPlan() {
    ProtonVpnPreview {
        val cycles = listOf(
            CommonUpgradeDialogViewModel.CycleViewInfo(
                PlanCycle.YEARLY,
                R.string.payment_price_per_year,
                R.string.payment_price_cycle_year_label,
                CommonUpgradeDialogViewModel.PriceInfo(
                    "$120.00",
                    formattedPerMonthPrice = "$10.00",
                    savePercent = -37,
                    hasIntroPrice = true
                )
            ),
            CommonUpgradeDialogViewModel.CycleViewInfo(
                PlanCycle.MONTHLY,
                R.string.payment_price_per_month,
                R.string.payment_price_cycle_month_label,
                CommonUpgradeDialogViewModel.PriceInfo("$15.99", hasIntroPrice = false)
            ),
        )
        val plan = PlanModel("VPN Plus", "vpn2022", cycles)
        PaymentPanel(
            viewState = CommonUpgradeDialogViewModel.State.PurchaseReady(
                allPlans = listOf(plan),
                selectedPlan = plan,
                inProgress = false,
                buttonLabelOverride = null,
            ),
            selectedCycle = PlanCycle.YEARLY,
            {}, {}, {}, {}, {}
        )
    }
}

@ProtonVpnPreview
@Composable
private fun PreviewPlanWithWelcomePrice() {
    ProtonVpnPreview {
        val cycles = listOf(
            CommonUpgradeDialogViewModel.CycleViewInfo(
                PlanCycle.YEARLY,
                null,
                R.string.payment_price_cycle_year_label,
                CommonUpgradeDialogViewModel.PriceInfo(
                    "$120.00",
                    formattedPerMonthPrice = null,
                    savePercent = -37,
                    formattedRenewPrice = "$150",
                    hasIntroPrice = true
                )
            ),
            CommonUpgradeDialogViewModel.CycleViewInfo(
                PlanCycle.MONTHLY,
                null,
                R.string.payment_price_cycle_month_label,
                CommonUpgradeDialogViewModel.PriceInfo("$15.99", hasIntroPrice = false)
            ),
        )
        val plan = PlanModel("VPN Plus", "vpn2022", cycles)
        PaymentPanel(
            viewState = CommonUpgradeDialogViewModel.State.PurchaseReady(
                allPlans = listOf(plan),
                selectedPlan = plan,
                inProgress = false,
                buttonLabelOverride = null,
            ),
            selectedCycle = PlanCycle.YEARLY,
            {}, {}, {}, {}, {}
        )
    }
}

@ProtonVpnPreview
@Composable
private fun PreviewLoadingPlans() {
    ProtonVpnPreview {
        PaymentPanel(viewState = CommonUpgradeDialogViewModel.State.LoadingPlans(2, null), null, {}, {}, {}, {}, {})
    }
}
