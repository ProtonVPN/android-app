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

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.PlaceholderRect
import com.protonvpn.android.base.ui.optional
import com.protonvpn.android.base.ui.vpnGreen
import com.protonvpn.android.tv.buttons.TvTextButton
import com.protonvpn.android.tv.settings.ProtonTvFocusableSurface
import com.protonvpn.android.ui.planupgrade.CommonUpgradeDialogViewModel
import com.protonvpn.android.ui.planupgrade.PaymentPanelState
import com.protonvpn.android.ui.planupgrade.PricingCycleInfo
import com.protonvpn.android.ui.planupgrade.WithMinHeightOf
import com.protonvpn.android.ui.planupgrade.renewInfoText
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonDimens
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionStrongUnspecified
import me.proton.core.compose.theme.captionWeak
import me.proton.core.compose.theme.defaultSmallWeak

@Composable
fun PaymentPanelTv(
    viewState: PaymentPanelState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val upgradeState = viewState.upgradeState
    if (upgradeState == CommonUpgradeDialogViewModel.State.Initializing)
        return

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(key1 = upgradeState::class) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (upgradeState) {
            CommonUpgradeDialogViewModel.State.Initializing -> Unit
            is CommonUpgradeDialogViewModel.State.LoadingPlans -> {
                PlanSelectionColumn(
                    renewInfoText = null,
                    showSelectPlans = upgradeState.expectedCycleCount > 1
                ) {
                    repeat(upgradeState.expectedCycleCount) {
                        PlanCycleInfoSelectorPlaceholder()
                    }
                }
            }
            is CommonUpgradeDialogViewModel.State.PurchaseReady -> {
                // TODO: disable when inProgress
                val cycles = upgradeState.selectedPlan.cycles
                val activity = LocalActivity.current
                val selectedCycleInfo = remember(cycles, viewState.selectedCycle) {
                    cycles.firstOrNull { it.cycle == viewState.selectedCycle }
                }
                PlanSelectionColumn(
                    renewInfoText = selectedCycleInfo?.let { renewInfoText(selectedCycleInfo) },
                    showSelectPlans = cycles.size > 1
                ) {
                    cycles.forEachIndexed { index, cycle ->
                        PlanCycleInfoSelector(
                            onClick = { if (activity != null) { viewState.onPayClicked(activity) } },
                            onFocused = { viewState.onCycleSelected(cycle.cycle) },
                            cycle = cycle,
                            modifier = Modifier
                                .optional({ index == 0 }, Modifier.focusRequester(focusRequester))
                        )
                    }
                }
            }

            is CommonUpgradeDialogViewModel.State.LoadError -> {
                TvTextButton(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .focusRequester(focusRequester),
                    text = stringResource(id = R.string.try_again),
                    onClick = viewState.onErrorButtonClicked,
                )
            }

            is CommonUpgradeDialogViewModel.State.PlansFallback,
            is CommonUpgradeDialogViewModel.State.UpgradeDisabled -> {
                Text(
                    stringResource(R.string.upsell_tv_fallback_description),
                    color = ProtonTheme.colors.textWeak,
                    style = ProtonTheme.typography.body1Regular,
                )
                VerticalSpacer(height = 16.dp)
                TvTextButton(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .focusRequester(focusRequester),
                    text = stringResource(id = R.string.back),
                    onClick = onClose
                )
            }

            is CommonUpgradeDialogViewModel.State.PurchaseSuccess -> Unit
        }
    }
}

@Composable
private fun PlanSelectionColumn(
    showSelectPlans: Boolean,
    renewInfoText: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.width(500.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showSelectPlans) {
            Text(
                modifier = Modifier.padding(bottom = 4.dp),
                text = stringResource(R.string.payment_select_your_plan),
                style = ProtonTheme.typography.defaultSmallWeak
            )
        }

        content()

        RenewInfoText(
            renewInfoText ?: "", // Empty string to consume space.
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun PlanCycleInfoSelector(
    onClick: () -> Unit,
    onFocused: () -> Unit,
    cycle: CommonUpgradeDialogViewModel.CycleViewInfo,
    modifier: Modifier = Modifier,
) {
    ProtonTvFocusableSurface(
        onClick = onClick,
        onFocused = onFocused,
        shape = ProtonTheme.shapes.large,
        focusedColor = { ProtonTheme.colors.backgroundNorm },
        unfocusedBorder = true,
        modifier = modifier,
    ) {
        WithMinHeightOf(
            minHeightContent = {
                PricingCycleInfo("123", R.string.payment_price_per_year, "123") { text, style ->
                    Text(text, style = style)
                }
            },
            modifier = Modifier.height(IntrinsicSize.Max)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            PlanCycleInfo(cycle, Modifier.fillMaxHeight())
        }
    }
}

@Composable
private fun PlanCycleInfo(
    cycle: CommonUpgradeDialogViewModel.CycleViewInfo,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(id = cycle.cycleLabelResId),
                style = ProtonTheme.typography.body1Regular,
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
        }
        with(cycle) {
            PricingCycleInfo(
                priceInfo.formattedPrice,
                perCycleResId,
                priceInfo.formattedPerMonthPrice,
            ) { text, style -> Text(text, style = style) }
        }
    }
}

@Composable
private fun PlanCycleInfoSelectorPlaceholder(
    modifier: Modifier = Modifier,
) {
    WithMinHeightOf(
        minHeightContent = {
            PricingCycleInfo("123", R.string.payment_price_per_year, "123") { text, style ->
                Text(text, style = style)
            }
        },
        modifier = modifier.height(IntrinsicSize.Max)
            .border(2.dp, ProtonTheme.colors.separatorNorm, ProtonTheme.shapes.large)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
        ) {
            PlaceholderRect(width = 120.dp)
            Spacer(modifier = Modifier.weight(1f))
            PlaceholderRect()
        }
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
