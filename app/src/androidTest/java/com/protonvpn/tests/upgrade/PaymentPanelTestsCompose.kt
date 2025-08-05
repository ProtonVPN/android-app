/*
 *
 *  * Copyright (c) 2023. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.tests.upgrade

import androidx.compose.foundation.layout.Column
import com.protonvpn.android.ui.planupgrade.CommonUpgradeDialogViewModel
import com.protonvpn.android.ui.planupgrade.RenewInfo
import com.protonvpn.android.ui.planupgrade.ViewState
import com.protonvpn.testRules.setVpnContent
import me.proton.core.plan.presentation.entity.PlanCycle
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.ui.compose.FusionComposeTest
import org.junit.Test

private val cycleInfoWithRenew = ViewState.CycleViewInfo(PlanCycle.YEARLY, 0, 0,
    CommonUpgradeDialogViewModel.PriceInfo(
        formattedPrice = "10 CHF",
        formattedRenewPrice = "20 CHF"
    )
)

private val cycleInfo = ViewState.CycleViewInfo(PlanCycle.YEARLY, 0, 0,
    CommonUpgradeDialogViewModel.PriceInfo(formattedPrice = "10 CHF")
)


class PaymentPanelTestsCompose : FusionComposeTest() {

    @Test
    fun renewPriceIsShownYear() {
        composeRule.setVpnContent {
            RenewInfo(cycleInfoWithRenew)
        }
        node.withText("Welcome offer. Auto renews at 20 CHF/year").assertIsDisplayed()
    }

    @Test
    fun renewPriceIsShownMonth() {
        composeRule.setVpnContent {
            RenewInfo(cycleInfoWithRenew.copy(cycle = PlanCycle.MONTHLY))
        }
        node.withText("Welcome offer. Auto renews at 20 CHF/month").assertIsDisplayed()
    }

    @Test
    fun regularPriceIsShown() {
        composeRule.setVpnContent {
            Column {
                RenewInfo(cycleInfo)
            }
        }
        node.withText("Subscription auto renews at 10 CHF/year").assertIsDisplayed()
    }
}
