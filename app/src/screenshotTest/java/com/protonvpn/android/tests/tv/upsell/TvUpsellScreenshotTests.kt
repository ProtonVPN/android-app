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

package com.protonvpn.android.tests.tv.upsell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.android.tools.screenshot.PreviewTest
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnTvPreview
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.tv.upsell.TvUpsellContent
import com.protonvpn.android.tv.upsell.TvUpsellLayoutIap
import com.protonvpn.android.tv.upsell.TvUpsellLayoutQrCode
import com.protonvpn.android.tv.upsell.TvUpsellViewModel
import com.protonvpn.android.ui.planupgrade.PaymentCycle
import com.protonvpn.android.ui.planupgrade.PaymentPanelState
import com.protonvpn.android.ui.planupgrade.PaymentRecurrence
import com.protonvpn.android.ui.planupgrade.PlanCycle
import com.protonvpn.android.ui.planupgrade.PlanModel
import com.protonvpn.android.ui.planupgrade.UpgradeDialogViewModel

@PreviewTest
@ProtonVpnTvPreview
@Composable
fun TvUpsellLayoutIapScreenshotTest(
    @PreviewParameter(UpgradeStateProvider::class) upgradeState: UpgradeDialogViewModel.State
) {
    ProtonVpnTvPreview {
        val paymentPanelState = PaymentPanelState(
            upgradeState = upgradeState,
            selectedCycle = (upgradeState as? UpgradeDialogViewModel.State.PurchaseReady)
                ?.selectedPlan?.preselectedCycle,
            { _ -> }, {}, {},
        )
        TvUpsellLayoutIap(
            viewState = netShieldViewState,
            paymentPanelState = paymentPanelState,
            onClose = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@PreviewTest
@ProtonVpnTvPreview
@Composable
fun TvUpsellLayoutQrCodeScreenshotTest(
    @PreviewParameter(TvUpsellContentProvider::class) content: TvUpsellContent
) {
    ProtonVpnTvPreview {
        TvUpsellLayoutQrCode(
            content = content,
            freeCountries = 10,
            plusCountriesRounded = 120,
            onClose = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

class TvUpsellContentProvider : PreviewParameterProvider<TvUpsellContent> {
    override val values = sequenceOf(
        TvUpsellContent.Country(CountryId("US")),
        TvUpsellContent.Country(null),
        TvUpsellContent.CustomDns,
        TvUpsellContent.LanConnections,
        TvUpsellContent.NetShield,
        TvUpsellContent.SplitTunneling,
        TvUpsellContent.SubscriptionExpired,
        TvUpsellContent.PaymentPending,
    )
}

class UpgradeStateProvider : PreviewParameterProvider<UpgradeDialogViewModel.State> {
    override val values = sequenceOf(
        UpgradeDialogViewModel.State.LoadingPlans(2, null),
        UpgradeDialogViewModel.State.PurchaseReady(listOf(plusPlan), plusPlan, inProgress = false),
        UpgradeDialogViewModel.State.LoadError,
        // Shown in the IAP layout only when the non-IAP screen is disabled with the kill-switch.
        UpgradeDialogViewModel.State.UpgradeDisabled(false),
    )
}

private val netShieldViewState = TvUpsellViewModel.ViewState(
    imageResId = R.drawable.netshield_tv,
    titleResId = R.string.upsell_tv_netshield_title,
    descriptionResId = R.string.upsell_tv_netshield_description,
)

private val plusPlan = PlanModel(
    displayName = "VPN Plus",
    planName = "vpn2022",
    currency = "USD",
    cycles = listOf(
        UpgradeDialogViewModel.CycleViewInfo(
            productId = "ProductId",
            offerToken = "OfferToken",
            cycle = PlanCycle(PaymentCycle.Year(1), PaymentRecurrence.Finite(1)),
            priceInfo = UpgradeDialogViewModel.PriceInfo(
                formattedPrice = "$120.00",
                formattedPerMonthPrice = "$10.00",
                savePercent = -37,
                hasDiscountPrice = true,
            ),
        ),
        UpgradeDialogViewModel.CycleViewInfo(
            productId = "ProductId",
            offerToken = "OfferToken",
            cycle = PlanCycle(PaymentCycle.Month(1), PaymentRecurrence.Infinite),
            priceInfo = UpgradeDialogViewModel.PriceInfo("$15.99", hasDiscountPrice = false),
        ),
    ),
    preselectedCycle = PaymentCycle.Year(1),
)
