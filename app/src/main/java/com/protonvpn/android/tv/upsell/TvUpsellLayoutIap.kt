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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnTvPreview
import com.protonvpn.android.base.ui.upsellBackground
import com.protonvpn.android.base.ui.upsellGradientEnd
import com.protonvpn.android.base.ui.upsellGradientStart
import com.protonvpn.android.ui.planupgrade.PaymentCycle
import com.protonvpn.android.ui.planupgrade.PaymentPanelState
import com.protonvpn.android.ui.planupgrade.PaymentRecurrence
import com.protonvpn.android.ui.planupgrade.PlanCycle
import com.protonvpn.android.ui.planupgrade.PlanModel
import com.protonvpn.android.ui.planupgrade.UpgradeDialogViewModel
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.compose.tv.theme.ProtonThemeTv

@Composable
fun TvUpsellLayoutIap(
    viewState: TvUpsellViewModel.ViewState,
    paymentPanelState: PaymentPanelState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .background(color = ProtonTheme.colors.upsellBackground)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ProtonTheme.colors.upsellGradientStart,
                        ProtonTheme.colors.upsellGradientEnd,
                        Color.Transparent,
                        Color.Transparent,
                    )
                ),
                alpha = 0.7f,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(fraction = 0.6f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            Image(
                painter = painterResource(id = viewState.imageResId),
                contentDescription = null,
            )

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = viewState.titleResId),
                textAlign = TextAlign.Center,
                style = ProtonTheme.typography.hero,
            )

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = AnnotatedString.fromHtml(
                    htmlString = stringResource(
                        id = viewState.descriptionResId,
                        *viewState.descriptionArgs(context),
                    )
                ),
                textAlign = TextAlign.Center,
                color = ProtonTheme.colors.textWeak,
                style = ProtonTheme.typography.body1Regular,
            )

            PaymentPanelTv(
                viewState = paymentPanelState,
                modifier = Modifier.width(500.dp),
                onClose = onClose,
            )
        }
    }
}

@ProtonVpnTvPreview
@Composable
private fun TvUpsellLayoutIapPreview() {
    ProtonThemeTv {
        val viewState = TvUpsellViewModel.ViewState(
            imageResId = R.drawable.worldwide_coverage_tv,
            titleResId = R.string.upsell_tv_all_countries_title,
            descriptionResId = R.string.upsell_tv_all_countries_description,
            descriptionArgs = { context ->
                arrayOf(
                    context.resources.getQuantityString(
                        R.plurals.upgrade_plus_servers_new,
                        21123,
                        21123,
                    ),
                    context.resources.getQuantityString(
                        R.plurals.upgrade_plus_countries,
                        120,
                        120,
                    ),
                )
            }
        )
        val cycles = listOf(
            UpgradeDialogViewModel.CycleViewInfo(
                productId = "ProductId",
                offerToken = "OfferToken",
                cycle = PlanCycle(PaymentCycle.Year(1), PaymentRecurrence.Finite(1)),
                priceInfo = UpgradeDialogViewModel.PriceInfo(
                    "$120.00",
                    formattedPerMonthPrice = "$10.00",
                    savePercent = -37,
                    hasDiscountPrice = true
                )
            ),
            UpgradeDialogViewModel.CycleViewInfo(
                productId = "ProductId",
                offerToken = "OfferToken",
                cycle = PlanCycle(PaymentCycle.Month(1), PaymentRecurrence.Infinite),
                priceInfo = UpgradeDialogViewModel.PriceInfo("$15.99", hasDiscountPrice = false)
            ),
        )
        val plan = PlanModel("VPN Plus", "vpn2022", "USD", cycles, PaymentCycle.Year(1))
        val paymentState = PaymentPanelState(
            upgradeState = UpgradeDialogViewModel.State.PurchaseReady(listOf(plan), plan, false),
            selectedCycle = cycles.first().cycle.paymentCycle,
            {}, {}, {},
        )
        TvUpsellLayoutIap(viewState, paymentState, {})
    }
}

@ProtonVpnTvPreview
@Composable
private fun TvUpsellLayoutIapLoadingPreview() {
    ProtonThemeTv {
        val viewState = TvUpsellViewModel.ViewState(
            imageResId = R.drawable.worldwide_coverage_tv,
            titleResId = R.string.upsell_tv_all_countries_title,
            descriptionResId = R.string.upsell_tv_all_countries_description,
            descriptionArgs = { context ->
                arrayOf(
                    context.resources.getQuantityString(
                        R.plurals.upgrade_plus_servers_new,
                        21123,
                        21123,
                    ),
                    context.resources.getQuantityString(
                        R.plurals.upgrade_plus_countries,
                        120,
                        120,
                    ),
                )
            }
        )
        val paymentState = PaymentPanelState(
            upgradeState = UpgradeDialogViewModel.State.LoadingPlans(2, null),
            selectedCycle = null,
            {}, {}, {},
        )
        TvUpsellLayoutIap(viewState, paymentState, {})
    }
}
