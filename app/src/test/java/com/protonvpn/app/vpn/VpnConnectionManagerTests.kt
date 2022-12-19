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
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
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
import com.protonvpn.test.shared.TestVpnUser
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.MockedServers
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.pauseDispatcher
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.session.SessionId
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

    private lateinit var userData: UserData
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var serverManager: ServerManager

    private lateinit var mockBackendSelfState: MutableLiveData<VpnState>

    private lateinit var testDispatcher: TestCoroutineDispatcher
    private lateinit var testScope: TestCoroutineScope

    private var time: Long = 1000
    private val clock = { time }

    private val vpnUser = TestVpnUser.create(maxTier = 2)
    private val connectionParams = ConnectionParams(
        Profile.getTempProfile(ServerWrapper.makePreBakedFastest()),
        MockedServers.server,
        MockedServers.server.connectingDomains.first(),
        VpnProtocol.WireGuard
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        time = 1000

        testDispatcher = TestCoroutineDispatcher()
        testScope = TestCoroutineScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)
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

        Storage.setPreferences(MockSharedPreference())
        userData = UserData.create()
        vpnStateMonitor = VpnStateMonitor()
        val supportsProtocol = SupportsProtocol(appConfig)
        serverManager = ServerManager(userData, mockCurrentUser, clock, supportsProtocol, mockk(relaxed = true)).apply {
            setServers(MockedServers.serverList, null)
        }

        vpnConnectionManager = VpnConnectionManager(
            permissionDelegate = mockk(relaxed = true),
            userData = userData,
            appConfig = appConfig,
            backendProvider = mockBackendProvider,
            networkManager = mockNetworkManager,
            vpnErrorHandler = mockVpnErrorHandler,
            vpnStateMonitor = vpnStateMonitor,
            vpnBackgroundUiDelegate = mockk(),
            serverManager = serverManager,
            certificateRepository = mockk(),
            currentVpnServiceProvider = mockk(relaxed = true),
            currentUser = mockCurrentUser,
            scope = testScope,
            now = clock,
            powerManager = mockPowerManager,
            supportsProtocol = supportsProtocol
        )
    }

    @Test
    fun `when server is selected and protocol connection starts wake lock is released`() = testScope.runBlockingTest {
        coEvery { mockBackendProvider.prepareConnection(any(), any(), any()) } answers {
            PrepareResult(mockBackend, connectionParams)
        }

        vpnConnectionManager.connect(
            mockVpnUiDelegate, Profile.getTempProfile(ServerWrapper.makePreBakedFastest()), "Test"
        )
        coVerify { mockBackend.connect(connectionParams) }
        verify(exactly = 1) { mockWakeLock.acquire(any()) }
        verify(exactly = 1) { mockWakeLock.release() }
    }

    @Test
    fun `when connection is aborted wake lock is released`() = testScope.runBlockingTest {
        coEvery { mockBackendProvider.prepareConnection(any(), any(), any()) } answers {
            vpnConnectionManager.disconnect("Test")
            PrepareResult(mockBackend, connectionParams)
        }

        vpnConnectionManager.connect(
            mockVpnUiDelegate, Profile.getTempProfile(ServerWrapper.makePreBakedFastest()), "Test"
        )
        verify(exactly = 1) { mockWakeLock.acquire(any()) }
        verify(exactly = 1) { mockWakeLock.release() }
    }

    @Test
    fun `when fallback finishes wake lock is released`() = testScope.runBlockingTest {
        // No servers triggers fallback connections
        serverManager.setServers(emptyList(), null)

        vpnConnectionManager.connect(
            mockVpnUiDelegate, Profile.getTempProfile(ServerWrapper.makePreBakedFastest()), "Test"
        )
        advanceUntilIdle()
        // Wake lock is acquired twice, once to prepare connection, second time for fallback logic.
        verify(exactly = 2) { mockWakeLock.acquire(any()) }
        verify(exactly = 2) { mockWakeLock.release() }
    }

    @Test
    fun `when error is reported during fallback then ongoing fallback is not overridden`() = testScope.runBlockingTest {
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
            mockVpnUiDelegate, Profile.getTempProfile(ServerWrapper.makePreBakedFastest()), "Test"
        )

        pauseDispatcher()
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
}
