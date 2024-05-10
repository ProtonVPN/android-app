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

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.protonvpn.android.R
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.ui.CardLabel
import com.protonvpn.android.redesign.recents.ui.RecentAvailability
import com.protonvpn.android.redesign.recents.ui.RecentItemViewState
import com.protonvpn.android.redesign.recents.ui.RecentsList
import com.protonvpn.android.redesign.recents.ui.VpnConnectionCardViewState
import com.protonvpn.android.redesign.recents.ui.rememberRecentsExpandState
import com.protonvpn.android.redesign.recents.usecases.RecentsListViewState
import com.protonvpn.android.redesign.vpn.ui.ChangeServerViewState
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.ui.home.vpn.ChangeServerButton
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.ui.compose.FusionComposeTest
import org.junit.Test
import kotlin.math.roundToInt

class RecentsListUiTests : FusionComposeTest() {

    @Test
    fun whenNoRecentsThenRecentsHeaderIsHidden() {
        val viewState = RecentsListViewState(
            ConnectionCardViewState,
            emptyList(),
            null
        )
        composeRule.setContent {
            RecentsList(viewState, {}, {}, {}, {}, {}, {}, expandState = null, errorSnackBar = null)
        }
        node.withText(R.string.fastest_country).assertIsDisplayed()
        node.withText(R.string.recents_headline).assertDoesNotExist()
        node.withText(R.string.home_upsell_carousel_headline).assertDoesNotExist()
    }

    @Test
    fun whenRecentsPresentThenRecentsHeaderIsShown() {
        val viewState = RecentsListViewState(
            ConnectionCardViewState,
            listOf(
                RecentItemViewState(0, ConnectIntentViewSwitzerland, false, false, RecentAvailability.ONLINE)
            ),
            null
        )
        composeRule.setContent {
            RecentsList(viewState, {}, {}, {}, {}, {}, {}, expandState = null, errorSnackBar = null)
        }
        node.withText(R.string.fastest_country).assertIsDisplayed()
        node.withText("Switzerland").assertIsDisplayed()
        node.withText(R.string.recents_headline).assertIsDisplayed()
    }

    @Test
    fun whenUpsellContentIsPresentThenUpsellHeaderIsShown() {
        val viewState = RecentsListViewState(
            ConnectionCardViewState,
            emptyList(),
            null
        )
        composeRule.setContent {
            val upsellContent = @Composable { modifier: Modifier, padding: Dp ->
                Text("dummy upsell content", modifier = modifier)
            }
            RecentsList(viewState, {}, {}, {}, {}, {}, {}, upsellContent = upsellContent, expandState = null, errorSnackBar = null)
        }
        node.withText(R.string.fastest_country).assertIsDisplayed()
        node.withText(R.string.home_upsell_carousel_headline).assertIsDisplayed()
        node.withText(R.string.recents_headline).assertDoesNotExist()
    }

    @Test
    fun whenConnectionCardGetsSmallerItStaysFullyCollapsed() {
        val viewState = RecentsListViewState(
            ConnectionCardViewState,
            emptyList(),
            null
        )
        val changeServerButtonState = mutableStateOf<ChangeServerViewState?>(ChangeServerViewState.Unlocked)
        val upsellContentText = "dummy upsell content"
        composeRule.setContent {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val expandState = rememberRecentsExpandState()
                val maxHeightPx = LocalDensity.current.run { maxHeight.toPx() }
                expandState.setMaxHeight(maxHeightPx.roundToInt())
                val upsellContent = @Composable { modifier: Modifier, padding: Dp ->
                    Text(upsellContentText, modifier = modifier)
                }

                val changeServerButton: (@Composable ColumnScope.() -> Unit)? = changeServerButtonState.value?.let {
                    @Composable {
                        ChangeServerButton(it, onChangeServerClick = {}, onUpgradeButtonShown = {})
                    }
                }
                RecentsList(
                    viewState, {}, {}, {}, {}, {}, {},
                    modifier = Modifier.offset { IntOffset(0, expandState.listOffsetPx) },
                    changeServerButton = changeServerButton,
                    upsellContent = upsellContent,
                    expandState = expandState,
                    errorSnackBar = null
                )
            }
        }
        node.withText(R.string.server_change_button_title).assertIsDisplayed()
        changeServerButtonState.value = null // Connection card gets smaller.

        node.withText(R.string.server_change_button_title).assertDoesNotExist()
        node.withText(upsellContentText).assertIsNotDisplayed() // Upsell content didn't scroll up.
    }

    companion object {
        private val ConnectIntentViewFastest =
            ConnectIntentViewState(ConnectIntentPrimaryLabel.Country(CountryId.fastest, null), null, emptySet())
        private val ConnectIntentViewSwitzerland =
            ConnectIntentViewState(ConnectIntentPrimaryLabel.Country(CountryId.switzerland, null), null, emptySet())
        private val ConnectionCardViewState = VpnConnectionCardViewState(
            cardLabel = CardLabel(R.string.connection_card_label_last_connected),
            mainButtonLabelRes = R.string.buttonConnect,
            isConnectedOrConnecting = false,
            connectIntentViewState = ConnectIntentViewFastest,
            canOpenConnectionPanel = false,
            canOpenFreeCountriesPanel = false,
        )
    }
}
