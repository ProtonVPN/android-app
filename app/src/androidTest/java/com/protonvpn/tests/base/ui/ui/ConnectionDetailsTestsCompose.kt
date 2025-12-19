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
package com.protonvpn.tests.base.ui.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.protonvpn.android.R
import com.protonvpn.android.bus.TrafficUpdate
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.home_screen.ui.ConnectionDetails
import com.protonvpn.android.redesign.home_screen.ui.ConnectionDetailsViewModel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.servers.StreamingService
import com.protonvpn.android.vpn.IpPair
import com.protonvpn.testRules.setVpnContent
import kotlinx.coroutines.flow.MutableStateFlow
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.FusionConfig
import me.proton.test.fusion.ui.compose.FusionComposeTest
import org.junit.Test

class ConnectionDetailsTestsCompose : FusionComposeTest() {

    private val sampleViewState = ConnectionDetailsViewModel.ConnectionDetailsViewState.Connected(
        userIp = "192.168.1.1",
        vpnIp = IpPair("10.0.0.1", "2000::/64"),
        entryCountryId = CountryId.sweden,
        exitCountryId = CountryId.iceland,
        trafficHistory = listOf(
            TrafficUpdate(
                monotonicTimestampMs = 5_000_000,
                sessionStartTimestampMs = 0,
                downloadSpeed = 1000,
                uploadSpeed = 1000,
                sessionDownload = 10000,
                sessionUpload = 20000,
                sessionTimeSeconds = 5000
            )
        ),
        connectIntentViewState = ConnectIntentViewState(
            primaryLabel = ConnectIntentPrimaryLabel.Country(
                exitCountry = CountryId.sweden,
                entryCountry = CountryId.iceland,
            ),
            secondaryLabel = null,
            serverFeatures = emptySet()
        ),
        serverDisplayName = "CH#1",
        serverState = null,
        serverCity = "Stockholm",
        serverGatewayName = null,
        serverLoad = 50f,
        serverFeatures = ConnectionDetailsViewModel.ServerFeatures(
            hasSecureCore = true,
            streamingServices = listOf(StreamingService("ProtoStream", ""))
        )
    )

    @Test
    fun checkConnectionDetailsRendering() {
        composeRule.setVpnContent {
            ConnectionDetails(sampleViewState, onClosePanel = {})
        }

        node.withText("1 hr 23 min").assertExists()
        node.withText("***.***.*.*").assertExists()
        node.withText("10.0.0.1").assertExists()
        node.withText("Sweden").assertExists()
        node.withText("CH#1").assertExists()
        node.withText("Stockholm").assertExists()
    }

    @Test
    fun checkIPVisibilityToggle() {
        composeRule.setVpnContent {
            ConnectionDetails(sampleViewState, onClosePanel = {})
        }

        node.withText("***.***.*.*").assertExists()
        node.withContentDescription(R.string.accessibility_show_ip).click()
        node.withText("192.168.1.1").assertExists()
        node.withText("2000::/64").assertExists()
    }

    @Test
    fun checkFeaturesVisibility() {
        composeRule.setVpnContent {
            ConnectionDetails(sampleViewState, onClosePanel = {})
        }
        val resources = FusionConfig.targetContext.resources
        composeRule
            .onNodeWithText(resources.getString(R.string.connection_feature_secure_core_description))
            .performScrollTo()
            .assertExists()
        composeRule
            .onNodeWithText(resources.getString(R.string.connection_feature_streaming_description))
            .performScrollTo()
            .assertExists()
        node.withText("ProtoStream").assertExists()
    }

    @Test
    fun checkServerLoadRowClickOpensBottomSheet() {
        composeRule.setVpnContent {
            ConnectionDetails(sampleViewState, onClosePanel = {})
        }

        composeRule.onNodeWithText(FusionConfig.targetContext.resources.getString(R.string.connection_details_server_load))
            .performScrollTo()
            .performClick()
        node.withText(R.string.connection_details_server_load_description).assertExists()
    }

    @Test
    fun correctSpeedGraphScaleIsDisplayed() {
        val viewStateFlow = MutableStateFlow(sampleViewState)
        viewStateFlow.value = sampleViewState.copy(
            trafficHistory = listOf(TrafficUpdate(0, 0, 100, 0, 0, 0, 6000))
        )

        composeRule.setVpnContent {
            val viewState by viewStateFlow.collectAsState()
            ConnectionDetails(viewState, onClosePanel = {})
        }

        node.withText("Speed B/s").assertExists()

        viewStateFlow.value = sampleViewState.copy(
            trafficHistory = listOf(TrafficUpdate(0, 0, 1000000, 0, 0, 0, 6000))
        )

        node.withText("Speed MB/s").assertExists()
    }

    @Test
    fun checkSessionTimeChanges() {
        val viewStateFlow = MutableStateFlow(sampleViewState)
        viewStateFlow.value = sampleViewState.copy(
            trafficHistory = listOf(TrafficUpdate(0, 0, 0, 0, 0, 0, 6000))
        )

        composeRule.setVpnContent {
            val viewState by viewStateFlow.collectAsState()
            ConnectionDetails(viewState, onClosePanel = {})
        }

        node.withText("1 hr 40 min").assertExists()

        viewStateFlow.value = sampleViewState.copy(
            trafficHistory = listOf(TrafficUpdate(0, 0, 0, 0, 0, 0, 6))
        )

        node.withText("6 sec").assertExists()

        viewStateFlow.value = sampleViewState.copy(
            trafficHistory = listOf(TrafficUpdate(0, 0, 0, 0, 0, 0, 100000))
        )

        node.withText("1 day 3 hr").assertExists()
    }

}
