/*
 * Copyright (c) 2023. Proton Technologies AG
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
package com.protonvpn.tests.redesign.vpn.ui

import com.protonvpn.android.netshield.NetShieldStats
import com.protonvpn.android.redesign.vpn.ui.LocationText
import com.protonvpn.android.redesign.vpn.ui.VpnStatusView
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.ui.compose.FusionComposeTest

class VpnStatusViewTests : FusionComposeTest() {

    @Test
    fun disabledViewDisplay() {
        val stateFlow = MutableStateFlow(
            VpnStatusViewState.Disabled(
                LocationText("Country", "192.168.0.1")
            )
        )

        composeRule.setContent {
            VpnStatusView(stateFlow = stateFlow)
        }

        node.withText("You are not protected")
            .assertIsDisplayed()
        node.useUnmergedTree()
            .withText("Country - 192.168.0.1")
            .assertIsDisplayed()
    }

    @Test
    fun connectingStateDisplay() {
        val stateFlow = MutableStateFlow(
            VpnStatusViewState.Connecting(
                LocationText("Country", "192.168.0.1")
            )
        )

        composeRule.setContent {
            VpnStatusView(stateFlow = stateFlow)
        }

        node.withText("Protecting your identity")
            .assertIsDisplayed()
        node.useUnmergedTree()
            .withText("Country - 192.168.0.1")
            .assertIsDisplayed()
    }

    @Test
    fun connectedStateDisplay() {
        val stateFlow = MutableStateFlow(
            VpnStatusViewState.Connected(
                isSecureCoreServer = true,
                netShieldStatsGreyedOut = false,
                netShieldStats = NetShieldStats(5)
            )
        )

        composeRule.setContent {
            VpnStatusView(stateFlow = stateFlow)
        }

        node.withText("Protected")
            .assertIsDisplayed()
        node.useUnmergedTree()
            .hasAncestor(node.withTag("adsBlocked"))
            .withTag("value")
            .assertContainsText("5")
    }
}
