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

package com.protonvpn.tests.redesign.vpn.ui

import androidx.compose.foundation.layout.Row
import androidx.test.filters.SdkSuppress
import com.protonvpn.android.models.vpn.SERVER_FEATURE_P2P
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentRow
import com.protonvpn.android.redesign.vpn.ui.GetConnectIntentViewState
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.test.shared.createServer
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.ui.compose.FusionComposeTest
import org.junit.Before
import org.junit.Test
import java.util.EnumSet

// These tests use mocking of final classes that's not available on API < 28
@SdkSuppress(minSdkVersion = 28)
class GetConnectIntentViewStateTests : FusionComposeTest() {

    @MockK
    private lateinit var serverManager: ServerManager

    @MockK
    private lateinit var mockTranslator: Translator

    private lateinit var getConnectIntentViewState: GetConnectIntentViewState

    private val noServerFeatures = EnumSet.noneOf(ServerFeature::class.java)
    private val p2pServerFeatures = EnumSet.of(ServerFeature.P2P)

    private val serverCh =
        createServer("serverCh1", serverName = "CH#1", exitCountry = "CH", city = "Zurich", tier = 2)
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

    private val switzerland = CountryId("ch")
    private val poland = CountryId("pl")

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        val allServers = listOf(serverCh, serverChFree, serverPl, serverPlNoFeatures, serverLtViaSe, serverPlViaCh)
        every { serverManager.getServerById(any()) } answers {
            allServers.find { it.serverId == firstArg() }
        }
        every { mockTranslator.getCity(any()) } answers { firstArg() }

        getConnectIntentViewState = GetConnectIntentViewState(serverManager, mockTranslator)
    }

    @Test
    fun fastest() {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.fastest, noServerFeatures)
        setConnectIntentRowComposable(connectIntent)

        node.withTag("primaryLabel").assertContainsText("Fastest country")
        node.withTag("secondaryLabel").assertDoesNotExist()
    }

    @Test
    fun fastestConnected() {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.fastest, noServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverCh)

        node.withTag("primaryLabel").assertContainsText("Fastest country")
        node.withTag("secondaryLabel").hasChild(node.withText("Switzerland")).assertIsDisplayed()
    }

    @Test
    fun fastestWithFeature() {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.fastest, p2pServerFeatures)
        setConnectIntentRowComposable(connectIntent)

        node.withTag("primaryLabel").assertContainsText("Fastest country")
        node.withTag("secondaryLabel").hasChild(node.withText("P2P")).assertIsDisplayed()
    }

    @Test
    fun fastestWithFeatureConnected() {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.fastest, p2pServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverPl)

        node.withTag("primaryLabel").assertContainsText("Fastest country")
        node.withTag("secondaryLabel").hasChild(node.withText("Poland")).assertIsDisplayed()
        node.withTag("secondaryLabel").hasChild(node.withText("P2P")).assertIsDisplayed()
    }

    @Test
    fun country() {
        val connectIntent = ConnectIntent.FastestInCountry(switzerland, noServerFeatures)
        setConnectIntentRowComposable(connectIntent)

        node.withTag("primaryLabel").assertContainsText("Switzerland")
        node.withTag("secondaryLabel").assertDoesNotExist()
    }

    @Test
    fun countryConnected() {
        val connectIntent = ConnectIntent.FastestInCountry(switzerland, noServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverCh)

        node.withTag("primaryLabel").assertContainsText("Switzerland")
        node.withTag("secondaryLabel").assertDoesNotExist()
    }

    @Test
    fun countryConnectedToDifferentCountry() {
        val connectIntent = ConnectIntent.FastestInCountry(switzerland, noServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverPl)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").assertDoesNotExist()
    }

    @Test
    fun city() {
        val connectIntent = ConnectIntent.FastestInCity(switzerland, "Zurich", noServerFeatures)
        setConnectIntentRowComposable(connectIntent)

        node.withTag("primaryLabel").assertContainsText("Switzerland")
        node.withTag("secondaryLabel").hasChild(node.withText("Zurich")).assertIsDisplayed()
    }

    @Test
    fun cityConnected() {
        val connectIntent = ConnectIntent.FastestInCity(switzerland, "Zurich", noServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverCh)

        node.withTag("primaryLabel").assertContainsText("Switzerland")
        node.withTag("secondaryLabel").hasChild(node.withText("Zurich")).assertIsDisplayed()
    }

    @Test
    fun cityConnectedToDifferentCountryAndCity() {
        val connectIntent = ConnectIntent.FastestInCity(switzerland, "Zurich", noServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverPl)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").hasChild(node.withText("Warsaw")).assertIsDisplayed()
    }

    @Test
    fun cityWithTranslation() {
        every { mockTranslator.getCity("Zurich") } returns "Zurych"
        val connectIntent = ConnectIntent.FastestInCity(switzerland, "Zurich", noServerFeatures)
        setConnectIntentRowComposable(connectIntent)

        node.withTag("primaryLabel").assertContainsText("Switzerland")
        node.withTag("secondaryLabel").hasChild(node.withText("Zurych")).assertIsDisplayed()
    }

    @Test
    fun secureCoreFastest() {
        val connectIntent = ConnectIntent.SecureCore(CountryId.fastest, CountryId.fastest)
        setConnectIntentRowComposable(connectIntent)

        node.withTag("primaryLabel").assertContainsText("Fastest country")
        node.withTag("secondaryLabel").assertDoesNotExist()
    }

    @Test
    fun secureCoreFastestConnected() {
        val connectIntent = ConnectIntent.SecureCore(CountryId.fastest, CountryId.fastest)
        setConnectIntentRowComposable(connectIntent, serverPlViaCh)

        node.withTag("primaryLabel").assertContainsText("Fastest country")
        node.withTag("secondaryLabel").hasChild(node.withText("via Switzerland to Poland")).assertIsDisplayed()
    }

    @Test
    fun secureCoreFastestWithExitCountry() {
        val connectIntent = ConnectIntent.SecureCore(poland, CountryId.fastest)
        setConnectIntentRowComposable(connectIntent)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").assertDoesNotExist()
    }

    @Test
    fun secureCoreFastestWithExitCountryConnected() {
        val connectIntent = ConnectIntent.SecureCore(poland, CountryId.fastest)
        setConnectIntentRowComposable(connectIntent, serverPlViaCh)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").assertDoesNotExist()
    }

    @Test
    fun secureCoreWithEntryAndExitCountry() {
        val connectIntent = ConnectIntent.SecureCore(poland, switzerland)
        setConnectIntentRowComposable(connectIntent)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").hasChild(node.withText("via Switzerland")).assertIsDisplayed()
    }

    @Test
    fun secureCoreWithEntryAndExitCountryConnected() {
        val connectIntent = ConnectIntent.SecureCore(poland, switzerland)
        setConnectIntentRowComposable(connectIntent, serverPlViaCh)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").hasChild(node.withText("via Switzerland")).assertIsDisplayed()
    }

    @Test
    fun secureCoreWithEntryAndExitCountryConnectedToDifferentCountries() {
        val connectIntent = ConnectIntent.SecureCore(poland, switzerland)
        setConnectIntentRowComposable(connectIntent, serverLtViaSe)

        node.withTag("primaryLabel").assertContainsText("Lithuania")
        node.withTag("secondaryLabel").hasChild(node.withText("via Sweden")).assertIsDisplayed()
    }

    @Test
    fun server() {
        val connectIntent = ConnectIntent.Server(serverPl.serverId, noServerFeatures)
        setConnectIntentRowComposable(connectIntent)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").hasChild(node.withText("Warsaw #1")).assertIsDisplayed()
    }

    @Test
    fun serverConnected() {
        val connectIntent = ConnectIntent.Server(serverPl.serverId, noServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverPl)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").hasChild(node.withText("Warsaw #1")).assertIsDisplayed()
    }

    @Test
    fun serverWithFeatures() {
        val connectIntent = ConnectIntent.Server(serverPl.serverId, p2pServerFeatures)
        setConnectIntentRowComposable(connectIntent)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").hasChild(node.withText("Warsaw #1")).assertIsDisplayed()
        node.withTag("secondaryLabel").hasChild(node.withText("P2P")).assertIsDisplayed()
    }

    @Test
    fun serverWithFeaturesConnected() {
        val connectIntent = ConnectIntent.Server(serverPl.serverId, p2pServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverPl)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").hasChild(node.withText("Warsaw #1")).assertIsDisplayed()
        node.withTag("secondaryLabel").hasChild(node.withText("P2P")).assertIsDisplayed()
    }

    @Test
    fun serverWithFeaturesConnectedToServerWithNoFeatures() {
        val connectIntent = ConnectIntent.Server(serverPl.serverId, p2pServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverPlNoFeatures)

        node.withTag("primaryLabel").assertContainsText("Poland")
        node.withTag("secondaryLabel").hasChild(node.withText("Warsaw #2")).assertIsDisplayed()
        node.withTag("secondaryLabel").hasChild(node.withText("P2P")).assertDoesNotExist()
    }

    @Test
    fun freeServer() {
        val connectIntent = ConnectIntent.Server(serverChFree.serverId, noServerFeatures)
        setConnectIntentRowComposable(connectIntent)

        node.withTag("primaryLabel").assertContainsText("Switzerland")
        node.withTag("secondaryLabel").hasChild(node.withText("FREE#1")).assertIsDisplayed()
    }

    @Test
    fun serverConnectedToDifferentServer() {
        val connectIntent = ConnectIntent.Server(serverPl.serverId, p2pServerFeatures)
        setConnectIntentRowComposable(connectIntent, serverCh)

        node.withTag("primaryLabel").assertContainsText("Switzerland")
        node.withTag("secondaryLabel").hasChild(node.withText("Zurich #1")).assertIsDisplayed()
        node.withTag("secondaryLabel").hasChild(node.withText("P2P")).assertDoesNotExist()
    }

    private fun setConnectIntentRowComposable(connectIntent: ConnectIntent, connectedServer: Server? = null) {
        composeRule.setContent {
            Row {
                ConnectIntentRow(state = getConnectIntentViewState(connectIntent, connectedServer))
            }
        }
    }
}
