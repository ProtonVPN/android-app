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

package com.protonvpn.tests.redesign.recents

import com.protonvpn.android.R
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.ui.RecentAvailability
import com.protonvpn.android.redesign.recents.ui.RecentItemViewState
import com.protonvpn.android.redesign.recents.ui.RecentsList
import com.protonvpn.android.redesign.recents.ui.VpnConnectionCardViewState
import com.protonvpn.android.redesign.recents.ui.VpnConnectionState
import com.protonvpn.android.redesign.recents.usecases.RecentsListViewState
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.ui.compose.FusionComposeTest
import org.junit.Test

class RecentsListUiTests : FusionComposeTest() {

    @Test
    fun whenNoRecentsThenRecentsHeaderIsHidden() {
        val viewState = RecentsListViewState(
            VpnConnectionCardViewState(
                R.string.connection_card_label_last_connected,
                ConnectIntentViewFastest,
                VpnConnectionState.Disconnected
            ),
            emptyList(),
            null
        )
        composeRule.setContent {
            RecentsList(viewState, {}, {}, {}, {}, {}, {}, {}, expandState = null)
        }
        node.withText(R.string.fastest_country).assertIsDisplayed()
        node.withText(R.string.recents_headline).assertDoesNotExist()
    }

    @Test
    fun whenRecentsPresentThenRecentsHeaderIsShown() {
        val viewState = RecentsListViewState(
            VpnConnectionCardViewState(
                R.string.connection_card_label_last_connected,
                ConnectIntentViewFastest,
                VpnConnectionState.Disconnected
            ),
            listOf(
                RecentItemViewState(0, ConnectIntentViewSwitzerland, false, false, RecentAvailability.ONLINE)
            ),
            null
        )
        composeRule.setContent {
            RecentsList(viewState, {}, {}, {}, {}, {}, {}, {}, expandState = null)
        }
        node.withText(R.string.fastest_country).assertIsDisplayed()
        node.withText("Switzerland").assertIsDisplayed()
        node.withText(R.string.recents_headline).assertIsDisplayed()
    }

    companion object {
        val ConnectIntentViewFastest =
            ConnectIntentViewState(ConnectIntentPrimaryLabel.Country(CountryId.fastest, null), null, emptySet())
        val ConnectIntentViewSwitzerland =
            ConnectIntentViewState(ConnectIntentPrimaryLabel.Country(CountryId.switzerland, null), null, emptySet())
    }
}
