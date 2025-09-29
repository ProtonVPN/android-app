/*
 * Copyright (c) 2022. Proton AG
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
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.appconfig.GetFeatureFlags
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.servers.Server
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.settings.data.ApplyEffectiveUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.telemetry.VpnConnectionTelemetry
import com.protonvpn.android.ui.vpn.VpnBackgroundUiDelegate
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.PrepareResult
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnBackend
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnConnectionErrorHandler
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnConnectionManager.Companion.STORAGE_KEY_STATE
import com.protonvpn.android.vpn.VpnFallbackResult
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import com.protonvpn.mocks.FakeGetProfileById
import com.protonvpn.mocks.FakeSettingsFeatureFlagsFlow
import com.protonvpn.mocks.FakeVpnUiDelegate
import com.protonvpn.mocks.createInMemoryServerManager
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestVpnUser
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createServer
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.session.SessionId
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// See also VpnConnectionTests in androidTests.
@OptIn(ExperimentalCoroutinesApi::class)
class VpnConnectionManagerTests {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    private lateinit var vpnConnectionManager: VpnConnectionManager

    @MockK
    private lateinit var mockNetworkManager: NetworkManager
    @RelaxedMockK
    private lateinit var mockVpnErrorHandler: VpnConnectionErrorHandler
    @MockK
    private lateinit var mockPowerManager: PowerManager
    @RelaxedMockK
    private lateinit var mockWakeLock: PowerManager.WakeLock
    @MockK
    private lateinit var mockBackendProvider: VpnBackendProvider
    @MockK
    private lateinit var mockCurrentUser: CurrentUser
    @RelaxedMockK
    private lateinit var mockBackend: VpnBackend
    @MockK
    private lateinit var mockVpnBackgroundUiDelegate: VpnBackgroundUiDelegate
    @MockK
    private lateinit var appConfig: AppConfig
    @RelaxedMockK
    private lateinit var mockVpnConnectionTelemetry: VpnConnectionTelemetry

    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var vpnStatusProviderUI: VpnStatusProviderUI
    private lateinit var serverManager: ServerManager

    private lateinit var mockBackendSelfState: MutableStateFlow<VpnState>

    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testScope: TestScope
    private lateinit var supportsProtocol: SupportsProtocol

    private val clock get() = testScheduler::currentTime

    private val fakeVpnUiDelegate: VpnUiDelegate = FakeVpnUiDelegate()
    private val vpnUser = TestVpnUser.create(maxTier = 2)
    private val connectionParams = ConnectionParams(
        ConnectIntent.Default,
        MockedServers.server,
        MockedServers.server.connectingDomains.first(),
        VpnProtocol.WireGuard
    )
    private val trigger = ConnectTrigger.Auto("test")

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        testScheduler = TestCoroutineScheduler()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        testScope = TestScope(testDispatcher)

        Dispatchers.setMain(testDispatcher)
        mockBackendSelfState = MutableStateFlow(VpnState.Disabled)

        coEvery { mockCurrentUser.sessionId() } returns SessionId("session id")
        coEvery { mockCurrentUser.vpnUser() } returns vpnUser

        every { mockWakeLock.isHeld } returns true
        every { mockPowerManager.newWakeLock(PARTIAL_WAKE_LOCK, any()) } returns mockWakeLock
        every { appConfig.getFeatureFlags() } returns FeatureFlags()
        every { appConfig.getSmartProtocols() } returns ProtocolSelection.REAL_PROTOCOLS
        every { mockNetworkManager.isConnectedToNetwork() } returns true
        every { mockBackend.vpnProtocol } returns connectionParams.protocolSelection!!.vpn
        every { mockBackend.selfStateFlow } returns mockBackendSelfState
        every { mockBackend.internalVpnProtocolState } returns mockBackendSelfState
        every { mockBackend.lastKnownExitIp } returns MutableStateFlow(null)
        every { mockVpnErrorHandler.switchConnectionFlow } returns MutableSharedFlow()
        every { mockVpnBackgroundUiDelegate.shouldSkipAccessRestrictions() } returns false
        every { mockVpnBackgroundUiDelegate.showInfoNotification(any()) } just Runs
        every { mockVpnBackgroundUiDelegate.askForPermissions(any(), any(), any()) } answers {
            arg<() -> Unit>(2).invoke()
        }

        Storage.setPreferences(MockSharedPreference())
        vpnStateMonitor = VpnStateMonitor()
        vpnStatusProviderUI = VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor)
        supportsProtocol = SupportsProtocol(createGetSmartProtocols())
        serverManager = createInMemoryServerManager(
            testScope,
            TestDispatcherProvider(testDispatcher),
            supportsProtocol,
            MockedServers.serverList
        )

        createManager()
    }

    private fun createManager() {
        val serverManager2 = ServerManager2(serverManager, supportsProtocol)
        val settingsForConnection = SettingsForConnection(
            rawSettingsFlow = flowOf(LocalUserSettings.Default),
            getProfileById = FakeGetProfileById(),
            applyEffectiveUserSettings = ApplyEffectiveUserSettings(
                mainScope = testScope.backgroundScope,
                currentUser = mockCurrentUser,
                isTv = mockk(relaxed = true),
                flags = FakeSettingsFeatureFlagsFlow(),
            ),
            vpnStatusProviderUI = vpnStatusProviderUI
        )
        vpnConnectionManager = VpnConnectionManager(
            permissionDelegate = mockk(relaxed = true),
            settingsForConnection = settingsForConnection,
            getFeatureFlags = GetFeatureFlags(MutableStateFlow(FeatureFlags())),
            backendProvider = mockBackendProvider,
            networkManager = mockNetworkManager,
            vpnErrorHandler = mockVpnErrorHandler,
            vpnStateMonitor = vpnStateMonitor,
            vpnBackgroundUiDelegate = mockVpnBackgroundUiDelegate,
            serverManager = serverManager2,
            certificateRepository = mockk(),
            currentVpnServiceProvider = mockk(relaxed = true),
            currentUser = mockCurrentUser,
            scope = testScope.backgroundScope,
            now = clock,
            powerManager = mockPowerManager,
            supportsProtocol = supportsProtocol,
            vpnConnectionTelemetry = mockVpnConnectionTelemetry,
            autoLoginManager = mockk(relaxed = true),
            vpnErrorAndFallbackObservability = mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when server is selected and protocol connection starts wake lock is released`() = testScope.runTest {
        coEvery { mockBackendProvider.prepareConnection(any(), any(), any()) } answers {
            PrepareResult(mockBackend, connectionParams)
        }

        vpnConnectionManager.connect(fakeVpnUiDelegate, ConnectIntent.Default, trigger)
        coVerify { mockBackend.connect(connectionParams) }
        verify(exactly = 1) { mockWakeLock.acquire(any()) }
        verify(exactly = 1) { mockWakeLock.release() }
    }

    @Test
    fun `when connection is aborted wake lock is released`() = testScope.runTest {
        coEvery { mockBackendProvider.prepareConnection(any(), any(), any()) } answers {
            vpnConnectionManager.disconnect(DisconnectTrigger.Test())
            PrepareResult(mockBackend, connectionParams)
        }

        vpnConnectionManager.connect(fakeVpnUiDelegate, ConnectIntent.Default, trigger)
        verify(exactly = 1) { mockWakeLock.acquire(any()) }
        verify(exactly = 1) { mockWakeLock.release() }
    }

    @Test
    fun `when fallback finishes wake lock is released`() = testScope.runTest {
        // No servers triggers fallback connections
        serverManager.setServers(emptyList(), null, null)
        coEvery { mockVpnErrorHandler.onServerNotAvailable(any()) } returns null

        vpnConnectionManager.connect(fakeVpnUiDelegate, ConnectIntent.Default, trigger)
        advanceUntilIdle()
        // Wake lock is acquired twice, once to prepare connection, second time for fallback logic.
        verify(exactly = 2) { mockWakeLock.acquire(any()) }
        verify(exactly = 2) { mockWakeLock.release() }
    }

    @Test
    fun `when error is reported during fallback then ongoing fallback is not overridden`() = testScope.runTest {
        coEvery { mockBackendProvider.prepareConnection(any(), any(), any()) } answers {
            PrepareResult(mockBackend, connectionParams)
        }

        val fallbackDurationMs = 1000L
        coEvery { mockVpnErrorHandler.onUnreachableError(any()) } coAnswers {
            delay(fallbackDurationMs)
            assertTrue(false)
            VpnFallbackResult.Error(connectionParams, ErrorType.UNREACHABLE, reason = null) // Needed for compilation, should not be reached.
        }

        vpnConnectionManager.connect(fakeVpnUiDelegate, ConnectIntent.Default, trigger)

        with(mockBackendSelfState) {
            // Triggers fallback that calls onUnreachableError.
            value = VpnState.Error(ErrorType.UNREACHABLE_INTERNAL, isFinal = false)
            advanceTimeBy(fallbackDurationMs / 2)
            coVerify { mockVpnErrorHandler.onUnreachableError(any()) }

            // Tries to start a second fallback handleUnrecoverableError.
            value = VpnState.Error(ErrorType.UNREACHABLE, isFinal = false)
            // Cancels the current ongoing fallback.
            value = VpnState.Connected
        }
        advanceTimeBy(fallbackDurationMs)
    }

    @Test
    fun `when connecting VpnConnectionTelemetry is notified`() = testScope.runTest {
        coEvery { mockBackendProvider.prepareConnection(any(), any(), any()) } answers {
            PrepareResult(mockBackend, connectionParams)
        }

        vpnConnectionManager.connect(fakeVpnUiDelegate, ConnectIntent.Default, trigger)
        coVerify { mockBackend.connect(connectionParams) }

        verify(exactly = 1) { mockVpnConnectionTelemetry.onConnectionStart(trigger) }
    }

    @Test
    fun `when reconnecting VpnConnectionTelemetry onDisconnectionTrigger is called with previous connections params`() =
        testScope.runTest {
            coEvery { mockBackendProvider.prepareConnection(any(), any(), any()) } answers {
                PrepareResult(mockBackend, connectionParams)
            }
            vpnConnectionManager.connect(fakeVpnUiDelegate, ConnectIntent.Default, trigger)
            val newConnectionParams = ConnectionParams(
                ConnectIntent.Default,
                MockedServers.server,
                MockedServers.server.connectingDomains.first(),
                VpnProtocol.OpenVPN
            )
            coEvery { mockBackendProvider.prepareConnection(any(), any(), any()) } answers {
                PrepareResult(mockBackend, newConnectionParams)
            }
            vpnConnectionManager.connect(fakeVpnUiDelegate, ConnectIntent.Default, trigger)

            verify(exactly = 1) {
                mockVpnConnectionTelemetry.onDisconnectionTrigger(DisconnectTrigger.NewConnection, connectionParams)
            }
        }

    @Test
    fun `when VPN service is destroyed while connected then report disconnection`() = testScope.runTest {
        coEvery { mockBackendProvider.prepareConnection(any(), any(), any()) } answers {
            PrepareResult(mockBackend, connectionParams)
        }
        vpnConnectionManager.connect(fakeVpnUiDelegate, ConnectIntent.Default, trigger)
        vpnConnectionManager.onVpnServiceDestroyed(connectionParamsUuid = connectionParams.uuid)
        verify(exactly = 1) {
            mockVpnConnectionTelemetry.onDisconnectionTrigger(DisconnectTrigger.ServiceDestroyed, connectionParams)
        }
    }

    @Test
    fun `when reconnecting with same params then report reconnection`() = testScope.runTest {
        coEvery { mockBackendProvider.prepareConnection(any(), any(), any()) } answers {
            PrepareResult(mockBackend, connectionParams)
        }
        vpnConnectionManager.connect(fakeVpnUiDelegate, ConnectIntent.Default, trigger)
        vpnConnectionManager.reconnect(triggerAction = "test", fakeVpnUiDelegate)
        verifyOrder {
            mockVpnConnectionTelemetry.onConnectionStart(trigger)
            // Reconnect and scanning starts before disconnecting.
            mockVpnConnectionTelemetry.onConnectionStart(ConnectTrigger.Reconnect)
            mockVpnConnectionTelemetry.onDisconnectionTrigger(ofType<DisconnectTrigger.Reconnect>(), connectionParams)
        }
    }

    @Test
    fun `connection is initiated after process restore`() = testScope.runTest {
        Storage.saveString(STORAGE_KEY_STATE, VpnState.Connected.name)
        createManager() // Recreate manager to make sure it doesn't override previously set state
        advanceUntilIdle()

        coEvery { mockBackendProvider.prepareConnection(any(), any(), any()) } answers {
            PrepareResult(mockBackend, connectionParams)
        }

        vpnConnectionManager.onRestoreProcess(ConnectIntent.Default, "process restore")

        coVerify { mockBackend.connect(connectionParams) }
    }

    @Test
    fun `when connecting to different server pinging happens before disconnecting`() =
        testScope.runTest {
            val server1 = MockedServers.serverList[0]
            val server2 = MockedServers.serverList[1]
            val intent1 = ConnectIntent.fromServer(server1, emptySet())
            val intent2 = ConnectIntent.fromServer(server2, emptySet())
            coEvery { mockBackendProvider.prepareConnection(any(), any(), any()) } answers {
                val intent = arg<ConnectIntent>(1)
                val server = arg<Server>(2)
                PrepareResult(mockBackend, ConnectionParams(intent, server, server.connectingDomains.first(), VpnProtocol.WireGuard))
            }

            vpnConnectionManager.connect(fakeVpnUiDelegate, intent1, trigger)
            mockBackendSelfState.value = VpnState.Connected
            advanceUntilIdle()

            vpnConnectionManager.connect(fakeVpnUiDelegate, intent2, trigger)
            mockBackendSelfState.value = VpnState.Connected
            advanceUntilIdle()

            coVerifyOrder {
                mockBackend.connect(any())
                mockBackendProvider.prepareConnection(any(), intent2, server2, any())
                mockBackend.disconnect()
                mockBackend.connect(any())
            }
        }

    @Test
    fun `when user connects while Guest Hole is connecting then Guest Hole is aborted`() = testScope.runTest {
        val server = createServer("serverId")
        serverManager.setGuestHoleServers(listOf(server))
        val ghIntent = AnyConnectIntent.GuestHole(server.serverId)
        val ghConnectionParams =
            ConnectionParams(ghIntent, server, server.connectingDomains.first(), VpnProtocol.WireGuard)
        val regularIntent = connectionParams.connectIntent
        coEvery { mockBackendProvider.prepareConnection(any(), ghIntent, any()) } returns
            PrepareResult(mockBackend, ghConnectionParams)
        coEvery { mockBackendProvider.prepareConnection(any(), regularIntent, any()) } returns
            PrepareResult(mockBackend, connectionParams)

        vpnConnectionManager.connect(fakeVpnUiDelegate, ghIntent, trigger)
        mockBackendSelfState.value = VpnState.Connecting

        vpnStateMonitor.onDisconnectedByReconnection.test {
            vpnConnectionManager.connect(fakeVpnUiDelegate, ConnectIntent.Fastest, trigger)

            awaitItem()
        }
    }

    @Test
    fun `when reconnecting to a different Guest Hole don't trigger disconnection`() = testScope.runTest {
        val server = createServer("serverId")
        serverManager.setGuestHoleServers(listOf(server))
        val ghIntent = AnyConnectIntent.GuestHole(server.serverId)
        val ghConnectionParams =
            ConnectionParams(ghIntent, server, server.connectingDomains.first(), VpnProtocol.WireGuard)
        coEvery { mockBackendProvider.prepareConnection(any(), any(), any()) } returns
            PrepareResult(mockBackend, ghConnectionParams)

        vpnConnectionManager.connect(fakeVpnUiDelegate, ghIntent, trigger)
        mockBackendSelfState.value = VpnState.Connecting

        vpnStateMonitor.onDisconnectedByReconnection.test {
            vpnConnectionManager.connect(fakeVpnUiDelegate, ghIntent, trigger)

            expectNoEvents()
        }
    }
}
