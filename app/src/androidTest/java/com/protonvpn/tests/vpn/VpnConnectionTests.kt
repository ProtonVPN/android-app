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
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.appconfig.SmartProtocolConfig
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.ui.vpn.VpnBackgroundUiDelegate
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.AgentConnectionInterface
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.LocalAgentUnreachableTracker
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.ProtonVpnBackendProvider
import com.protonvpn.android.vpn.ReasonRestricted
import com.protonvpn.android.vpn.ServerAvailabilityCheck
import com.protonvpn.android.vpn.SwitchServerReason
import com.protonvpn.android.vpn.VpnBackend
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
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
import kotlin.test.assertNotNull

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
    private lateinit var testDispatcherProvider: TestDispatcherProvider
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

    @RelaxedMockK
    lateinit var mockVpnUiDelegate: VpnUiDelegate

    @MockK
    lateinit var mockVpnBackgroundUiDelegate: VpnBackgroundUiDelegate

    @RelaxedMockK
    lateinit var mockServerAvailabilityCheck: ServerAvailabilityCheck

    @RelaxedMockK
    lateinit var vpnUser: VpnUser

    @RelaxedMockK
    lateinit var getNetZone: GetNetZone

    @MockK
    lateinit var foregroundActivityTracker: ForegroundActivityTracker

    @RelaxedMockK
    lateinit var mockLocalAgentUnreachableTracker: LocalAgentUnreachableTracker

    private lateinit var mockOpenVpn: MockVpnBackend
    private lateinit var mockWireguard: MockVpnBackend

    private lateinit var profileSmart: Profile
    private lateinit var profileOpenVPN: Profile
    private lateinit var profileWireguard: Profile
    private lateinit var profileWireguardTls: Profile
    private lateinit var fallbackOpenVpnProfile: Profile

    private lateinit var serverWireguard: Server
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
        testDispatcherProvider = TestDispatcherProvider(testDispatcher)
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
            openVPNEnabled = true, wireguardEnabled = true, wireguardTcpEnabled = true, wireguardTlsEnabled = true)
        mockOpenVpn = spyk(createMockVpnBackend(VpnProtocol.OpenVPN))
        mockWireguard = spyk(createMockVpnBackend(VpnProtocol.WireGuard))

        coEvery { vpnErrorHandler.switchConnectionFlow } returns switchServerFlow

        val backendProvider = ProtonVpnBackendProvider(
            openVpn = mockOpenVpn,
            wireGuard = mockWireguard,
            config = appConfig,
            userData = userData
        )

        monitor = VpnStateMonitor()
        manager = VpnConnectionManager(permissionDelegate, userData, appConfig, backendProvider, networkManager, vpnErrorHandler, monitor,
            mockVpnBackgroundUiDelegate, serverManager, certificateRepository, scope, ::time, mockk(relaxed = true),
            currentUser, mockk(relaxed = true))

        MockNetworkManager.currentStatus = NetworkStatus.Unmetered

        val server = MockedServers.server
        serverWireguard = MockedServers.serverList[1]
        fallbackServer = MockedServers.serverList[2]

        profileSmart = MockedServers.getProfile(server, VpnProtocol.Smart)
        profileOpenVPN = MockedServers.getProfile(server, VpnProtocol.OpenVPN)
        profileWireguard = MockedServers.getProfile(serverWireguard, VpnProtocol.WireGuard)
        profileWireguardTls = MockedServers.getProfile(server, VpnProtocol.WireGuard, transmissionProtocol = TransmissionProtocol.TLS)
        fallbackOpenVpnProfile = MockedServers.getProfile(fallbackServer, VpnProtocol.OpenVPN, "fallback")
        every { serverManager.getServerForProfile(any(), any()) } answers {
            MockedServers.serverList.find { it.serverId == arg<Profile>(0).wrapper.serverId } ?: MockedServers.server
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
    fun whenScanFailsForWireguardThenOpenVpnIsUsed() = scope.runBlockingTest {
        mockWireguard.failScanning = true
        manager.connect(mockVpnUiDelegate, profileSmart, "test")
        advanceUntilIdle()

        Assert.assertEquals(VpnState.Connected, monitor.state)
        Assert.assertEquals(VpnProtocol.OpenVPN, monitor.status.value.connectionParams?.protocolSelection?.vpn)
    }

    @Test
    fun whenFeatureFlagIsOffNoConnectionIsMade() = scope.runBlockingTest {
        every { appConfig.getFeatureFlags() } returns FeatureFlags(wireguardTlsEnabled = false)
        manager.connect(mockVpnUiDelegate, profileWireguardTls, "test")

        verify { mockVpnUiDelegate.onProtocolNotSupported() }
        Assert.assertEquals(VpnState.Disabled, monitor.state)
    }

    @Test
    fun whenScanForAllProtocolsFailsThenDefaultProtocolIsUsed() = scope.runBlockingTest {
        mockWireguard.failScanning = true
        mockOpenVpn.failScanning = true
        userData.protocol = ProtocolSelection(VpnProtocol.OpenVPN)
        manager.connect(mockVpnUiDelegate, profileSmart, "test")
        advanceUntilIdle()

        // When scanning fails we'll fallback to attempt connecting with WireGuard regardless of
        // selected protocol
        coVerify(exactly = 1) {
            mockWireguard.prepareForConnection(any(), any(), any(),false)
            mockWireguard.connect(not(isNull()))
        }

        Assert.assertEquals(VpnState.Connected, monitor.state)
    }

    @Test
    fun whenNoInternetWhileConnectingUseWireguard() = scope.runBlockingTest {
        MockNetworkManager.currentStatus = NetworkStatus.Disconnected
        userData.protocol = ProtocolSelection(VpnProtocol.OpenVPN, null)
        manager.connect(mockVpnUiDelegate, profileSmart, "test")
        advanceUntilIdle()

        // Always fall back to WireGuard, regardless of selected protocol.
        coVerify(exactly = 0) {
            mockOpenVpn.prepareForConnection(any(), any(), any(),false)
        }
        coVerify(exactly = 1) {
            mockWireguard.prepareForConnection(any(), any(), any(), any())
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
            mockWireguard.prepareForConnection(any(), any(), any(),false)
            mockWireguard.createAgentConnection(any(), any(), any())
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
            mockWireguard.prepareForConnection(any(), any(), any(),false)
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
            testDispatcherProvider,
            dagger.Lazy { serverManager },
            monitor,
            permissionDelegate,
            dagger.Lazy { manager },
            mockk(relaxed = true),
            foregroundActivityTracker
        )

        every { foregroundActivityTracker.foregroundActivity } returns mockk<ComponentActivity>()
        var wasExecuted = false
        val block: suspend () -> Unit = {
            Assert.assertTrue(monitor.isConnected)
            wasExecuted = true
        }
        guestHole.onAlternativesUnblock(block)
        advanceUntilIdle() // Wait for disconnection to finish.

        Assert.assertTrue(wasExecuted)
        Assert.assertTrue(monitor.isDisabled)
    }

    @Test
    fun whenUserActionTriggeredGuestholeIsCanceled() = scope.runBlockingTest {
        val guestHole = GuestHole(
            scope,
            testDispatcherProvider,
            dagger.Lazy { serverManager },
            monitor,
            permissionDelegate,
            dagger.Lazy { manager },
            mockk(relaxed = true),
            foregroundActivityTracker
        )
        every { foregroundActivityTracker.foregroundActivity } returns mockk<ComponentActivity>()
        mockOpenVpn.stateOnConnect = VpnState.Connected

        var wasExecuted = false
        val block: suspend () -> Unit = {
            wasExecuted = true
            monitor.onDisconnectedByUser.emit(Unit)
            Assert.assertFalse(!guestHole.job!!.isActive)
        }
        guestHole.onAlternativesUnblock(block)
        advanceUntilIdle()
        
        Assert.assertTrue(wasExecuted)
    }

    @Test
    fun whenVpnIsConnectedGuestholeIsNotTriggered() = scope.runBlockingTest {
        mockOpenVpn.stateOnConnect = VpnState.Connected
        val guestHole = GuestHole(
            this,
            testDispatcherProvider,
            dagger.Lazy { serverManager },
            monitor,
            permissionDelegate,
            dagger.Lazy { manager },
            mockk(relaxed = true),
            foregroundActivityTracker
        )

        manager.connect(mockVpnUiDelegate, profileWireguard, "test")
        advanceUntilIdle()

        every { foregroundActivityTracker.foregroundActivity } returns mockk<ComponentActivity>()
        guestHole.onAlternativesUnblock {
            Assert.fail()
        }

        Assert.assertTrue(monitor.isConnected)
    }

    @Test
    fun whenAuthErrorRequiresFallbackThenVpnConnectionIsReestablished() = scope.runBlockingTest {
        mockWireguard.stateOnConnect = VpnState.Error(ErrorType.AUTH_FAILED_INTERNAL)
        mockOpenVpn.stateOnConnect = VpnState.Connected
        val fallbackResult = VpnFallbackResult.Switch.SwitchProfile(
            serverWireguard,
            fallbackServer,
            fallbackOpenVpnProfile,
            SwitchServerReason.Downgrade("PLUS", "FREE")
        )
        coEvery { vpnErrorHandler.onAuthError(any()) } returns fallbackResult

        val fallbacks = runWhileCollecting(monitor.vpnConnectionNotificationFlow) {
            manager.connect(mockVpnUiDelegate, profileWireguard, "test")
            advanceUntilIdle()
        }

        coVerify(exactly = 1) {
            mockWireguard.connect(not(isNull()))
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
        mockWireguard.stateOnConnect = VpnState.Error(ErrorType.AUTH_FAILED_INTERNAL)
        coEvery { vpnErrorHandler.onAuthError(any()) } returns VpnFallbackResult.Error(ErrorType.MAX_SESSIONS)

        val fallbacks = runWhileCollecting(monitor.vpnConnectionNotificationFlow) {
            manager.connect(mockVpnUiDelegate, profileWireguard, "test")
            advanceUntilIdle()
        }

        coVerify(exactly = 1) {
            mockWireguard.connect(not(isNull()))
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
        mockWireguard.stateOnConnect = VpnState.Error(ErrorType.UNREACHABLE_INTERNAL)

        val fallbackConnection = mockOpenVpn.prepareForConnection(
            fallbackOpenVpnProfile, fallbackServer, emptySet(), true).first()
        val fallbackResult = VpnFallbackResult.Switch.SwitchServer(serverWireguard,
            fallbackOpenVpnProfile, fallbackConnection, SwitchServerReason.ServerUnreachable,
            compatibleProtocol = false, switchedSecureCore = false, notifyUser = true)
        coEvery { vpnErrorHandler.onUnreachableError(any()) } returns fallbackResult

        val fallbacks = runWhileCollecting(monitor.vpnConnectionNotificationFlow) {
            manager.connect(mockVpnUiDelegate, profileWireguard, "test")
            advanceUntilIdle()
        }

        coVerify(exactly = 1) {
            mockWireguard.connect(not(isNull()))
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

        manager.connect(mockVpnUiDelegate, profileWireguard, "test")
        advanceUntilIdle()

        Assert.assertEquals(VpnState.Connected, monitor.state)

        val fallbackResult = VpnFallbackResult.Switch.SwitchProfile(
            serverWireguard,
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
            serverWireguard,
            profileWireguard,
            SwitchServerReason.ServerInMaintenance
        )

        // Returning false means fallback will be used.
        every { mockVpnUiDelegate.onServerRestricted(any()) } returns false

        manager.connect(mockVpnUiDelegate, profile, "test")
        advanceUntilIdle()

        Assert.assertEquals(VpnState.Connected, monitor.state)
        Assert.assertEquals(profileWireguard, monitor.connectionProfile)

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
            serverWireguard,
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
    fun whenLocalAgentReportsUnknownCaThenUpdateCertificateAndReconnectLocalAgent() = scope.runBlockingTest {
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
            MockedServers.getProfile(MockedServers.serverList.find { it.isSecureCoreServer }!!, VpnProtocol.WireGuard)
        every { vpnUser.userTier } returns 0
        every { mockVpnUiDelegate.onServerRestricted(any()) } returns true

        manager.connect(mockVpnUiDelegate, secureCoreProfile, "test")

        verify { mockVpnUiDelegate.onServerRestricted(ReasonRestricted.SecureCoreUpgradeNeeded) }
    }

    @Test
    fun testUnreachableInternalWhenLocalAgentUnreachableTrackerSignalsFallback() = scope.runBlockingTest {
        var nativeClient: VpnBackend.VpnAgentClient? = null
        mockWireguard.setAgentProvider { certificate, _, client ->
            nativeClient = client
            mockAgent
        }
        manager.connect(mockVpnUiDelegate, profileWireguard, "test")
        advanceUntilIdle()
        assertNotNull(nativeClient)
        nativeClient!!.onState(agentConsts.stateConnected)
        advanceUntilIdle()
        assertEquals(VpnState.Connected, mockWireguard.selfState)

        every { mockLocalAgentUnreachableTracker.onUnreachable() } returns false
        nativeClient!!.onState(agentConsts.stateServerUnreachable)
        advanceUntilIdle()
        assertEquals(ErrorType.UNREACHABLE, (mockWireguard.selfState as? VpnState.Error)?.type)

        every { mockLocalAgentUnreachableTracker.onUnreachable() } returns true
        nativeClient!!.onState(agentConsts.stateServerUnreachable)
        advanceUntilIdle()
        assertEquals(ErrorType.UNREACHABLE_INTERNAL, (mockWireguard.selfState as? VpnState.Error)?.type)
    }

    private fun createMockVpnBackend(protocol: VpnProtocol): MockVpnBackend =
        MockVpnBackend(
            scope, testDispatcherProvider, networkManager, certificateRepository, userData, appConfig, protocol,
            mockLocalAgentUnreachableTracker, currentUser, getNetZone, mockServerAvailabilityCheck
        )
}
