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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.AppDatabase.Companion.buildDatabase
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.recents.data.RecentsDao
import com.protonvpn.android.redesign.recents.usecases.GetConnectionCardAndRecentsViewStateFlow
import com.protonvpn.android.redesign.stubs.toProfile
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.redesign.vpn.ui.GetConnectIntentViewState
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createInMemoryServersStore
import com.protonvpn.test.shared.createServer
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GetConnectionCardAndRecentsViewStateFlowTests {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockCurrentUser: CurrentUser

    @MockK
    private lateinit var mockVpnStatusProvider: VpnStatusProviderUI

    private lateinit var serverManager: ServerManager
    private lateinit var vpnStatusFlow: MutableStateFlow<VpnStateMonitor.Status>
    private lateinit var vpnUserFlow: MutableStateFlow<VpnUser?>
    private lateinit var recentsDao: RecentsDao
    private lateinit var testScope: TestScope

    private val serverCh: Server = createServer("1", exitCountry = "ch", tier = 2)
    private val serverIs: Server = createServer("2", exitCountry = "is", tier = 2)
    private val serverSe: Server = createServer("3", exitCountry = "se", tier = 2)
    private val serverSecureCore: Server =
        createServer("4", exitCountry = "pl", entryCountry = "ch", isSecureCore = true, tier = 2)

    private lateinit var getViewState: GetConnectionCardAndRecentsViewStateFlow

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(MockSharedPreference())

        vpnStatusFlow = MutableStateFlow(VpnStateMonitor.Status(VpnState.Disabled, null))
        coEvery { mockVpnStatusProvider.status } returns vpnStatusFlow
        vpnUserFlow = MutableStateFlow(TestUser.plusUser.vpnUser)
        coEvery { mockCurrentUser.vpnUserFlow } returns vpnUserFlow

        val testCoroutineScheduler = TestCoroutineScheduler()
        val testDispatcher = UnconfinedTestDispatcher(testCoroutineScheduler)
        testScope = TestScope(testDispatcher)

        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            // Transactions "take over" a thread and cause deadlocks if run on the test thread.
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .buildDatabase()
        recentsDao = db.recentsDao()

        val settingsFlow = MutableStateFlow(LocalUserSettings.Default)
        val effectiveUserSettings = EffectiveCurrentUserSettings(testScope.backgroundScope, settingsFlow)
        val effectiveUserSettingsCached = EffectiveCurrentUserSettingsCached(settingsFlow)

        serverManager = ServerManager(
            testScope.backgroundScope,
            effectiveUserSettingsCached,
            mockCurrentUser,
            { testCoroutineScheduler.currentTime },
            SupportsProtocol(createGetSmartProtocols()),
            createInMemoryServersStore(),
            mockk(),
        )
        runBlocking {
            serverManager.setServers(listOf(serverCh, serverIs, serverSe, serverSecureCore), null)
        }
        val translator = Translator(testScope.backgroundScope, serverManager)
        getViewState = GetConnectionCardAndRecentsViewStateFlow(
            recentsDao,
            GetConnectIntentViewState(serverManager, translator),
            serverManager,
            effectiveUserSettings,
            mockVpnStatusProvider,
            mockCurrentUser
        )
    }

    @Test
    fun defaultConnectionIsShownWhenThereAreNoRecents() = testScope.runTest {
        val viewState = getViewState.first()
        val expectedConnectionCard =
            ConnectIntentViewState(CountryId.fastest, null, false, null, emptySet())
        assertEquals(expectedConnectionCard, viewState.connectionCard.connectIntentViewState)
        assertEquals(emptyList(), viewState.recents)
    }

    @Test
    fun whenConnectedTheConnectionIsShownInConnectionCard() = testScope.runTest {
        vpnStatusFlow.value = VpnStateMonitor.Status(
            VpnState.Connected,
            ConnectionParams(ConnectIntentSwitzerland.toProfile(serverManager), serverCh, null, null)
        )
        val viewState = getViewState.first()
        assertEquals(ConnectIntentViewSwitzerland, viewState.connectionCard.connectIntentViewState)
        assertEquals(emptyList(), viewState.recents)
    }

    @Test
    fun mostRecentConnectionShownOnlyInConnectionCard() = testScope.runTest {
        insertRecents()
        recentsDao.insertOrUpdateForConnection(ConnectIntentIceland, 1000)
        val viewState = getViewState.first()

        assertEquals(ConnectIntentViewIceland, viewState.connectionCard.connectIntentViewState)
        assertEquals(
            listOf(ConnectIntentViewSecureCore, ConnectIntentViewFastest, ConnectIntentViewSweden),
            viewState.recents.map { it.connectIntent }
        )
    }

    @Test
    fun pinnedItemWithActiveConnectionIsDisplayedInRecents() = testScope.runTest {
        insertRecents()
        vpnStatusFlow.value = VpnStateMonitor.Status(
            VpnState.Connected,
            ConnectionParams(ConnectIntentSecureCore.toProfile(serverManager), serverSecureCore, null, null)
        )
        val viewState = getViewState.first()
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
    fun paidServersUnavailableToFreeUser() = testScope.runTest {
        insertRecents()
        vpnUserFlow.value = TestUser.freeUser.vpnUser
        val servers = listOf(
            serverSecureCore,
            serverCh,
            serverSe.copy(tier = 0),
            serverIs.copy(tier = 0),
        )
        serverManager.setServers(servers, null)
        val viewState = getViewState.first()
        assertEquals(listOf(false, true, true, true), viewState.recents.map { it.isAvailable })
    }

    @Test
    fun offlineServersAreMarkedOffline() = testScope.runTest {
        insertRecents()
        val servers = listOf(
            serverSecureCore,
            serverCh,
            serverSe.copy(isOnline = false),
            serverIs.copy(isOnline = false),
        )
        serverManager.setServers(servers, null)
        val viewState = getViewState.first()
        assertEquals(listOf(true, true, false, false), viewState.recents.map { it.isOnline })
    }

    @Test
    fun serverStatusChangeIsReflectedInRecents() = testScope.runTest(dispatchTimeoutMs = 5_000) {
        insertRecents()
        val viewStates = getViewState
            .onEach {
                val offlineSecureCoreServer = serverSecureCore.copy(isOnline = false)
                serverManager.setServers(
                    listOf(serverCh, offlineSecureCoreServer),
                    null
                )
            }
            .take(2)
            .toList()

        val secureCoreItemBefore = viewStates.first().recents.find { it.isPinned }
        val secureCoreItemAfter = viewStates.last().recents.find { it.isPinned }
        assertNotNull(secureCoreItemBefore)
        assertNotNull(secureCoreItemAfter)
        assertTrue(secureCoreItemBefore.isOnline)
        assertFalse(secureCoreItemAfter.isOnline)
    }

    private suspend fun insertRecents() {
        with(recentsDao) {
            // Pinned items:
            insertOrUpdateForConnection(ConnectIntentSecureCore, 4)
            getRecentsList().first().forEach { pin(it.id, 100) }

            // Most recent first for the code to match order in UI.
            insertOrUpdateForConnection(ConnectIntentFastest, 3)
            insertOrUpdateForConnection(ConnectIntentSweden, 2)
            insertOrUpdateForConnection(ConnectIntentIceland, 1)
        }
    }

    companion object {
        val ConnectIntentSecureCore = ConnectIntent.SecureCore(CountryId("PL"), CountryId.switzerland)
        val ConnectIntentFastest = ConnectIntent.FastestInCountry(CountryId.fastest, setOf(ServerFeature.P2P))
        val ConnectIntentSweden = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        val ConnectIntentIceland = ConnectIntent.FastestInCountry(CountryId.iceland, emptySet())
        val ConnectIntentSwitzerland = ConnectIntent.FastestInCountry(CountryId.switzerland, emptySet())

        // ConnectIntentViewStates for the constants above:
        val ConnectIntentViewSecureCore = ConnectIntentViewState(
            ConnectIntentSecureCore.exitCountry,
            ConnectIntentSecureCore.entryCountry,
            true,
            ConnectIntentSecondaryLabel.SecureCore(null, ConnectIntentSecureCore.entryCountry),
            emptySet()
        )
        val ConnectIntentViewFastest = createViewStateForFastestInCountry(ConnectIntentFastest)
        val ConnectIntentViewSweden = createViewStateForFastestInCountry(ConnectIntentSweden)
        val ConnectIntentViewIceland = createViewStateForFastestInCountry(ConnectIntentIceland)
        val ConnectIntentViewSwitzerland = createViewStateForFastestInCountry(ConnectIntentSwitzerland)

        private fun createViewStateForFastestInCountry(intent: ConnectIntent.FastestInCountry) =
            ConnectIntentViewState(intent.country, null, false, null, intent.features)
    }
}
