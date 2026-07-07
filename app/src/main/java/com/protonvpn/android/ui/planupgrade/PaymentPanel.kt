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

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
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

@Immutable
data class PaymentPanelState(
    val upgradeState: UpgradeDialogViewModel.State,
    val selectedCycle: PlanCycle?,
    val onPayClicked: (Activity) -> Unit,
    val onErrorButtonClicked: () -> Unit,
    val onCycleSelected: (PlanCycle) -> Unit,
)

@Composable
fun PaymentPanel(
    viewState: PaymentPanelState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val upgradeState = viewState.upgradeState
    if (upgradeState == UpgradeDialogViewModel.State.Initializing)
        return

    Column(
        modifier = modifier
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
            when (upgradeState) {
                is UpgradeDialogViewModel.State.Initializing -> {}
                is UpgradeDialogViewModel.State.LoadingPlans -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (upgradeState.expectedCycleCount > 1) {
                            selectPlanText()
                        }
                        repeat(upgradeState.expectedCycleCount) { CycleSelectionPlaceholderRow() }
                        RenewInfoText(
                            "",
                            modifier = renewInfoModifier.alpha(0f)
                        )
                    }
                }
                is UpgradeDialogViewModel.State.PurchaseReady -> {
                    val cycles = upgradeState.selectedPlan.cycles
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (cycles.size > 1) {
                            selectPlanText()
                        }
                        cycles.forEach { cycle ->
                            CycleComposable(
                                cycle,
                                cycle.cycle == viewState.selectedCycle,
                                viewState.onCycleSelected
                            )
                        }

                        val selectedCycleInfo = cycles.firstOrNull { it.cycle == viewState.selectedCycle }
                        if (selectedCycleInfo != null) {
                            RenewInfo(
                                selectedCycleInfo = selectedCycleInfo,
                                modifier = renewInfoModifier
                                    .align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
                is UpgradeDialogViewModel.State.LoadError,
                is UpgradeDialogViewModel.State.UpgradeDisabled,
                is UpgradeDialogViewModel.State.PurchaseSuccess-> Unit
            }
        }

        val activity = LocalActivity.current
        val onClick: () -> Unit = when(upgradeState) {
            is UpgradeDialogViewModel.State.Initializing,
            is UpgradeDialogViewModel.State.LoadingPlans,
            is UpgradeDialogViewModel.State.PurchaseSuccess -> { {} }

            is UpgradeDialogViewModel.State.PurchaseReady -> {
                if (activity != null) { { viewState.onPayClicked(activity) } } else { {} }
            }
            is UpgradeDialogViewModel.State.LoadError -> viewState.onErrorButtonClicked
            is UpgradeDialogViewModel.State.UpgradeDisabled -> onClose
        }
        ProtonSolidButton(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(top = 16.dp, bottom = 8.dp),
            contained = false,
            loading = upgradeState.inProgress,
            onClick = onClick
        ) {
            when (upgradeState) {
                is UpgradeDialogViewModel.State.PurchaseSuccess,
                is UpgradeDialogViewModel.State.Initializing -> {
                    /* empty button */
                }
                is UpgradeDialogViewModel.State.LoadingPlans -> {
                    val buttonText = viewState.upgradeState.buttonLabelOverride
                        ?: stringResource(R.string.payment_button_get_plan, Constants.CURRENT_PLUS_PLAN_LABEL)
                    Text(buttonText)
                }
                is UpgradeDialogViewModel.State.PurchaseReady -> {
                    val buttonText = viewState.upgradeState.buttonLabelOverride
                        ?: stringResource(R.string.payment_button_get_plan, upgradeState.selectedPlan.displayName)
                    Text(buttonText)
                }
                is UpgradeDialogViewModel.State.LoadError ->
                    Text(stringResource(R.string.try_again))
                is UpgradeDialogViewModel.State.UpgradeDisabled ->
                    Text(stringResource(R.string.close))
            }
        }
    }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
@Composable
fun RenewInfo(
    selectedCycleInfo: UpgradeDialogViewModel.CycleViewInfo,
    modifier: Modifier = Modifier
) {
    val renewInfoText = renewInfoText(selectedCycleInfo)
    if (renewInfoText != null) {
        RenewInfoText(renewInfoText, modifier)
    }
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
    cycle: UpgradeDialogViewModel.CycleViewInfo,
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

@ProtonVpnPreview
@Composable
private fun PreviewPlan() {
    ProtonVpnPreview {
        val cycles = listOf(
            UpgradeDialogViewModel.CycleViewInfo(
                productId = "ProductId",
                offerToken = "OfferToken",
                cycle = PlanCycle.YEARLY,
                perCycleResId = R.string.payment_price_per_year,
                cycleLabelResId = R.string.payment_price_cycle_year_label,
                priceInfo = UpgradeDialogViewModel.PriceInfo(
                    "$120.00",
                    formattedPerMonthPrice = "$10.00",
                    savePercent = -37,
                    hasIntroPrice = true
                )
            ),
            UpgradeDialogViewModel.CycleViewInfo(
                productId = "ProductId",
                offerToken = "OfferToken",
                cycle = PlanCycle.MONTHLY,
                perCycleResId = R.string.payment_price_per_month,
                cycleLabelResId = R.string.payment_price_cycle_month_label,
                priceInfo = UpgradeDialogViewModel.PriceInfo("$15.99", hasIntroPrice = false)
            ),
        )
        val plan = PlanModel("VPN Plus", "vpn2022", "USD", cycles, PlanCycle.YEARLY)
        PaymentPanel(
            viewState = PaymentPanelState(
                UpgradeDialogViewModel.State.PurchaseReady(
                    allPlans = listOf(plan),
                    selectedPlan = plan,
                    inProgress = false,
                    buttonLabelOverride = null,
                ),
                selectedCycle = PlanCycle.YEARLY,
                {}, {}, {},
            ),
            onClose = {}
        )
    }
}

@ProtonVpnPreview
@Composable
private fun PreviewPlanWithWelcomePrice() {
    ProtonVpnPreview {
        val cycles = listOf(
            UpgradeDialogViewModel.CycleViewInfo(
                productId = "ProductId",
                offerToken = "OfferToken",
                cycle = PlanCycle.YEARLY,
                perCycleResId = null,
                cycleLabelResId = R.string.payment_price_cycle_year_label,
                priceInfo = UpgradeDialogViewModel.PriceInfo(
                    "$120.00",
                    formattedPerMonthPrice = null,
                    savePercent = -37,
                    formattedRenewPrice = "$150",
                    hasIntroPrice = true
                )
            ),
            UpgradeDialogViewModel.CycleViewInfo(
                productId = "ProductId",
                offerToken = "OfferToken",
                cycle = PlanCycle.MONTHLY,
                perCycleResId = null,
                cycleLabelResId = R.string.payment_price_cycle_month_label,
                priceInfo = UpgradeDialogViewModel.PriceInfo("$15.99", hasIntroPrice = false)
            ),
        )
        val plan = PlanModel("VPN Plus", "vpn2022", "USD", cycles, PlanCycle.YEARLY)
        PaymentPanel(
            viewState = PaymentPanelState(
                UpgradeDialogViewModel.State.PurchaseReady(
                    allPlans = listOf(plan),
                    selectedPlan = plan,
                    inProgress = false,
                    buttonLabelOverride = null,
                ),
                selectedCycle = PlanCycle.YEARLY,
                {}, {}, {},
            ),
            onClose = {}
        )
    }
}

@ProtonVpnPreview
@Composable
private fun PreviewLoadingPlans() {
    ProtonVpnPreview {
        PaymentPanel(
            viewState = PaymentPanelState(
                UpgradeDialogViewModel.State.LoadingPlans(2, null),
                null,
                {}, {}, {},
            ),
            onClose = {},
        )
    }
}
