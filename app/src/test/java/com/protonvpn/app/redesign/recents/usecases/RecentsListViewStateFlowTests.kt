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

package com.protonvpn.app.redesign.recents.usecases

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.servers.api.ConnectingDomain
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.api.ServerEntryInfo
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.profiles.data.Profile
import com.protonvpn.android.profiles.data.ProfileAutoOpen
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.data.ProfileInfo
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.recents.data.DefaultConnection
import com.protonvpn.android.redesign.recents.data.RecentConnection
import com.protonvpn.android.redesign.recents.usecases.GetDefaultConnectIntent
import com.protonvpn.android.redesign.recents.usecases.GetIntentAvailability
import com.protonvpn.android.redesign.recents.usecases.ObserveDefaultConnection
import com.protonvpn.android.redesign.recents.usecases.RecentsListViewStateFlow
import com.protonvpn.android.redesign.recents.usecases.RecentsManager
import com.protonvpn.android.redesign.vpn.ChangeServerManager
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentAvailability
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.redesign.vpn.ui.GetConnectIntentViewState
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.settings.data.ApplyEffectiveUserSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.mocks.FakeGetProfileById
import com.protonvpn.mocks.FakeSettingsFeatureFlagsFlow
import com.protonvpn.mocks.createInMemoryServerManager
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createConnectIntentFastest
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createServer
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class RecentsListViewStateFlowTests {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockGetDefaultConnectIntent: GetDefaultConnectIntent

    @MockK
    private lateinit var mockObserveDefaultConnection: ObserveDefaultConnection

    @MockK
    private lateinit var mockRecentsManager: RecentsManager

    @MockK
    private lateinit var mockChangeServerManager: ChangeServerManager

    private lateinit var vpnStateMonitor: VpnStateMonitor

    private lateinit var currentUserProvider: TestCurrentUserProvider
    private lateinit var serverManager: ServerManager
    private lateinit var settingsFlow: MutableStateFlow<LocalUserSettings>
    private lateinit var testScope: TestScope

    private val serverCh: Server = createServer("1", exitCountry = "ch", tier = 2)
    private val serverIs: Server = createServer("2", exitCountry = "is", tier = 2)
    private val serverSe: Server = createServer("3", exitCountry = "se", tier = 2)
    private val serverSecureCore: Server =
        createServer("4", exitCountry = "pl", entryCountry = "ch", isSecureCore = true, tier = 2)

    private lateinit var viewStateFlow: RecentsListViewStateFlow
    private lateinit var profiles: FakeGetProfileById

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(MockSharedPreference())

        currentUserProvider = TestCurrentUserProvider(TestUser.plusUser.vpnUser)
        val testCoroutineScheduler = TestCoroutineScheduler()
        val testDispatcher = UnconfinedTestDispatcher(testCoroutineScheduler)
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher) // Remove this when ServerManager no longer uses asLiveData().
        val currentUser = CurrentUser(currentUserProvider)
        val bgScope = testScope.backgroundScope

        vpnStateMonitor = VpnStateMonitor()
        val vpnStatusProviderUI = VpnStatusProviderUI(bgScope, vpnStateMonitor)

        coEvery { mockGetDefaultConnectIntent(any(), any()) } returns createConnectIntentFastest()
        coEvery { mockRecentsManager.getRecentsList() } returns flowOf(emptyList())
        coEvery { mockRecentsManager.getMostRecentConnection() } returns flowOf(null)
        coEvery { mockRecentsManager.getRecentById(any()) } returns null
        coEvery { mockObserveDefaultConnection() } returns flowOf(DefaultConnection.LastConnection)
        every { mockChangeServerManager.isChangingServer } returns MutableStateFlow(false)

        profiles = FakeGetProfileById()
        settingsFlow = MutableStateFlow(LocalUserSettings.Default)
        val effectiveUserSettings = EffectiveCurrentUserSettings(bgScope, settingsFlow)
        val supportsProtocol = SupportsProtocol(createGetSmartProtocols())
        val settingsForConnection = SettingsForConnection(
            settingsFlow,
            profiles,
            ApplyEffectiveUserSettings(
                mainScope = testScope.backgroundScope,
                currentUser = currentUser,
                isTv = mockk(relaxed = true),
                flags = FakeSettingsFeatureFlagsFlow()
            ),
            vpnStatusProviderUI
        )

        mockkObject(CountryTools)
        every { CountryTools.getPreferredLocale() } returns Locale.US
        serverManager = createInMemoryServerManager(
            testScope,
            TestDispatcherProvider(testDispatcher),
            supportsProtocol,
            listOf(serverCh, serverIs, serverSe, serverSecureCore)
        )
        val serverManager2 = ServerManager2(serverManager, supportsProtocol)
        val getIntentAvailability = GetIntentAvailability(serverManager2, supportsProtocol)
        val translator = Translator(testScope.backgroundScope, serverManager)
        viewStateFlow = RecentsListViewStateFlow(
            recentsManager = mockRecentsManager,
            getConnectIntentViewState = GetConnectIntentViewState(
                serverManager = serverManager2,
                translator = translator,
                getProfileById = profiles
            ),
            serverManager = serverManager2,
            userSettings = effectiveUserSettings,
            settingsForConnection = settingsForConnection,
            getIntentAvailability = getIntentAvailability,
            observeDefaultConnection = mockObserveDefaultConnection,
            currentUser = currentUser,
            getDefaultConnectIntent = mockGetDefaultConnectIntent,
            vpnStatusProvider = vpnStatusProviderUI,
            changeServerManager = mockChangeServerManager
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain() // Remove this when ServerManager no longer uses asLiveData().
    }

    @Test
    fun defaultConnectionIsShownWhenThereAreNoRecents() = testScope.runTest {
        val viewState = viewStateFlow.first()
        val expectedPrimaryLabel = ConnectIntentPrimaryLabel.Fastest(null, isSecureCore = false, isFree = false)
        val expectedConnectionCard = ConnectIntentViewState(expectedPrimaryLabel, null, emptySet())
        assertEquals(expectedConnectionCard, viewState.connectionCard.connectIntentViewState)
        assertEquals(emptyList(), viewState.recents)
    }

    @Test
    fun whenConnectedTheConnectionIsShownInConnectionCard() = testScope.runTest {
        vpnStateMonitor.updateStatus(
            VpnStateMonitor.Status(
                VpnState.Connected,
                ConnectionParams(ConnectIntentSwitzerland, serverCh, null, null)
            )
        )
        val viewState = viewStateFlow.first()
        assertEquals(ConnectIntentViewSwitzerland, viewState.connectionCard.connectIntentViewState)
        assertEquals(emptyList(), viewState.recents)
    }

    @Test
    fun mostRecentConnectionShownOnlyInConnectionCard() = testScope.runTest {
        coEvery { mockRecentsManager.getRecentsList() } returns flowOf(DefaultRecents)
        coEvery { mockRecentsManager.getMostRecentConnection() } returns flowOf(RecentIceland)
        coEvery { mockRecentsManager.getRecentById(any()) } returns RecentIceland

        val viewState = viewStateFlow.first()

        assertEquals(ConnectIntentViewIceland, viewState.connectionCard.connectIntentViewState)
        assertEquals(
            listOf(ConnectIntentViewSecureCore, ConnectIntentViewFastest, ConnectIntentViewSweden),
            viewState.recents.map { it.connectIntent }
        )
    }

    @Test
    fun pinnedItemWithActiveConnectionIsDisplayedInRecents() = testScope.runTest {
        coEvery { mockRecentsManager.getRecentsList() } returns flowOf(DefaultRecents)
        vpnStateMonitor.updateStatus(
            VpnStateMonitor.Status(
                VpnState.Connected,
                ConnectionParams(ConnectIntentSecureCore, serverSecureCore, null, null)
            )
        )
        val viewState = viewStateFlow.first()
        val expectedRecents = listOf(
            ConnectIntentViewSecureCore,
            ConnectIntentViewFastest,
            ConnectIntentViewSweden,
            ConnectIntentViewIceland,
        )
        assertEquals(ConnectIntentViewSecureCore, viewState.connectionCard.connectIntentViewState)
        assertEquals(expectedRecents, viewState.recents.map { it.connectIntent })
        assertTrue(viewState.recents.first().isPinned)
        assertTrue(viewState.recents.first().isConnected)
    }

    @Test
    fun pinnedItemWithIncompatibleFallbackConnectionIsNotDisplayedAsConnected() = testScope.runTest {
        val swedenPinned = RecentSweden.copy(isPinned = true)
        coEvery { mockRecentsManager.getRecentsList() } returns flowOf(listOf(swedenPinned))
        vpnStateMonitor.updateStatus(
            VpnStateMonitor.Status(
                VpnState.Connected,
                // Assume all servers in Sweden are offline and fallback connection was made to Switzerland.
                ConnectionParams(ConnectIntentSweden, serverCh, null, null)
            )
        )
        val viewState = viewStateFlow.first()
        val expectedRecents = listOf(ConnectIntentViewSweden)
        assertEquals(expectedRecents, viewState.recents.map { it.connectIntent })
        assertFalse(viewState.recents.first().isConnected)
    }

    @Test
    fun whenMostRecentIsUnavailableTheDefaultIsShownInConnectionCard() = testScope.runTest {
        coEvery { mockRecentsManager.getRecentsList() } returns flowOf(DefaultRecents)
        coEvery { mockRecentsManager.getMostRecentConnection() } returns flowOf(DefaultRecents.first())

        val offlineSecureCore = serverSecureCore.copy(rawIsOnline = false)
        serverManager.setServers(listOf(serverCh, serverIs, serverSe, offlineSecureCore), null, null)

        val viewState = viewStateFlow.first()

        val expected = createViewStateForFastestInCountry(ConnectIntent.Default)
        assertEquals(expected, viewState.connectionCard.connectIntentViewState)
    }

    @Test
    fun noRecentsShownToFreeUsers() = testScope.runTest {
        coEvery { mockRecentsManager.getRecentsList() } returns flowOf(DefaultRecents)
        currentUserProvider.vpnUser = TestUser.freeUser.vpnUser
        val servers = listOf(
            serverSecureCore,
            serverCh,
            serverSe.copy(tier = 0),
            serverIs.copy(tier = 0),
        )
        serverManager.setServers(servers, null, null)
        val viewState = viewStateFlow.first()
        assertEquals(emptyList(), viewState.recents.map { it.availability })
    }

    @Test
    fun offlineServersAreMarkedOffline() = testScope.runTest {
        val fastestP2P = ConnectIntent.FastestInCountry(CountryId.fastest, setOf(ServerFeature.P2P))
        val recents =
            DefaultRecents + listOf(
                RecentConnection.UnnamedRecent(100, false, fastestP2P)
            )
        coEvery { mockRecentsManager.getRecentsList() } returns flowOf(recents)
        val servers = listOf(
            serverSecureCore,
            serverCh,
            serverSe.copy(rawIsOnline = false),
            serverIs.copy(rawIsOnline = false),
        )
        serverManager.setServers(servers, null, null)
        val viewState = viewStateFlow.first()

        val expected = listOf(
            ConnectIntentAvailability.ONLINE,
            ConnectIntentAvailability.ONLINE,
            ConnectIntentAvailability.AVAILABLE_OFFLINE,
            ConnectIntentAvailability.AVAILABLE_OFFLINE,
            ConnectIntentAvailability.NO_SERVERS, // No P2P servers in ServerManager.
        )
        assertEquals(expected, viewState.recents.map { it.availability })
    }

    @Test
    fun serverStatusChangeIsReflectedInRecents() = testScope.runTest(timeout = 5_000.milliseconds) {
        coEvery { mockRecentsManager.getRecentsList() } returns flowOf(DefaultRecents)
        val viewStates = viewStateFlow
            .onEach {
                val offlineSecureCoreServer = serverSecureCore.copy(rawIsOnline = false)
                serverManager.setServers(listOf(serverCh, offlineSecureCoreServer), null, null)
            }
            .take(2)
            .toList()

        val secureCoreItemBefore = viewStates.first().recents.find { it.isPinned }
        val secureCoreItemAfter = viewStates.last().recents.find { it.isPinned }
        assertNotNull(secureCoreItemBefore)
        assertNotNull(secureCoreItemAfter)
        assertEquals(ConnectIntentAvailability.ONLINE, secureCoreItemBefore.availability)
        assertEquals(ConnectIntentAvailability.AVAILABLE_OFFLINE, secureCoreItemAfter.availability)
    }

    @Test
    fun protocolSettingChangeIsReflectedInRecents() = testScope.runTest(timeout = 5_000.milliseconds) {
        coEvery { mockRecentsManager.getRecentsList() } returns flowOf(DefaultRecents)
        val wgEntryProtocols = mapOf(
            ProtocolSelection(VpnProtocol.WireGuard).apiName to ServerEntryInfo("1.2.3.5", listOf(22, 443))
        )
        val wgOnlyDomain =
            ConnectingDomain(entryIp = null, wgEntryProtocols, "domain", null, "id1", publicKeyX25519 = "key")
        val wgOnlyServer = serverSecureCore.copy(connectingDomains = listOf(wgOnlyDomain))
        serverManager.setServers(listOf(serverCh, wgOnlyServer), null, null)

        val viewStates = viewStateFlow
            .onEach {
                settingsFlow.update { it.copy(protocol = ProtocolSelection(VpnProtocol.OpenVPN)) }
            }
            .take(2)
            .toList()

        val itemBefore = viewStates.first().recents.find { it.isPinned }
        val itemAfter = viewStates.last().recents.find { it.isPinned }
        assertNotNull(itemBefore)
        assertNotNull(itemAfter)
        assertEquals(ConnectIntentAvailability.ONLINE, itemBefore.availability)
        assertEquals(ConnectIntentAvailability.UNAVAILABLE_PROTOCOL, itemAfter.availability)
    }

    @Test
    fun freeCountriesInfoShownOnlyFoFreeUsers() = testScope.runTest {
        assertFalse(viewStateFlow.first().connectionCard.canOpenFreeCountriesPanel)

        currentUserProvider.vpnUser = TestUser.freeUser.vpnUser
        assertTrue(viewStateFlow.first().connectionCard.canOpenFreeCountriesPanel)
    }

    @Test
    fun profileNameIsShownInConnectionCard() = testScope.runTest {
        profiles.set(ProfileRecent.profile)
        coEvery { mockRecentsManager.getMostRecentConnection() } returns flowOf(ProfileRecent)
        coEvery { mockRecentsManager.getRecentById(any()) } answers { ProfileRecent }

        val viewState = viewStateFlow.first()
        val connectionCardPrimaryLabel = viewState.connectionCard.connectIntentViewState.primaryLabel
        assertTrue(connectionCardPrimaryLabel is ConnectIntentPrimaryLabel.Profile)
        assertEquals(ProfileRecent.profile.info.name, connectionCardPrimaryLabel.name)
    }

    companion object {
        val ConnectIntentSecureCore = ConnectIntent.SecureCore(CountryId("PL"), CountryId.switzerland)
        val ConnectIntentFastest = ConnectIntent.FastestInCountry(CountryId.fastest, emptySet())
        val ConnectIntentSweden = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        val ConnectIntentIceland = ConnectIntent.FastestInCountry(CountryId.iceland, emptySet())
        val ConnectIntentSwitzerland = ConnectIntent.FastestInCountry(CountryId.switzerland, emptySet())

        // ConnectIntentViewStates for the constants above:
        val ConnectIntentViewSecureCore = ConnectIntentViewState(
            ConnectIntentPrimaryLabel.Country(
                ConnectIntentSecureCore.exitCountry,
                ConnectIntentSecureCore.entryCountry,
            ),
            ConnectIntentSecondaryLabel.SecureCore(null, ConnectIntentSecureCore.entryCountry),
            emptySet()
        )
        val ConnectIntentViewFastest = createViewStateForFastestInCountry(ConnectIntentFastest)
        val ConnectIntentViewSweden = createViewStateForFastestInCountry(ConnectIntentSweden)
        val ConnectIntentViewIceland = createViewStateForFastestInCountry(ConnectIntentIceland)
        val ConnectIntentViewSwitzerland = createViewStateForFastestInCountry(ConnectIntentSwitzerland)

        val RecentSecureCore = RecentConnection.UnnamedRecent(1, true, ConnectIntentSecureCore)
        val RecentFastest = RecentConnection.UnnamedRecent(2, true, ConnectIntentFastest)
        val RecentSweden = RecentConnection.UnnamedRecent(3, false, ConnectIntentSweden)
        val RecentIceland = RecentConnection.UnnamedRecent(4, false, ConnectIntentIceland)

        val Profile = Profile(
            ProfileInfo(1, "MyProfile", ProfileColor.Color1, ProfileIcon.Icon1, createdAt = 0L, isUserCreated = true),
            ProfileAutoOpen.None,
            ConnectIntent.FastestInCountry(CountryId.fastest, emptySet(), profileId = 1),
            userId = TestUser.plusUser.vpnUser.userId
        )
        val ProfileRecent = RecentConnection.ProfileRecent(5, false, Profile)

        val DefaultRecents = listOf(RecentSecureCore, RecentFastest, RecentSweden, RecentIceland)

        private fun createViewStateForFastestInCountry(intent: ConnectIntent.FastestInCountry): ConnectIntentViewState {
            val primaryLabel = if (intent.country.isFastest)
                ConnectIntentPrimaryLabel.Fastest(connectedCountry = null, isSecureCore = false, isFree = false)
            else
                ConnectIntentPrimaryLabel.Country(intent.country, null)
            return ConnectIntentViewState(primaryLabel, secondaryLabel = null, serverFeatures = intent.features)
        }
    }
}
