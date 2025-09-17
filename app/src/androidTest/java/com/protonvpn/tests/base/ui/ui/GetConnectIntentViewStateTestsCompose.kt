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

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import com.protonvpn.android.servers.api.SERVER_FEATURE_P2P
import com.protonvpn.android.servers.api.SERVER_FEATURE_RESTRICTED
import com.protonvpn.android.servers.Server
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.profiles.data.Profile
import com.protonvpn.android.profiles.data.ProfileAutoOpen
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.data.ProfileInfo
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentLabels
import com.protonvpn.android.redesign.vpn.ui.GetConnectIntentViewState
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.mocks.FakeGetProfileById
import com.protonvpn.test.shared.createServer
import com.protonvpn.testRules.setVpnContent
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import me.proton.core.domain.entity.UserId
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.ui.compose.FusionComposeTest
import org.junit.Before
import org.junit.Test
import java.util.EnumSet

// These tests use mocking of final classes that's not available on API < 28
@SdkSuppress(minSdkVersion = 28)
class GetConnectIntentViewStateTestsCompose : FusionComposeTest() {

    @MockK
    private lateinit var mockServerManager: ServerManager2

    @MockK
    private lateinit var mockTranslator: Translator

    private lateinit var getConnectIntentViewState: GetConnectIntentViewState

    private val noServerFeatures = EnumSet.noneOf(ServerFeature::class.java)
    private val p2pServerFeatures = EnumSet.of(ServerFeature.P2P)

    private val serverCh =
        createServer("serverCh1", serverName = "CH#1", exitCountry = "CH", city = "Zurich", tier = 2)
    private val serverChState =
        createServer("serverChState1", serverName = "CH-R#1", exitCountry = "CH", city = "Zurich", tier = 2, state = "Canton Zurich")
    private val serverChFree =
        createServer("serverCh1Free", serverName = "CH-FREE#1", exitCountry = "CH", city = "Zurich", tier = 0)
    private val serverPl = createServer(
        "serverPl1",
        serverName = "PL#1",
        exitCountry = "PL",
        city = "Warsaw",
        tier = 2,
        features = SERVER_FEATURE_P2P
    )
    private val serverPlNoFeatures =
        createServer("serverPl2", serverName = "PL#2", exitCountry = "PL", city = "Warsaw", tier = 2)
    private val serverPlViaCh =
        createServer("serverPlViaCh", serverName = "CH-PL#1", exitCountry = "PL", entryCountry = "CH", tier = 2)
    private val serverLtViaSe =
        createServer("serverLtViaSe", serverName = "SE-LT#1", exitCountry = "LT", entryCountry = "SE", tier = 2)
    private val serverGateway =
        createServer(
            "serverGateway",
            serverName = "VPN#1",
            exitCountry = "US",
            gatewayName = "Gateway Name",
            tier = 2,
            features = SERVER_FEATURE_RESTRICTED
        )

    private val switzerland = CountryId("ch")
    private val poland = CountryId("pl")

    private lateinit var profiles: FakeGetProfileById

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        val allServers = listOf(serverCh, serverChFree, serverPl, serverPlNoFeatures, serverLtViaSe, serverPlViaCh, serverGateway)
        coEvery { mockServerManager.getServerById(any()) } answers {
            allServers.find { it.serverId == firstArg() }
        }
        coEvery { mockServerManager.getFreeCountries() } returns listOf(VpnCountry("ch", listOf(serverCh, serverChFree)))
        every { mockTranslator.getCity(any()) } answers { firstArg() }
        every { mockTranslator.getState(any()) } answers { firstArg() }

        profiles = FakeGetProfileById()
        getConnectIntentViewState = GetConnectIntentViewState(mockServerManager, mockTranslator, profiles)
    }

    @Test
    fun fastest() = runTest {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.fastest, noServerFeatures)
        setConnectIntentRowComposable(connectIntent, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Fastest country")
        node.withTag("secondaryLabel").assertDoesNotExist()
    }

    @Test
    fun fastestConnected() = runTest {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.fastest, noServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverCh, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Fastest country")
        node.withTag("secondaryLabel").hasChild(node.withText("Switzerland")).assertIsDisplayed()
    }

    @Test
    fun fastestWithFeature() = runTest {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.fastest, p2pServerFeatures)
        setConnectIntentRowComposable(connectIntent, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Fastest country")
        node.withTag("secondaryLabel").hasChild(node.withText("P2P")).assertIsDisplayed()
    }

    @Test
    fun fastestWithFeatureConnected() = runTest {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.fastest, p2pServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverPl, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Fastest country")
        node.withTag("secondaryLabel").hasChild(node.withText("Poland")).assertIsDisplayed()
        node.withTag("secondaryLabel").hasChild(node.withText("P2P")).assertIsDisplayed()
    }

    @Test
    fun fastestFreeUser() = runTest {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.fastest, noServerFeatures)
        setConnectIntentRowComposable(connectIntent, isFreeUser = true)

        node.withTag("primaryLabel").assertContainsText("Fastest free server")
        node.withTag("secondaryLabel")
            // The displayed text includes an embedded image, use content description for easier matching.
            .withContentDescription("Auto-selected from 1 country")
            .assertIsDisplayed()
    }

    @Test
    fun fastestFreeUserConnected() = runTest {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.fastest, noServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverChFree, isFreeUser = true)

        node.withTag("primaryLabel").assertContainsText("Fastest free server")
        node.withTag("secondaryLabel")
            .hasChild(node.withText("Switzerland #${serverChFree.serverNumber}")).assertIsDisplayed()
    }

    @Test
    fun country() = runTest {
        val connectIntent = ConnectIntent.FastestInCountry(switzerland, noServerFeatures)
        setConnectIntentRowComposable(connectIntent, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Switzerland")
        node.withTag("secondaryLabel").assertDoesNotExist()
    }

    @Test
    fun countryConnected() = runTest {
        val connectIntent = ConnectIntent.FastestInCountry(switzerland, noServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverCh, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Switzerland")
        node.withTag("secondaryLabel").assertDoesNotExist()
    }

    @Test
    fun countryConnectedToDifferentCountry() = runTest {
        val connectIntent = ConnectIntent.FastestInCountry(switzerland, noServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverPl, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").assertDoesNotExist()
    }

    @Test
    fun city() = runTest {
        val connectIntent = ConnectIntent.FastestInCity(switzerland, "Zurich", noServerFeatures)
        setConnectIntentRowComposable(connectIntent, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Switzerland")
        node.withTag("secondaryLabel").hasChild(node.withText("Zurich")).assertIsDisplayed()
    }

    @Test
    fun state() = runTest {
        val connectIntent = ConnectIntent.FastestInState(switzerland, "Canton Zurich", noServerFeatures)
        setConnectIntentRowComposable(connectIntent, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Switzerland")
        node.withTag("secondaryLabel").hasChild(node.withText("Canton Zurich")).assertIsDisplayed()
    }

    @Test
    fun cityConnected() = runTest {
        val connectIntent = ConnectIntent.FastestInCity(switzerland, "Zurich", noServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverCh, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Switzerland")
        node.withTag("secondaryLabel").hasChild(node.withText("Zurich")).assertIsDisplayed()
    }

    @Test
    fun stateConnected() = runTest {
        val connectIntent = ConnectIntent.FastestInState(switzerland, "Canton Zurich", noServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverChState, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Switzerland")
        node.withTag("secondaryLabel").hasChild(node.withText("Canton Zurich")).assertIsDisplayed()
    }

    @Test
    fun cityConnectedToDifferentCountryAndCity() = runTest {
        val connectIntent = ConnectIntent.FastestInCity(switzerland, "Zurich", noServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverPl, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").hasChild(node.withText("Warsaw")).assertIsDisplayed()
    }

    @Test
    fun cityWithTranslation() = runTest {
        every { mockTranslator.getCity("Zurich") } returns "Zurych"
        val connectIntent = ConnectIntent.FastestInCity(switzerland, "Zurich", noServerFeatures)
        setConnectIntentRowComposable(connectIntent, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Switzerland")
        node.withTag("secondaryLabel").hasChild(node.withText("Zurych")).assertIsDisplayed()
    }

    @Test
    fun stateWithTranslation() = runTest {
        every { mockTranslator.getState("Canton Zurich") } returns "Kanton Zurych"
        val connectIntent = ConnectIntent.FastestInState(switzerland, "Canton Zurich", noServerFeatures)
        setConnectIntentRowComposable(connectIntent, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Switzerland")
        node.withTag("secondaryLabel").hasChild(node.withText("Kanton Zurych")).assertIsDisplayed()
    }

    @Test
    fun secureCoreFastest() = runTest {
        val connectIntent = ConnectIntent.SecureCore(CountryId.fastest, CountryId.fastest)
        setConnectIntentRowComposable(connectIntent, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Fastest country")
        node.withTag("secondaryLabel").hasChild(node.withText("via Secure Core")).assertIsDisplayed()
    }

    @Test
    fun secureCoreFastestConnected() = runTest {
        val connectIntent = ConnectIntent.SecureCore(CountryId.fastest, CountryId.fastest)
        setConnectIntentRowComposable(connectIntent, serverPlViaCh, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Fastest country")
        node.withTag("secondaryLabel").hasChild(node.withText("Poland via Switzerland")).assertIsDisplayed()
    }

    @Test
    fun secureCoreFastestWithExitCountry() = runTest {
        val connectIntent = ConnectIntent.SecureCore(poland, CountryId.fastest)
        setConnectIntentRowComposable(connectIntent, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").hasChild(node.withText("via Secure Core")).assertIsDisplayed()
    }

    @Test
    fun secureCoreFastestWithExitCountryConnected() = runTest {
        val connectIntent = ConnectIntent.SecureCore(poland, CountryId.fastest)
        setConnectIntentRowComposable(connectIntent, serverPlViaCh, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").hasChild(node.withText("via Switzerland")).assertIsDisplayed()
    }

    @Test
    fun secureCoreWithEntryAndExitCountry() = runTest {
        val connectIntent = ConnectIntent.SecureCore(poland, switzerland)
        setConnectIntentRowComposable(connectIntent, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").hasChild(node.withText("via Switzerland")).assertIsDisplayed()
    }

    @Test
    fun secureCoreWithEntryAndExitCountryConnected() = runTest {
        val connectIntent = ConnectIntent.SecureCore(poland, switzerland)
        setConnectIntentRowComposable(connectIntent, serverPlViaCh, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").hasChild(node.withText("via Switzerland")).assertIsDisplayed()
    }

    @Test
    fun secureCoreWithEntryAndExitCountryConnectedToDifferentCountries() = runTest {
        val connectIntent = ConnectIntent.SecureCore(poland, switzerland)
        setConnectIntentRowComposable(connectIntent, serverLtViaSe, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Lithuania")
        node.withTag("secondaryLabel").hasChild(node.withText("via Sweden")).assertIsDisplayed()
    }

    @Test
    fun server() = runTest {
        val connectIntent = ConnectIntent.fromServer(serverPl, noServerFeatures)
        setConnectIntentRowComposable(connectIntent, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").hasChild(node.withText("Warsaw #1")).assertIsDisplayed()
    }

    @Test
    fun serverConnected() = runTest {
        val connectIntent = ConnectIntent.fromServer(serverPl, noServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverPl, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").hasChild(node.withText("Warsaw #1")).assertIsDisplayed()
    }

    @Test
    fun serverWithFeatures() = runTest {
        val connectIntent = ConnectIntent.fromServer(serverPl, p2pServerFeatures)
        setConnectIntentRowComposable(connectIntent, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").hasChild(node.withText("Warsaw #1")).assertIsDisplayed()
        node.withTag("secondaryLabel").hasChild(node.withText("P2P")).assertIsDisplayed()
    }

    @Test
    fun serverWithFeaturesConnected() = runTest {
        val connectIntent = ConnectIntent.fromServer(serverPl, p2pServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverPl, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").hasChild(node.withText("Warsaw #1")).assertIsDisplayed()
        node.withTag("secondaryLabel").hasChild(node.withText("P2P")).assertIsDisplayed()
    }

    @Test
    fun serverWithFeaturesConnectedToServerWithNoFeatures() = runTest {
        val connectIntent = ConnectIntent.fromServer(serverPl, p2pServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverPlNoFeatures, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").hasChild(node.withText("Warsaw #2")).assertIsDisplayed()
        node.withTag("secondaryLabel").hasChild(node.withText("P2P")).assertDoesNotExist()
    }

    @Test
    fun freeServer() = runTest {
        val connectIntent = ConnectIntent.fromServer(serverChFree, noServerFeatures)
        setConnectIntentRowComposable(connectIntent, isFreeUser = true)

        node.withTag("primaryLabel").assertContainsText("Switzerland")
        node.withTag("secondaryLabel").hasChild(node.withText("FREE#1")).assertIsDisplayed()
    }

    @Test
    fun serverConnectedToDifferentServer() = runTest {
        val connectIntent = ConnectIntent.fromServer(serverPl, p2pServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverCh, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Switzerland")
        node.withTag("secondaryLabel").hasChild(node.withText("Zurich #1")).assertIsDisplayed()
        node.withTag("secondaryLabel").hasChild(node.withText("P2P")).assertDoesNotExist()
    }

    @Test
    fun serverConnectedToDifferentServerWithState() = runTest {
        val connectIntent = ConnectIntent.fromServer(serverPl, p2pServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverChState, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Switzerland")
        node.withTag("secondaryLabel").hasChild(node.withText("Canton Zurich #1")).assertIsDisplayed()
        node.withTag("secondaryLabel").hasChild(node.withText("P2P")).assertDoesNotExist()
    }

    @Test
    fun gateway() = runTest {
        val connectIntent = ConnectIntent.Gateway("Gateway Name", null)
        setConnectIntentRowComposable(connectIntent, null, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Gateway Name")
        node.withTag("secondaryLabel").assertDoesNotExist()
    }

    @Test
    fun gatewayConnected() = runTest {
        val connectIntent = ConnectIntent.Gateway("Gateway Name", null)
        setConnectIntentRowComposable(connectIntent, serverGateway, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Gateway Name")
        node.withTag("secondaryLabel").assertDoesNotExist()
    }

    @Test
    fun gatewaySpecificServer() = runTest {
        val connectIntent = ConnectIntent.Gateway("Gateway Name", serverGateway.serverId)
        setConnectIntentRowComposable(connectIntent, null, isFreeUser = false)

        node.withTag("primaryLabel").assertContainsText("Gateway Name")
        node.withTag("secondaryLabel").hasChild(node.withText("United States #1")).assertIsDisplayed()
    }

    @Test
    fun profileWithCountryAndFeature() = runTest {
        setProfileComposable(1, "CH P2P", ConnectIntent.FastestInCountry(switzerland, p2pServerFeatures, profileId = 1))
        node.withTag("primaryLabel").assertContainsText("CH P2P")
        node.withTag("secondaryLabel").hasChild(node.withText("Switzerland")).assertIsDisplayed()
        node.withTag("secondaryLabel").hasChild(node.withText("P2P")).assertIsDisplayed()
    }

    @Test
    fun profileWithCountryAndFeatureConnected() = runTest {
        setProfileComposable(1, "CH P2P", ConnectIntent.FastestInCountry(switzerland, p2pServerFeatures, profileId = 1))
        node.withTag("primaryLabel").assertContainsText("CH P2P")
        node.withTag("secondaryLabel").hasChild(node.withText("Switzerland")).assertIsDisplayed()
        node.withTag("secondaryLabel").hasChild(node.withText("P2P")).assertIsDisplayed()
    }

    @Test
    fun profileServer() = runTest {
        setProfileComposable(1, "Existing Server", ConnectIntent.Server("serverCh1", switzerland, noServerFeatures, profileId = 1))
        node.withTag("secondaryLabel").hasChild(node.withText("Switzerland #1")).assertIsDisplayed()
    }

    @Test
    fun profileRemovedServer() = runTest {
        setProfileComposable(1, "Removed Server", ConnectIntent.Server("removedServer", switzerland, noServerFeatures, profileId = 1))
        node.withTag("primaryLabel").assertContainsText("Removed Server")
        node.withTag("secondaryLabel").hasChild(node.withText("Switzerland")).assertIsDisplayed()
    }

    private suspend fun setProfileComposable(id: Long, name: String, intent: ConnectIntent) {
        val profile = Profile(
            ProfileInfo(
                id = id,
                name = name,
                color = ProfileColor.Color1,
                icon = ProfileIcon.Icon1,
                createdAt = 0L,
                isUserCreated = true,
            ),
            connectIntent = intent,
            userId = UserId("dummy id"),
            autoOpen = ProfileAutoOpen.None,
        )
        profiles.set(profile)
        setConnectIntentRowComposable(profile.connectIntent, isFreeUser = false)
    }

    private suspend fun setConnectIntentRowComposable(
        connectIntent: ConnectIntent,
        connectedServer: Server? = null,
        isFreeUser: Boolean,
    ) {
        val state = getConnectIntentViewState.forConnectedIntent(connectIntent, isFreeUser, connectedServer)
        composeRule.setVpnContent {
            Row {
                ConnectIntentLabels(
                    primaryLabel = state.primaryLabel,
                    secondaryLabel = state.secondaryLabel,
                    serverFeatures = state.serverFeatures,
                    isConnected = false,
                    secondaryLabelVerticalPadding = 2.dp
                )
            }
        }
    }
}
