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

import androidx.activity.ComponentActivity
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.proton.gopenpgp.localAgent.LocalAgent
import com.proton.gopenpgp.localAgent.NativeClient
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.SmartProtocolConfig
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.vpn.VpnBackgroundUiDelegate
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.AgentConnectionInterface
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.ProtonVpnBackendProvider
import com.protonvpn.android.vpn.ReasonRestricted
import com.protonvpn.android.vpn.SwitchServerReason
import com.protonvpn.android.vpn.VpnConnectionErrorHandler
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnFallbackResult
import com.protonvpn.android.vpn.VpnPermissionDelegate
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnUiDelegate
import com.protonvpn.di.MockNetworkManager
import com.protonvpn.mocks.MockAgentProvider
import com.protonvpn.mocks.MockVpnBackend
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.mockVpnUser
import com.protonvpn.test.shared.runWhileCollecting
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.yield
import me.proton.core.network.domain.NetworkStatus
import me.proton.core.network.domain.session.SessionId
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
// These tests use mocking of final classes that's not available on API < 28
@SdkSuppress(minSdkVersion = 28)
@OptIn(ExperimentalCoroutinesApi::class)
class VpnConnectionTests {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    private val permissionDelegate = VpnPermissionDelegate { null }
    private lateinit var testDispatcher: TestCoroutineDispatcher
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

    @MockK
    lateinit var certificateRepository: CertificateRepository

    @RelaxedMockK
    lateinit var vpnErrorHandler: VpnConnectionErrorHandler

    @RelaxedMockK
    lateinit var mockAgent: AgentConnectionInterface

    @MockK
    lateinit var mockVpnUiDelegate: VpnUiDelegate

    @MockK
    lateinit var mockVpnBackgroundUiDelegate: VpnBackgroundUiDelegate

    @RelaxedMockK
    lateinit var vpnUser: VpnUser

    @MockK
    lateinit var foregroundActivityTracker: ForegroundActivityTracker

    private lateinit var mockStrongSwan: MockVpnBackend
    private lateinit var mockOpenVpn: MockVpnBackend
    private lateinit var mockWireguard: MockVpnBackend

    private lateinit var profileSmart: Profile
    private lateinit var profileIKEv2: Profile
    private lateinit var profileOpenVPN: Profile
    private lateinit var profileWireguard: Profile
    private lateinit var fallbackOpenVpnProfile: Profile

    private lateinit var serverIKEv2: Server
    private lateinit var fallbackServer: Server

    private val switchServerFlow = MutableSharedFlow<VpnFallbackResult.Switch>()

    private val agentConsts = LocalAgent.constants()
    private val validCert =
        CertificateRepository.CertificateResult.Success("good_cert", "good_key")
    private lateinit var currentCert: CertificateRepository.CertificateResult.Success
    private var time = 0L

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        testDispatcher = TestCoroutineDispatcher()
        scope = TestCoroutineScope(testDispatcher)
        userData = spyk(UserData.create())

        coEvery { currentUser.sessionId() } returns SessionId("1")
        every { vpnUser.userTier } returns 1
        currentUser.mockVpnUser { vpnUser }

        every { mockVpnUiDelegate.shouldSkipAccessRestrictions() } returns false
        every { mockVpnBackgroundUiDelegate.shouldSkipAccessRestrictions() } returns false

        currentCert = validCert
        coEvery { certificateRepository.getCertificate(any(), any()) } answers {
            currentCert
        }
        coEvery { certificateRepository.getCertificateWithoutRefresh(any()) } answers {
            currentCert
        }
        coEvery { certificateRepository.updateCertificate(any(), any()) } answers {
            currentCert = validCert
            currentCert
        }
        coEvery { certificateRepository.generateNewKey(any()) } returns mockk()
        every { certificateRepository.currentCertUpdateFlow } returns emptyFlow()

        networkManager = MockNetworkManager()

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
            config = appConfig
        )

        monitor = VpnStateMonitor()
        manager = VpnConnectionManager(permissionDelegate, userData, backendProvider, networkManager, vpnErrorHandler, monitor,
            mockVpnBackgroundUiDelegate, serverManager, certificateRepository, scope, ::time, currentUser,
            mockk(relaxed = true))

        MockNetworkManager.currentStatus = NetworkStatus.Unmetered

        val server = MockedServers.server
        serverIKEv2 = MockedServers.serverList[1]
        fallbackServer = MockedServers.serverList[2]

        profileSmart = MockedServers.getProfile(VpnProtocol.Smart, server)
        profileIKEv2 = MockedServers.getProfile(VpnProtocol.IKEv2, serverIKEv2)
        profileOpenVPN = MockedServers.getProfile(VpnProtocol.OpenVPN, server)
        profileWireguard = MockedServers.getProfile(VpnProtocol.WireGuard, server)
        fallbackOpenVpnProfile = MockedServers.getProfile(VpnProtocol.OpenVPN, fallbackServer, "fallback")
        every { serverManager.getServerForProfile(any(), any()) } answers {
            MockedServers.serverList.find { it.serverId == arg<Profile>(0).wrapper.serverId }
        }

        setupMockAgent { client ->
            client.onState(agentConsts.stateConnecting)
            client.onState(agentConsts.stateConnected)
        }
    }

    private fun setupMockAgent(action: (NativeClient) -> Unit) {
        class NativeClientWrapper(private val client: NativeClient) : NativeClient by client {
            var lastState: String = LocalAgent.constants().stateDisconnected
                private set

            override fun onState(state: String) {
                lastState = state
                client.onState(state)
            }
        }

        val mockAgentProvider: MockAgentProvider = { _, _, client ->
            val wrappedClient = NativeClientWrapper(client)
            scope.launch {
                yield() // Don't set state immediately, yield for mockAgent to be returned first.
                action(client)
            }
            every { mockAgent.state } answers { wrappedClient.lastState }
            mockAgent
        }

        mockWireguard.setAgentProvider(mockAgentProvider)
        mockOpenVpn.setAgentProvider(mockAgentProvider)
    }

    @Test
    fun whenScanFailsForWireguardAndIkev2ThenOpenVpnIsUsed() = scope.runBlockingTest {
        mockWireguard.failScanning = true
        mockStrongSwan.failScanning = true
        manager.connect(mockVpnUiDelegate, profileSmart, "test")
        advanceUntilIdle()

        Assert.assertEquals(VpnState.Connected, monitor.state)
        Assert.assertEquals(VpnProtocol.OpenVPN, monitor.status.value.connectionParams?.protocol)
    }

    @Test
    fun whenScanForAllProtocolsFailsThenDefaultProtocolIsUsed() = scope.runBlockingTest {
        mockWireguard.failScanning = true
        mockStrongSwan.failScanning = true
        mockOpenVpn.failScanning = true
        userData.setProtocols(VpnProtocol.OpenVPN, null)
        manager.connect(mockVpnUiDelegate, profileSmart, "test")
        advanceUntilIdle()

        // When scanning fails we'll fallback to attempt connecting with WireGuard regardless of
        // selected protocol
        coVerify(exactly = 1) {
            mockWireguard.prepareForConnection(any(), any(), false)
            mockWireguard.connect(not(isNull()))
        }

        Assert.assertEquals(VpnState.Connected, monitor.state)
    }

    @Test
    fun whenNoInternetWhileConnectingUseWireguard() = scope.runBlockingTest {
        MockNetworkManager.currentStatus = NetworkStatus.Disconnected
        userData.setProtocols(VpnProtocol.OpenVPN, null)
        manager.connect(mockVpnUiDelegate, profileSmart, "test")
        advanceUntilIdle()

        // Always fall back to WireGuard, regardless of selected protocol.
        coVerify(exactly = 0) {
            mockOpenVpn.prepareForConnection(any(), any(), false)
        }
        coVerify(exactly = 1) {
            mockWireguard.prepareForConnection(any(), any(), any())
            mockWireguard.connect(not(isNull()))
        }

        Assert.assertEquals(VpnState.Connected, monitor.state)
    }

    @Test
    fun localAgentIsUsedForWireguard() = scope.runBlockingTest {
        MockNetworkManager.currentStatus = NetworkStatus.Disconnected
        manager.connect(mockVpnUiDelegate, profileWireguard, "test")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mockWireguard.prepareForConnection(any(), any(), false)
            mockWireguard.createAgentConnection(any(), any(), any())
        }
        Assert.assertEquals(VpnState.Connected, monitor.state)
    }

    @Test
    fun localAgentIsNotUsedForIkev2() = scope.runBlockingTest {
        MockNetworkManager.currentStatus = NetworkStatus.Disconnected
        manager.connect(mockVpnUiDelegate, profileIKEv2, "test")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mockStrongSwan.prepareForConnection(any(), any(), false)
        }
        coVerify(exactly = 0) {
            mockStrongSwan.connectToLocalAgent()
        }
        Assert.assertEquals(VpnState.Connected, monitor.state)
    }

    @Test
    fun localAgentIsNotUsedForGuesthole() = scope.runBlockingTest {
        MockNetworkManager.currentStatus = NetworkStatus.Disconnected
        coEvery { currentUser.sessionId() } returns null
        every { currentUser.sessionIdCached() } returns null
        manager.connect(mockVpnUiDelegate, profileWireguard, "test")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mockWireguard.prepareForConnection(any(), any(), false)
        }
        coVerify(exactly = 0) {
            mockWireguard.connectToLocalAgent()
        }
        Assert.assertEquals(VpnState.Connected, monitor.state)
    }

    @Test
    fun whenGuestholeIsTriggeredVpnConnectionIsEstablishedOnlyForTheCall() = scope.runBlockingTest {
        // Guest Hole requires no user is logged in.
        coEvery { currentUser.sessionId() } returns null
        every { currentUser.sessionIdCached() } returns null

        mockOpenVpn.stateOnConnect = VpnState.Connected
        val guestHole = GuestHole(
            this,
            TestDispatcherProvider(testDispatcher),
            dagger.Lazy { serverManager },
            monitor,
            permissionDelegate,
            dagger.Lazy { manager },
            mockk(relaxed = true),
            foregroundActivityTracker
        )

        every { foregroundActivityTracker.foregroundActivity } returns mockk<ComponentActivity>()
        guestHole.onAlternativesUnblock {
            Assert.assertTrue(monitor.isConnected)
        }
        Assert.assertTrue(monitor.isDisabled)
    }

    @Test
    fun whenVpnIsConnectedGuestholeIsNotTriggered() = scope.runBlockingTest {
        mockOpenVpn.stateOnConnect = VpnState.Connected
        val guestHole = GuestHole(
            this,
            TestDispatcherProvider(testDispatcher),
            dagger.Lazy { serverManager },
            monitor,
            permissionDelegate,
            dagger.Lazy { manager },
            mockk(relaxed = true),
            foregroundActivityTracker
        )

        manager.connect(mockVpnUiDelegate, profileIKEv2, "test")
        advanceUntilIdle()

        every { foregroundActivityTracker.foregroundActivity } returns mockk<ComponentActivity>()
        guestHole.onAlternativesUnblock {
            Assert.fail()
        }

        Assert.assertTrue(monitor.isConnected)
    }

    @Test
    fun whenAuthErrorRequiresFallbackThenVpnConnectionIsReestablished() = scope.runBlockingTest {
        mockStrongSwan.stateOnConnect = VpnState.Error(ErrorType.AUTH_FAILED_INTERNAL)
        mockOpenVpn.stateOnConnect = VpnState.Connected
        val fallbackResult = VpnFallbackResult.Switch.SwitchProfile(
            serverIKEv2,
            fallbackServer,
            fallbackOpenVpnProfile,
            SwitchServerReason.Downgrade("PLUS", "FREE")
        )
        coEvery { vpnErrorHandler.onAuthError(any()) } returns fallbackResult

        val fallbacks = runWhileCollecting(monitor.vpnConnectionNotificationFlow) {
            manager.connect(mockVpnUiDelegate, profileIKEv2, "test")
            advanceUntilIdle()
        }

        coVerify(exactly = 1) {
            mockStrongSwan.connect(not(isNull()))
        }
        coVerify(exactly = 1) {
            mockOpenVpn.connect(not(isNull()))
        }

        Assert.assertEquals(VpnState.Connected, monitor.state)
        Assert.assertEquals(fallbackOpenVpnProfile, monitor.connectionProfile)
        Assert.assertEquals(listOf(fallbackResult), fallbacks)
    }

    @Test
    fun whenAuthErrorDueToMaxSessionsThenDisconnectAndReportError() = scope.runBlockingTest {
        mockStrongSwan.stateOnConnect = VpnState.Error(ErrorType.AUTH_FAILED_INTERNAL)
        coEvery { vpnErrorHandler.onAuthError(any()) } returns VpnFallbackResult.Error(ErrorType.MAX_SESSIONS)

        val fallbacks = runWhileCollecting(monitor.vpnConnectionNotificationFlow) {
            manager.connect(mockVpnUiDelegate, profileIKEv2, "test")
            advanceUntilIdle()
        }

        coVerify(exactly = 1) {
            mockStrongSwan.connect(not(isNull()))
        }

        Assert.assertEquals(VpnState.Disabled, monitor.state)
        Assert.assertEquals(listOf(VpnFallbackResult.Error(ErrorType.MAX_SESSIONS)), fallbacks)
    }

    @Test
    fun whenLocalAgentReportsMaxSessionsThenDisconnectAndReportError() = scope.runBlockingTest {
        setupMockAgent { client ->
            client.onState(agentConsts.stateHardJailed)
            client.onError(agentConsts.errorCodeMaxSessionsPlus, "")
        }
        val notifications = runWhileCollecting(monitor.vpnConnectionNotificationFlow) {
            manager.connect(mockVpnUiDelegate, profileWireguard, "test")
            advanceUntilIdle()
        }

        assertEquals(VpnState.Disabled, monitor.state)
        assertEquals(listOf(VpnFallbackResult.Error(ErrorType.MAX_SESSIONS)), notifications)
    }

    @Test
    fun whenUnreachableInternalStateIsReachedThenSwitchServer() = scope.runBlockingTest {
        mockStrongSwan.stateOnConnect = VpnState.Error(ErrorType.UNREACHABLE_INTERNAL)

        val fallbackConnection = mockOpenVpn.prepareForConnection(
            fallbackOpenVpnProfile, fallbackServer, true).first()
        val fallbackResult = VpnFallbackResult.Switch.SwitchServer(serverIKEv2,
            fallbackOpenVpnProfile, fallbackConnection, SwitchServerReason.ServerUnreachable,
            compatibleProtocol = false, switchedSecureCore = false, notifyUser = true)
        coEvery { vpnErrorHandler.onUnreachableError(any()) } returns fallbackResult

        val fallbacks = runWhileCollecting(monitor.vpnConnectionNotificationFlow) {
            manager.connect(mockVpnUiDelegate, profileIKEv2, "test")
            advanceUntilIdle()
        }

        coVerify(exactly = 1) {
            mockStrongSwan.connect(not(isNull()))
        }

        Assert.assertEquals(listOf(fallbackResult), fallbacks)
    }

    @Test
    fun whenErrorHandlerEmitsServerSwitchThenConnectToNewServer() = scope.runBlockingTest {
        val fallbacks = mutableListOf<VpnFallbackResult>()
        val collectJob = launch {
            monitor.vpnConnectionNotificationFlow.collect {
                fallbacks += it
            }
        }

        manager.connect(mockVpnUiDelegate, profileIKEv2, "test")
        advanceUntilIdle()

        Assert.assertEquals(VpnState.Connected, monitor.state)

        val fallbackResult = VpnFallbackResult.Switch.SwitchProfile(
            serverIKEv2,
            fallbackServer,
            fallbackOpenVpnProfile,
            SwitchServerReason.Downgrade("PLUS", "FREE")
        )
        switchServerFlow.emit(fallbackResult)
        scope.advanceUntilIdle()

        collectJob.cancel()

        Assert.assertEquals(VpnState.Connected, monitor.state)
        Assert.assertEquals(listOf(fallbackResult), fallbacks)
    }

    @Test
    fun whenConnectingToOfflineServerThenSwitchToOtherServer() = scope.runBlockingTest {
        val offlineServer = MockedServers.serverList.first { it.serverName == "SE#3" }
        val profile = Profile.getTempProfile(offlineServer)
        coEvery {
            vpnErrorHandler.onServerInMaintenance(profile, null)
        } returns VpnFallbackResult.Switch.SwitchProfile(
            offlineServer,
            serverIKEv2,
            profileIKEv2,
            SwitchServerReason.ServerInMaintenance
        )

        // Returning false means fallback will be used.
        every { mockVpnUiDelegate.onServerRestricted(any()) } returns false

        manager.connect(mockVpnUiDelegate, profile, "test")
        advanceUntilIdle()

        Assert.assertEquals(VpnState.Connected, monitor.state)
        Assert.assertEquals(profileIKEv2, monitor.connectionProfile)

        verify { mockVpnUiDelegate.onServerRestricted(ReasonRestricted.Maintenance) }
    }

    @Test
    fun whenErrorHandlerEmitsServerSwitchWhileDisconnectedThenDontConnectToNewServer() = scope.runBlockingTest {
        val fallbacks = mutableListOf<VpnFallbackResult>()
        val collectJob = launch {
            monitor.vpnConnectionNotificationFlow.collect {
                fallbacks += it
            }
        }
        val fallbackResult = VpnFallbackResult.Switch.SwitchProfile(
            serverIKEv2,
            fallbackServer,
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
    fun whenLocalAgentReportsLowPlanThenEnter_POLICY_VIOLATION_LOW_PLAN_State() = scope.runBlockingTest {
        setupMockAgent { client ->
            client.onState(agentConsts.stateHardJailed)
            client.onError(agentConsts.errorCodePolicyViolationLowPlan, "")
        }
        manager.connect(mockVpnUiDelegate, profileWireguard, "test")
        advanceUntilIdle()

        assertEquals(ErrorType.POLICY_VIOLATION_LOW_PLAN, (mockWireguard.selfState as? VpnState.Error)?.type)
    }

    @Test

    fun whenLocalAgentReportsBadCertSignatureThenGenerateNewKeyAndReconnect() = scope.runBlockingTest {
        mockLocalAgentErrorAndAssertStates(
            agentErrorState = { client ->
                client.onState(agentConsts.stateHardJailed)
                client.onError(agentConsts.errorCodeBadCertSignature, "")
            },
            expectedVpnStates = listOf(
                VpnState.Disabled,
                VpnState.ScanningPorts,
                VpnState.Connecting,
                VpnState.Disconnecting,
                VpnState.Disabled, // Full reconnection.
                VpnState.Connecting,
                VpnState.Connected
            )
        )
        coVerify(exactly = 1) { certificateRepository.generateNewKey(any()) }
        coVerify(exactly = 1) { certificateRepository.updateCertificate(any(), any()) }
    }

    @Test
    fun whenLocalAgentReportsCertificateRevokedThenGenerateNewKeyAndReconnect() = scope.runBlockingTest {
        mockLocalAgentErrorAndAssertStates(
            agentErrorState = { client ->
                client.onState(agentConsts.stateHardJailed)
                client.onError(agentConsts.errorCodeCertificateRevoked, "")
            },
            expectedVpnStates = listOf(
                VpnState.Disabled,
                VpnState.ScanningPorts,
                VpnState.Connecting,
                VpnState.Disconnecting,
                VpnState.Disabled, // Full reconnection.
                VpnState.Connecting,
                VpnState.Connected
            )
        )
        coVerify(exactly = 1) { certificateRepository.generateNewKey(any()) }
        coVerify(exactly = 1) { certificateRepository.updateCertificate(any(), any()) }
    }

    @Test
    fun whenLocalAgentReportsCertificateExpiredThenGetCertificateAndReconnectLocalAgent() = scope.runBlockingTest {
        mockLocalAgentErrorAndAssertStates(
            agentErrorState = { client ->
                client.onState(agentConsts.stateClientCertificateExpiredError)
            },
            expectedVpnStates = listOf(
                VpnState.Disabled,
                VpnState.ScanningPorts,
                VpnState.Connecting,
                VpnState.Connected
            )
        )
        coVerify(exactly = 2) { certificateRepository.getCertificate(any(), any()) }
    }

    @Test
    fun whenLocalAgentJailedWithExpiredCertThenGetCertificateAndReconnectLocalAgent() = scope.runBlockingTest {
        mockLocalAgentErrorAndAssertStates(
            agentErrorState = { client ->
                client.onState(agentConsts.stateHardJailed)
                client.onError(agentConsts.errorCodeCertificateExpired, "")
            },
            expectedVpnStates = listOf(
                VpnState.Disabled,
                VpnState.ScanningPorts,
                VpnState.Connecting,
                VpnState.Connected
            )
        )
        coVerify(exactly = 2) { certificateRepository.getCertificate(any(), any()) }
    }

    @Test
    fun whenLocalAgentReportsUnknownCaThenUpdateCertificateAndReconnectLocalAgent() = scope.runBlockingTest{
        mockLocalAgentErrorAndAssertStates(
            agentErrorState = { client ->
                client.onState(agentConsts.stateClientCertificateUnknownCA)
            },
            expectedVpnStates = listOf(
                VpnState.Disabled,
                VpnState.ScanningPorts,
                VpnState.Connecting,
                VpnState.Connected
            )
        )
        // Unknown CA forces update.
        coVerify(exactly = 1) { certificateRepository.updateCertificate(any(), any()) }
    }

    private fun TestCoroutineScope.mockLocalAgentErrorAndAssertStates(
        agentErrorState: (NativeClient) -> Unit,
        expectedVpnStates: List<VpnState>
    ) {
        var localAgentConnectAttempt = 0
        setupMockAgent { client ->
            if (localAgentConnectAttempt == 0) {
                agentErrorState(client)
            } else {
                client.onState(agentConsts.stateConnecting)
                client.onState(agentConsts.stateConnected)
            }
            localAgentConnectAttempt++
        }
        val vpnStates = runWhileCollecting(monitor.status) {
            manager.connect(mockVpnUiDelegate, profileWireguard, "test")
            advanceUntilIdle()
        }
        assertEquals(2, localAgentConnectAttempt)
        assertEquals(expectedVpnStates, vpnStates.map { it.state })
        assertEquals(VpnState.Connected, mockWireguard.selfState)
    }

    @Test
    fun whenFreeUserConnectsToSecureCoreServerThenUserIsNotified() = scope.runBlockingTest {
        val secureCoreProfile =
            MockedServers.getProfile(VpnProtocol.WireGuard, MockedServers.serverList.find { it.isSecureCoreServer }!!)
        every { vpnUser.userTier } returns 0
        every { mockVpnUiDelegate.onServerRestricted(any()) } returns true

        manager.connect(mockVpnUiDelegate, secureCoreProfile, "test")

        verify { mockVpnUiDelegate.onServerRestricted(ReasonRestricted.SecureCoreUpgradeNeeded) }
    }
}
