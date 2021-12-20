/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.tests.vpn

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.SmartProtocolConfig
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.AgentConnectionInterface
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.ProtonVpnBackendProvider
import com.protonvpn.android.vpn.SwitchServerReason
import com.protonvpn.android.vpn.VpnConnectionErrorHandler
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnFallbackResult
import com.protonvpn.android.vpn.VpnPermissionDelegate
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.di.MockNetworkManager
import com.protonvpn.di.MockVpnConnectionManager
import com.protonvpn.mocks.MockVpnBackend
import com.protonvpn.test.shared.MockedServers
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.yield
import me.proton.core.network.domain.NetworkStatus
import me.proton.core.network.domain.session.SessionId
import com.proton.gopenpgp.localAgent.LocalAgent
import com.protonvpn.android.auth.usecase.CurrentUser
import kotlinx.coroutines.runBlocking
import me.proton.core.network.domain.ApiResult
import me.proton.core.test.kotlin.TestDispatcherProvider
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
// These tests use mocking of final classes that's not available on API < 28
@SdkSuppress(minSdkVersion = 28)
class VpnConnectionTests {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    private lateinit var context: Context
    private lateinit var scope: TestCoroutineScope
    private lateinit var userData: UserData
    private lateinit var monitor: VpnStateMonitor
    private lateinit var manager: VpnConnectionManager
    private lateinit var networkManager: MockNetworkManager

    @RelaxedMockK
    private lateinit var currentUser: CurrentUser

    @RelaxedMockK
    lateinit var serverManager: ServerManager

    @RelaxedMockK
    lateinit var appConfig: AppConfig

    @RelaxedMockK
    lateinit var certificateRepository: CertificateRepository

    @RelaxedMockK
    lateinit var vpnErrorHandler: VpnConnectionErrorHandler

    @RelaxedMockK
    lateinit var mockAgent: AgentConnectionInterface

    @MockK
    lateinit var mockVpnPermissionDelegate: VpnPermissionDelegate

    private lateinit var mockStrongSwan: MockVpnBackend
    private lateinit var mockOpenVpn: MockVpnBackend
    private lateinit var mockWireguard: MockVpnBackend

    private lateinit var profileSmart: Profile
    private lateinit var profileIKEv2: Profile
    private lateinit var profileOpenVPN: Profile
    private lateinit var profileWireguard: Profile
    private lateinit var fallbackOpenVpnProfile: Profile

    private val switchServerFlow = MutableSharedFlow<VpnFallbackResult.Switch>()

    private val agentConsts = LocalAgent.constants()
    private val validCert =
        CertificateRepository.CertificateResult.Success("good_cert", "good_key")
    private val badCert =
        CertificateRepository.CertificateResult.Success("bad_cert", "bad_key")
    private lateinit var currentCert: CertificateRepository.CertificateResult.Success
    private var time = 0L

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        context = InstrumentationRegistry.getInstrumentation().context
        scope = TestCoroutineScope()
        userData = spyk(UserData.create())
        coEvery { currentUser.sessionId() } returns SessionId("1")
        networkManager = MockNetworkManager()

        every { mockVpnPermissionDelegate.getContext() } returns context
        every { appConfig.getSmartProtocolConfig() } returns SmartProtocolConfig(
            ikeV2Enabled = true, openVPNEnabled = true, wireguardEnabled = true)
        mockStrongSwan = spyk(MockVpnBackend(
            scope, networkManager, certificateRepository, userData, appConfig, VpnProtocol.IKEv2, currentUser))
        mockOpenVpn = spyk(MockVpnBackend(
            scope, networkManager, certificateRepository, userData, appConfig, VpnProtocol.OpenVPN, currentUser))
        mockWireguard = spyk(MockVpnBackend(
            scope, networkManager, certificateRepository, userData, appConfig, VpnProtocol.WireGuard, currentUser))

        coEvery { vpnErrorHandler.switchConnectionFlow } returns switchServerFlow

        val backendProvider = ProtonVpnBackendProvider(
            strongSwan = mockStrongSwan,
            openVpn = mockOpenVpn,
            wireGuard = mockWireguard,
            serverDeliver = serverManager,
            config = appConfig
        )

        monitor = VpnStateMonitor()
        manager = MockVpnConnectionManager(userData, backendProvider, networkManager, vpnErrorHandler, monitor,
            mockk(relaxed = true), mockk(relaxed = true), scope, ::time)

        MockNetworkManager.currentStatus = NetworkStatus.Unmetered

        val server = MockedServers.server
        profileSmart = MockedServers.getProfile(VpnProtocol.Smart, server)
        profileIKEv2 = MockedServers.getProfile(VpnProtocol.IKEv2, server)
        profileOpenVPN = MockedServers.getProfile(VpnProtocol.OpenVPN, server)
        profileWireguard = MockedServers.getProfile(VpnProtocol.WireGuard, server)
        fallbackOpenVpnProfile = MockedServers.getProfile(VpnProtocol.OpenVPN, server, "fallback")
        val wrapperSlot = slot<ServerWrapper>()
        every { serverManager.getServer(capture(wrapperSlot)) } answers {
            MockedServers.serverList.find { it.serverId == wrapperSlot.captured.serverId } ?: server
        }

        setupMockAgent()
    }

    private fun setupMockAgent() {
        var agentState = agentConsts.stateDisconnected
        every { mockAgent.state } answers { agentState }
        mockWireguard.setAgentProvider { certificate, _, client ->
            agentState = if (certificate == validCert)
                agentConsts.stateConnected else agentConsts.stateHardJailed
            scope.launch {
                yield()
                client.onState(agentState)
                if (agentState == agentConsts.stateHardJailed)
                    client.onError(agentConsts.errorCodeCertificateExpired, "")
                else {
                    client.onState(agentConsts.stateConnecting)
                    client.onState(agentConsts.stateConnected)
                }
            }
            mockAgent
        }

        currentCert = validCert
        coEvery { certificateRepository.getCertificate(any(), any()) } answers {
            currentCert
        }
        coEvery { certificateRepository.updateCertificate(any(), any()) } answers {
            currentCert = validCert
            currentCert
        }
    }

    @Test
    fun testSmartFallbackToOpenVPN() = runBlockingTest {
        mockWireguard.failScanning = true
        mockStrongSwan.failScanning = true
        manager.connectInBackground(context, profileSmart)
        yield()

        Assert.assertEquals(VpnState.Connected, monitor.state)
        Assert.assertEquals(VpnProtocol.OpenVPN, monitor.status.value.connectionParams?.protocol)
    }

    @Test
    fun testAllBlocked() = runBlockingTest {
        mockWireguard.failScanning = true
        mockStrongSwan.failScanning = true
        mockOpenVpn.failScanning = true
        userData.setProtocols(VpnProtocol.OpenVPN, null)
        manager.connectInBackground(context, profileSmart)
        yield()

        // When scanning fails we'll fallback to attempt connecting with IKEv2 regardless of
        // selected protocol
        coVerify(exactly = 1) {
            mockStrongSwan.prepareForConnection(any(), any(), false)
            mockStrongSwan.connect(any())
        }

        Assert.assertEquals(VpnState.Connected, monitor.state)
    }

    @Test
    fun smartNoInternet() = runBlockingTest {
        MockNetworkManager.currentStatus = NetworkStatus.Disconnected
        userData.setProtocols(VpnProtocol.OpenVPN, null)
        manager.connectInBackground(context, profileSmart)
        yield()

        // Always fall back to StrongSwan, regardless of selected protocol.
        coVerify(exactly = 0) {
            mockOpenVpn.prepareForConnection(any(), any(), false)
        }
        coVerify(exactly = 1) {
            mockStrongSwan.prepareForConnection(any(), any(), any())
            mockStrongSwan.connect(any())
        }

        Assert.assertEquals(VpnState.Connected, monitor.state)
    }

    @Test
    fun connectToLocalAgent() = runBlockingTest {
        MockNetworkManager.currentStatus = NetworkStatus.Disconnected
        manager.connectInBackground(context, profileWireguard)
        scope.advanceUntilIdle()

        coVerify(exactly = 1) {
            mockWireguard.prepareForConnection(any(), any(), false)
            mockWireguard.createAgentConnection(any(), any(), any())
        }
        Assert.assertEquals(VpnState.Connected, monitor.state)
    }

    @Test
    fun localAgentNotUsedForIKEv2() = runBlockingTest {
        MockNetworkManager.currentStatus = NetworkStatus.Disconnected
        manager.connectInBackground(context, profileIKEv2)

        coVerify(exactly = 1) {
            mockStrongSwan.prepareForConnection(any(), any(), false)
        }
        coVerify(exactly = 0) {
            mockStrongSwan.connectToLocalAgent()
        }
        Assert.assertEquals(VpnState.Connected, monitor.state)
    }

    @Test
    fun localAgentNotUsedForGuestHole() = runBlockingTest {
        MockNetworkManager.currentStatus = NetworkStatus.Disconnected
        coEvery { currentUser.sessionId() } returns null
        every { currentUser.sessionIdCached() } returns null
        manager.connectInBackground(context, profileWireguard)

        coVerify(exactly = 1) {
            mockWireguard.prepareForConnection(any(), any(), false)
        }
        coVerify(exactly = 0) {
            mockWireguard.connectToLocalAgent()
        }
        Assert.assertEquals(VpnState.Connected, monitor.state)
    }

    @Test
    fun executeInGuestHole() = runBlocking {
        mockOpenVpn.stateOnConnect = VpnState.Connected
        val guestHole = spyk(
            GuestHole(
                mockk(relaxed = true),
                this,
                TestDispatcherProvider,
                dagger.Lazy { serverManager },
                monitor,
                dagger.Lazy { manager },
                mockk(relaxed = true)
            )
        )
        every { guestHole.getCurrentActivity() } returns mockk<ComponentActivity>()
        val guestHoleResult: ApiResult<Any>? = guestHole.onPotentiallyBlocked("/vpn", null) {
            ApiResult.Success<Any>(mockk())
        }
        Assert.assertTrue(monitor.isDisabled)
        Assert.assertNotNull(guestHoleResult)
    }

    @Test
    fun guestHoleFail() = runBlockingTest {
        mockOpenVpn.failScanning = true
        mockOpenVpn.stateOnConnect = VpnState.Disabled

        val guestHole = spyk(
            GuestHole(
                context,
                this,
                TestDispatcherProvider,
                dagger.Lazy { serverManager },
                monitor,
                dagger.Lazy { manager },
                mockk(relaxed = true)
            )
        )
        every { guestHole.getCurrentActivity() } returns mockk()
        val guestHoleResult: ApiResult<Any>? = guestHole.onPotentiallyBlocked("/randomCall", null) {
            ApiResult.Success<Any>(mockk())
        }
        Assert.assertTrue(monitor.isDisabled)
        Assert.assertNull(guestHoleResult)
    }

    @Test
    fun authErrorHandleDowngrade() = runBlockingTest {
        mockStrongSwan.stateOnConnect = VpnState.Error(ErrorType.AUTH_FAILED_INTERNAL)
        mockOpenVpn.stateOnConnect = VpnState.Connected
        val fallbackResult =
            VpnFallbackResult.Switch.SwitchProfile(profileIKEv2.server, fallbackOpenVpnProfile, SwitchServerReason.Downgrade("PLUS", "FREE"))
        coEvery { vpnErrorHandler.onAuthError(any()) } returns fallbackResult


        val fallbacks = mutableListOf<VpnFallbackResult>()
        val collectJob = launch {
            monitor.fallbackConnectionFlow.collect {
                fallbacks += it
            }
        }

        manager.connectInBackground(context, profileIKEv2)
        advanceUntilIdle()
        collectJob.cancel()

        coVerify(exactly = 1) {
            mockStrongSwan.connect(any())
        }
        coVerify(exactly = 1) {
            mockOpenVpn.connect(any())
        }

        Assert.assertEquals(VpnState.Connected, monitor.state)
        Assert.assertEquals(fallbackOpenVpnProfile, monitor.connectionProfile)
        Assert.assertEquals(listOf(fallbackResult), fallbacks)
    }

    @Test
    fun authErrorHandleMaxSessions() = runBlockingTest {
        mockStrongSwan.stateOnConnect = VpnState.Error(ErrorType.AUTH_FAILED_INTERNAL)
        coEvery { vpnErrorHandler.onAuthError(any()) } returns VpnFallbackResult.Error(ErrorType.MAX_SESSIONS)

        val fallbacks = mutableListOf<VpnFallbackResult>()
        val collectJob = launch {
            monitor.fallbackConnectionFlow.collect {
                fallbacks += it
            }
        }

        manager.connectInBackground(context, profileIKEv2)
        advanceUntilIdle()
        collectJob.cancel()

        coVerify(exactly = 1) {
            mockStrongSwan.connect(any())
        }

        Assert.assertEquals(VpnState.Disabled, monitor.state)
        Assert.assertTrue(fallbacks.isNotEmpty())
    }

    @Test
    fun handleUnreachableFallbackToOtherProtocol() = runBlockingTest {
        mockStrongSwan.stateOnConnect = VpnState.Error(ErrorType.UNREACHABLE_INTERNAL)

        val fallbackConnection = mockOpenVpn.prepareForConnection(
            fallbackOpenVpnProfile, fallbackOpenVpnProfile.server!!, true).first()
        val fallbackResult = VpnFallbackResult.Switch.SwitchServer(profileIKEv2.server,
            fallbackOpenVpnProfile, fallbackConnection, SwitchServerReason.ServerUnreachable,
            compatibleProtocol = false, switchedSecureCore = false)
        coEvery { vpnErrorHandler.onUnreachableError(any()) } returns fallbackResult

        val fallbacks = mutableListOf<VpnFallbackResult>()
        val collectJob = launch {
            monitor.fallbackConnectionFlow.collect {
                fallbacks += it
            }
        }

        manager.connectInBackground(context, profileIKEv2)
        advanceUntilIdle()
        collectJob.cancel()

        coVerify(exactly = 1) {
            mockStrongSwan.connect(any())
        }

        Assert.assertEquals(listOf(fallbackResult), fallbacks)
    }

    @Test
    fun testSwitchingConnection() = runBlockingTest {
        val fallbacks = mutableListOf<VpnFallbackResult>()
        val collectJob = launch {
            monitor.fallbackConnectionFlow.collect {
                fallbacks += it
            }
        }

        manager.connectInBackground(context, profileIKEv2)
        advanceUntilIdle()

        Assert.assertEquals(VpnState.Connected, monitor.state)

        val fallbackResult = VpnFallbackResult.Switch.SwitchProfile(
            profileIKEv2.server,
            fallbackOpenVpnProfile,
            SwitchServerReason.Downgrade("PLUS", "FREE")
        )
        switchServerFlow.emit(fallbackResult)
        advanceUntilIdle()

        collectJob.cancel()

        Assert.assertEquals(VpnState.Connected, monitor.state)
        Assert.assertEquals(listOf(fallbackResult), fallbacks)
    }

    @Test
    fun testSwitchOfflineServer() = runBlockingTest {
        val offlineServer = MockedServers.serverList.first { it.serverName == "SE#3" }
        val profile = Profile.getTempProfile(offlineServer, serverManager)
        coEvery {
            vpnErrorHandler.onServerInMaintenance(profile, null)
        } returns VpnFallbackResult.Switch.SwitchProfile(
            profile.server,
            profileIKEv2,
            SwitchServerReason.ServerInMaintenance
        )

        manager.connectInBackground(context, profile)
        scope.advanceUntilIdle()

        Assert.assertEquals(VpnState.Connected, monitor.state)
        Assert.assertEquals(profileIKEv2, monitor.connectionProfile)
    }

    @Test
    fun testDontSwitchWhenDisconnected() = runBlockingTest {
        val fallbacks = mutableListOf<VpnFallbackResult>()
        val collectJob = launch {
            monitor.fallbackConnectionFlow.collect {
                fallbacks += it
            }
        }
        val fallbackResult = VpnFallbackResult.Switch.SwitchProfile(
            profileIKEv2.server,
            fallbackOpenVpnProfile,
            SwitchServerReason.Downgrade("PLUS", "FREE")
        )
        switchServerFlow.emit(fallbackResult)
        advanceUntilIdle()

        collectJob.cancel()

        Assert.assertEquals(VpnState.Disabled, monitor.state)
        Assert.assertTrue(fallbacks.isEmpty())
    }

    @Test
    fun testExpiredCert() = runBlockingTest {
        coEvery { certificateRepository.getCertificate(any(), any()) } coAnswers {
            if (currentCert == badCert)
                certificateRepository.updateCertificate(currentUser.sessionId()!!, false)
            else
                currentCert
        }

        currentCert = badCert
        manager.connectInBackground(context, profileWireguard)
        scope.advanceUntilIdle()

        coVerify(exactly = 1) {
            certificateRepository.updateCertificate(any(), any())
        }

        assertEquals(VpnState.Connected, mockWireguard.selfState)
    }

    @Test
    fun testDowngradeWithLocalAgent() = runBlockingTest {
        mockWireguard.setAgentProvider { certificate, _, client ->
            client.onState(agentConsts.stateHardJailed)
            client.onError(agentConsts.errorCodePolicyViolationLowPlan, "")
            mockAgent
        }
        manager.connectInBackground(context, profileWireguard)
        scope.advanceUntilIdle()

        assertEquals(ErrorType.POLICY_VIOLATION_LOW_PLAN, (mockWireguard.selfState as? VpnState.Error)?.type)
    }
}
