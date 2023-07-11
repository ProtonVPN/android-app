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
import androidx.lifecycle.MutableLiveData
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.telemetry.VpnConnectionTelemetry
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
import com.protonvpn.android.vpn.VpnFallbackResult
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnUiDelegate
import com.protonvpn.app.userstorage.createDummyProfilesManager
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestVpnUser
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createInMemoryServersStore
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
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
    private lateinit var mockVpnUiDelegate: VpnUiDelegate
    @MockK
    private lateinit var appConfig: AppConfig
    @RelaxedMockK
    private lateinit var mockVpnConnectionTelemetry: VpnConnectionTelemetry

    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var serverManager: ServerManager

    private lateinit var mockBackendSelfState: MutableLiveData<VpnState>

    private lateinit var testScope: TestScope

    private val vpnUser = TestVpnUser.create(maxTier = 2)
    private val connectionParams = ConnectionParams(
        Profile.getTempProfile(ServerWrapper.makePreBakedFastest()),
        MockedServers.server,
        MockedServers.server.connectingDomains.first(),
        VpnProtocol.WireGuard
    )
    private val trigger = ConnectTrigger.Auto("test")

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        val testScheduler = TestCoroutineScheduler()
        testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val clock = testScheduler::currentTime

        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        mockBackendSelfState = MutableLiveData()

        coEvery { mockCurrentUser.sessionId() } returns SessionId("session id")
        coEvery { mockCurrentUser.vpnUser() } returns vpnUser

        every { mockWakeLock.isHeld } returns true
        every { mockPowerManager.newWakeLock(PARTIAL_WAKE_LOCK, any()) } returns mockWakeLock
        every { appConfig.getFeatureFlags() } returns FeatureFlags()
        every { appConfig.getSmartProtocols() } returns ProtocolSelection.REAL_PROTOCOLS
        every { mockNetworkManager.isConnectedToNetwork() } returns true
        every { mockBackend.vpnProtocol } returns connectionParams.protocolSelection!!.vpn
        every { mockBackend.selfStateObservable } returns mockBackendSelfState
        every { mockBackend.lastKnownExitIp } returns MutableStateFlow(null)
        every { mockVpnUiDelegate.askForPermissions(any(), any(), any()) } answers {
            arg<() -> Unit>(2).invoke()
        }
        every { mockVpnErrorHandler.switchConnectionFlow } returns MutableSharedFlow()
        every { mockVpnUiDelegate.shouldSkipAccessRestrictions() } returns false

        val userSettings = EffectiveCurrentUserSettings(testScope.backgroundScope, flowOf(LocalUserSettings.Default))
        val userSettingsCached = EffectiveCurrentUserSettingsCached(MutableStateFlow(LocalUserSettings.Default))

        Storage.setPreferences(MockSharedPreference())
        vpnStateMonitor = VpnStateMonitor()
        val supportsProtocol = SupportsProtocol(createGetSmartProtocols())
        val profileManager = createDummyProfilesManager()
        serverManager = ServerManager(userSettingsCached, mockCurrentUser, clock, supportsProtocol, createInMemoryServersStore(), profileManager)
        serverManager.setServers(MockedServers.serverList, null)

        vpnConnectionManager = VpnConnectionManager(
            permissionDelegate = mockk(relaxed = true),
            userSettings = userSettings,
            appConfig = appConfig,
            backendProvider = mockBackendProvider,
            networkManager = mockNetworkManager,
            vpnErrorHandler = mockVpnErrorHandler,
            vpnStateMonitor = vpnStateMonitor,
            vpnBackgroundUiDelegate = mockk(relaxed = true),
            serverManager = serverManager,
            certificateRepository = mockk(),
            currentVpnServiceProvider = mockk(relaxed = true),
            currentUser = mockCurrentUser,
            scope = testScope.backgroundScope,
            now = clock,
            powerManager = mockPowerManager,
            supportsProtocol = supportsProtocol,
            vpnConnectionTelemetry = mockVpnConnectionTelemetry
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

        vpnConnectionManager.connect(
            mockVpnUiDelegate, Profile.getTempProfile(ServerWrapper.makePreBakedFastest()), trigger
        )
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

        vpnConnectionManager.connect(
            mockVpnUiDelegate, Profile.getTempProfile(ServerWrapper.makePreBakedFastest()), trigger
        )
        verify(exactly = 1) { mockWakeLock.acquire(any()) }
        verify(exactly = 1) { mockWakeLock.release() }
    }

    @Test
    fun `when fallback finishes wake lock is released`() = testScope.runTest {
        // No servers triggers fallback connections
        serverManager.setServers(emptyList(), null)
        coEvery { mockVpnErrorHandler.onServerNotAvailable(any()) } returns null

        vpnConnectionManager.connect(
            mockVpnUiDelegate, Profile.getTempProfile(ServerWrapper.makePreBakedFastest()), trigger
        )
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
            VpnFallbackResult.Error(ErrorType.UNREACHABLE) // Needed for compilation, should not be reached.
        }

        vpnConnectionManager.connect(
            mockVpnUiDelegate, Profile.getTempProfile(ServerWrapper.makePreBakedFastest()), trigger
        )

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

        vpnConnectionManager.connect(
            mockVpnUiDelegate, Profile.getTempProfile(ServerWrapper.makePreBakedFastest()), trigger
        )
        coVerify { mockBackend.connect(connectionParams) }

        verify(exactly = 1) { mockVpnConnectionTelemetry.onConnectionStart(trigger) }
    }

    @Test
    fun `when reconnecting VpnConnectionTelemetry onDisconnectionTrigger is called with previous connections params`() =
        testScope.runTest {
            coEvery { mockBackendProvider.prepareConnection(any(), any(), any()) } answers {
                PrepareResult(mockBackend, connectionParams)
            }
            vpnConnectionManager.connect(
                mockVpnUiDelegate, Profile.getTempProfile(ServerWrapper.makePreBakedFastest()), trigger
            )

            val newConnectionParams = ConnectionParams(
                Profile.getTempProfile(ServerWrapper.makePreBakedFastest()),
                MockedServers.server,
                MockedServers.server.connectingDomains.first(),
                VpnProtocol.OpenVPN
            )
            coEvery { mockBackendProvider.prepareConnection(any(), any(), any()) } answers {
                PrepareResult(mockBackend, newConnectionParams)
            }
            vpnConnectionManager.connect(
                mockVpnUiDelegate, Profile.getTempProfile(ServerWrapper.makePreBakedFastest()), trigger
            )

            verify(exactly = 1) {
                mockVpnConnectionTelemetry.onDisconnectionTrigger(DisconnectTrigger.NewConnection, connectionParams)
            }
        }

    @Test
    fun `when VPN service is destroyed while connected then report disconnection`() = testScope.runTest {
        coEvery { mockBackendProvider.prepareConnection(any(), any(), any()) } answers {
            PrepareResult(mockBackend, connectionParams)
        }
        vpnConnectionManager.connect(
            mockVpnUiDelegate, Profile.getTempProfile(ServerWrapper.makePreBakedFastest()), trigger
        )
        vpnConnectionManager.onVpnServiceDestroyed()
        verify(exactly = 1) {
            mockVpnConnectionTelemetry.onDisconnectionTrigger(DisconnectTrigger.ServiceDestroyed, connectionParams)
        }
    }

    @Test
    fun `when reconnecting with same params then report reconnection`() = testScope.runTest {
        coEvery { mockBackendProvider.prepareConnection(any(), any(), any()) } answers {
            PrepareResult(mockBackend, connectionParams)
        }
        vpnConnectionManager.connect(
            mockVpnUiDelegate, Profile.getTempProfile(ServerWrapper.makePreBakedFastest()), trigger
        )
        vpnConnectionManager.reconnectWithCurrentParams(mockVpnUiDelegate)
        verifyOrder {
            mockVpnConnectionTelemetry.onDisconnectionTrigger(ofType<DisconnectTrigger.Reconnect>(), connectionParams)
            mockVpnConnectionTelemetry.onConnectionStart(ConnectTrigger.Reconnect)
        }
    }
}
