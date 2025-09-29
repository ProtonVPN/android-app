/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.app.vpn

import android.os.PowerManager
import android.os.PowerManager.PARTIAL_WAKE_LOCK
import androidx.activity.ComponentActivity
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.appconfig.GetFeatureFlags
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.servers.Server
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.netshield.NetShieldStats
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.settings.data.ApplyEffectiveUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.PrepareResult
import com.protonvpn.android.vpn.VpnBackend
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnConnectionErrorHandler
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.mocks.FakeGetProfileById
import com.protonvpn.mocks.FakeSettingsFeatureFlagsFlow
import com.protonvpn.mocks.FakeVpnPermissionDelegate
import com.protonvpn.mocks.FakeVpnUiDelegate
import com.protonvpn.mocks.TestProtonLogger
import com.protonvpn.mocks.createInMemoryServerManager
import com.protonvpn.test.shared.MockNetworkManager
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createServer
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class GuestHoleVpnConnectionManagerTests {

    @MockK
    private lateinit var mockBackend: VpnBackend
    @MockK
    private lateinit var mockBackendProvider: VpnBackendProvider
    @MockK
    private lateinit var mockPowerManager: PowerManager
    @RelaxedMockK
    private lateinit var mockWakeLock: PowerManager.WakeLock
    @MockK
    private lateinit var mockVpnErrorHandler: VpnConnectionErrorHandler

    private lateinit var guestHole: GuestHole
    private lateinit var serverManager: ServerManager
    private lateinit var testScope: TestScope

    private lateinit var vpnConnectionManager: VpnConnectionManager
    private lateinit var vpnStateMonitor: VpnStateMonitor

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        Storage.setPreferences(MockSharedPreference())
        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        val bgScope = testScope.backgroundScope
        val clock = testScope::currentTime

        val currentUser = CurrentUser(TestCurrentUserProvider(TestUser.freeUser.vpnUser))
        val rawSettingsFlow = flowOf(LocalUserSettings.Default)
        val foregroundActivityTracker = ForegroundActivityTracker(bgScope, flowOf(mockk<ComponentActivity>()))
        val supportsProtocol = SupportsProtocol(createGetSmartProtocols())

        every { mockWakeLock.isHeld } returns true
        every { mockPowerManager.newWakeLock(PARTIAL_WAKE_LOCK, any()) } returns mockWakeLock

        every { mockVpnErrorHandler.switchConnectionFlow } returns MutableSharedFlow()

        val networkManager = MockNetworkManager()
        serverManager = createInMemoryServerManager(
            testScope,
            TestDispatcherProvider(testDispatcher),
            supportsProtocol,
            emptyList()
        )
        val serverManager2 = ServerManager2(serverManager, supportsProtocol)
        val fakeVpnPermissionDelegate = FakeVpnPermissionDelegate()

        vpnStateMonitor = VpnStateMonitor()
        val vpnStatusUiProvider = VpnStatusProviderUI(bgScope, vpnStateMonitor)
        val settingsForConnection = SettingsForConnection(
            rawSettingsFlow = rawSettingsFlow,
            getProfileById = FakeGetProfileById(),
            applyEffectiveUserSettings = ApplyEffectiveUserSettings(
                mainScope = testScope.backgroundScope,
                currentUser = currentUser,
                isTv = mockk(relaxed = true),
                flags = FakeSettingsFeatureFlagsFlow(),
            ),
            vpnStatusProviderUI = vpnStatusUiProvider,
        )
        vpnConnectionManager = VpnConnectionManager(
            permissionDelegate = fakeVpnPermissionDelegate,
            getFeatureFlags = GetFeatureFlags(MutableStateFlow(FeatureFlags())),
            settingsForConnection = settingsForConnection,
            backendProvider = mockBackendProvider,
            networkManager = networkManager,
            vpnErrorHandler = mockVpnErrorHandler,
            vpnStateMonitor = vpnStateMonitor,
            vpnBackgroundUiDelegate = mockk(), // Create a fake?
            serverManager = serverManager2,
            certificateRepository = mockk(),
            scope = bgScope,
            now = clock,
            currentVpnServiceProvider = mockk(relaxed = true),
            currentUser = currentUser,
            supportsProtocol = supportsProtocol,
            powerManager = mockPowerManager,
            vpnConnectionTelemetry = mockk(relaxed = true),
            autoLoginManager = mockk(relaxed = true),
            vpnErrorAndFallbackObservability = mockk(relaxed = true),
        )
        guestHole = GuestHole(
            scope = bgScope,
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            serverManager = { serverManager },
            vpnMonitor = vpnStateMonitor,
            vpnPermissionDelegate = fakeVpnPermissionDelegate,
            vpnConnectionManager = { vpnConnectionManager },
            notificationHelper = mockk(relaxed = true),
            foregroundActivityTracker = foregroundActivityTracker,
            appFeaturesPrefs = AppFeaturesPrefs(MockSharedPreferencesProvider()),
            guestHoleSuppressor = mockk(relaxed = true),
        )

        // Can't use runBlocking because ServerManager uses testScope's StandardTestDispatcher, so it would deadlock.
        testScope.launch {
            serverManager.setServers(listOf(createServer("serverId", tier = 0)), null, null)
            serverManager.setBuiltInGuestHoleServersForTesting(listOf(createServer("ghServerId", tier = 0)))
        }
        testScope.runCurrent()

        ProtonLogger.setLogger(TestProtonLogger())
    }

    @Test
    fun `connecting to VPN while GH is active cancels GH`() = testScope.runTest(timeout = 5.seconds) {
        setupMockBackend(
            mockBackendProvider,
            mockBackend,
            connectAnswer = { _, selfState ->
                selfState.value = VpnState.Connecting
                yield()
                selfState.value = VpnState.Connected
            },
            disconnectAnswer = { selfState -> selfState.value = VpnState.Disabled }
        )

        val unblockFinish = CompletableDeferred<Unit>()
        guestHole.onAlternativesUnblock {
            vpnConnectionManager.connect(FakeVpnUiDelegate(), ConnectIntent.Fastest, ConnectTrigger.Auto("test"))
            unblockFinish.await()
        }

        runCurrent()
        assertEquals(VpnState.Connected, vpnStateMonitor.state)
        assertEquals(ConnectIntent.Fastest, vpnStateMonitor.connectionIntent)
    }

    @Test
    fun `GH finishing as VPN connection is being prepared doesn't cancel the VPN connection`() = testScope.runTest {
        val prepareDelayMs = 200L
        val unblockCallDurationMs = 100L
        setupMockBackend(
            mockBackendProvider,
            mockBackend,
            prepareDelay = prepareDelayMs,
            connectAnswer = { _, selfState -> yield(); selfState.value = VpnState.Connected },
            disconnectAnswer = { selfState -> selfState.value = VpnState.Disabled }
        )

        launch {
            guestHole.onAlternativesUnblock {
                delay(unblockCallDurationMs)
            }
        }
        advanceTimeBy(prepareDelayMs)
        advanceTimeBy(GuestHole.EXECUTE_CALL_DELAY_MS)
        advanceTimeBy(unblockCallDurationMs - 10)
        // onAlternativesUnblock finishes before the new connection is prepared.
        vpnConnectionManager.connect(FakeVpnUiDelegate(), ConnectIntent.Fastest, ConnectTrigger.Auto("test"))

        advanceTimeBy(10_000)
        assertEquals(VpnState.Connected, vpnStateMonitor.state)
        assertEquals(ConnectIntent.Fastest, vpnStateMonitor.connectionIntent)
    }

    private fun setupMockBackend(
        mockProvider: VpnBackendProvider,
        mock: VpnBackend,
        protocol: VpnProtocol = VpnProtocol.WireGuard,
        prepareDelay: Long = 0L,
        connectAnswer: suspend (params: ConnectionParams, state: MutableStateFlow<VpnState>) -> Unit,
        disconnectAnswer: suspend (MutableStateFlow<VpnState>) -> Unit,
    ) {
        coEvery { mockProvider.prepareConnection(any(), any(), any(), any()) } coAnswers {
            val connectIntent = secondArg<AnyConnectIntent>()
            val server = thirdArg<Server>()
            val connectionParams = ConnectionParams(connectIntent, server, server.connectingDomains.first(), protocol)
            delay(prepareDelay)
            PrepareResult(mock, connectionParams)
        }

        val backendStateFlow: MutableStateFlow<VpnState> = MutableStateFlow(VpnState.Disabled)
        every { mock.vpnProtocol } returns protocol
        every { mock.setSelfState(any()) } answers { backendStateFlow.value = firstArg() }
        every { mock.selfStateFlow } returns backendStateFlow
        every { mock.selfState } answers { backendStateFlow.value }
        every { mock.internalVpnProtocolState } answers { backendStateFlow }
        every { mock.lastKnownExitIp } returns MutableStateFlow(null)
        every { mock.netShieldStatsFlow } returns MutableStateFlow(NetShieldStats())

        coEvery { mock.connect(any()) } coAnswers { connectAnswer(firstArg(), backendStateFlow) }
        coEvery { mock.disconnect() } coAnswers { disconnectAnswer(backendStateFlow) }
    }
}
