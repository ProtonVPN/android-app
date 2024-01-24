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

import androidx.compose.foundation.layout.Column
import com.protonvpn.android.netshield.NetShieldActions
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.netshield.NetShieldStats
import com.protonvpn.android.netshield.NetShieldViewState
import com.protonvpn.android.redesign.vpn.ui.LocationText
import com.protonvpn.android.redesign.vpn.ui.StatusBanner
import com.protonvpn.android.redesign.vpn.ui.VpnStatusBottom
import com.protonvpn.android.redesign.vpn.ui.VpnStatusTop
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewState
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.ui.compose.FusionComposeTest
import org.junit.Test

class VpnStatusViewTests : FusionComposeTest() {

    @Test
    fun disabledViewDisplay() {
        val state = VpnStatusViewState.Disabled(
            LocationText("Country", "192.168.0.1")
        )
        setContentForState(state)

        node.withText("You are unprotected")
            .assertIsDisplayed()
        node.useUnmergedTree()
            .withText("Country - 192.168.0.1")
            .assertIsDisplayed()
    }

    @Test
    fun connectingStateDisplay() {
        val state = VpnStatusViewState.Connecting(
            LocationText("Country", "192.168.0.1")
        )
        setContentForState(state)

        node.withText("Protecting your digital identity")
            .assertIsDisplayed()
        node.useUnmergedTree()
            .withText("Country - 192.168.0.1")
            .assertIsDisplayed()
    }

    @Test
    fun connectedStateDisplay() {
        val state = VpnStatusViewState.Connected(
            isSecureCoreServer = true,
            banner = StatusBanner.NetShieldBanner(
                NetShieldViewState(
                    protocol = NetShieldProtocol.ENABLED_EXTENDED,
                    netShieldStats = NetShieldStats(
                        adsBlocked = 5,
                        trackersBlocked = 0,
                        savedBytes = 2000
                    )
                )
            ),
        )

        setContentForState(state)

        node.withText("Protected")
            .assertIsDisplayed()
        node.useUnmergedTree()
            .hasAncestor(node.withTag("adsBlocked"))
            .withTag("value")
            .assertContainsText("5")
    }

    @Test
    fun lockedChangeServerDisplayNotTheCountryWantedBanner() {
        val state = VpnStatusViewState.Connected(
            isSecureCoreServer = true,
            banner = StatusBanner.UnwantedCountry
        )

        setContentForState(state)
        node.useUnmergedTree()
            .withText("Not the country you wanted?")
            .assertIsDisplayed()
    }

    @Test
    fun upsellBannerDisplayed() {
        val state = VpnStatusViewState.Connected(
            isSecureCoreServer = true,
            banner = StatusBanner.UpgradePlus
        )
        setContentForState(state)
        node.useUnmergedTree()
            .withText("Block ads, trackers, and malware on websites and apps")
            .assertIsDisplayed()
    }

    private fun setContentForState(state: VpnStatusViewState) {
        composeRule.setContent {
            Column {
                VpnStatusTop(state = state, transitionValue = { 1f })
                VpnStatusBottom(
                    state = state,
                    transitionValue = { 1f },
                    NetShieldActions({}, {}, {}, {})
                )
            }
        }
    }
}
