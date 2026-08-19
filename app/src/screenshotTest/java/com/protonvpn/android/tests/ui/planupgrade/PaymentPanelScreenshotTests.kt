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

package com.protonvpn.android.tests.ui.planupgrade

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.protonvpn.android.annotations.ProtonVpnTestPreview
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.ui.planupgrade.PaymentCycle
import com.protonvpn.android.ui.planupgrade.PaymentPanel
import com.protonvpn.android.ui.planupgrade.PaymentPanelState
import com.protonvpn.android.ui.planupgrade.PaymentRecurrence
import com.protonvpn.android.ui.planupgrade.PlanCycle
import com.protonvpn.android.ui.planupgrade.PlanModel
import com.protonvpn.android.ui.planupgrade.UpgradeDialogViewModel

@PreviewTest
@ProtonVpnTestPreview
@Composable
fun PaymentPanelScreenshotTests() {
    ProtonVpnPreview {
        val planCycles = listOf(
            PlanCycle(PaymentCycle.Week(1), PaymentRecurrence.Infinite),
            PlanCycle(PaymentCycle.Month(1), PaymentRecurrence.Finite(1)),
            PlanCycle(PaymentCycle.Month(2), PaymentRecurrence.Finite(1)),
            PlanCycle(PaymentCycle.Year(1), PaymentRecurrence.Infinite),
        )
        val uiCycles = planCycles.mapIndexed { index, planCycle ->
            val priceInfo = UpgradeDialogViewModel.PriceInfo(
                formattedPrice = "\$$index.00",
                formattedPerMonthPrice = if (index > 1) "\$0.50" else null,
                hasDiscountPrice = false, // Not used in UI.
            )
            UpgradeDialogViewModel.CycleViewInfo("product", "token", planCycle, priceInfo)
        }
        val plan = PlanModel(
            "VPN", "vpn", "USD", uiCycles, PaymentCycle.Month(2)
        )
        val paymentPanelState = PaymentPanelState(
            upgradeState = UpgradeDialogViewModel.State.PurchaseReady(
                listOf(plan),
                plan,
                inProgress = false
            ),
            selectedCycle = null,
            { _ -> }, {}, {},
        )
        PaymentPanel(
            paymentPanelState,
            {}
        )
    }
}
