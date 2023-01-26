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
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.appconfig.SmartProtocolConfig
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.usecase.GetConnectingDomain
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.telemetry.Telemetry
import com.protonvpn.android.telemetry.VpnConnectionTelemetry
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.ui.vpn.VpnBackgroundUiDelegate
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.AgentConnectionInterface
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.ConnectivityMonitor
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.LocalAgentUnreachableTracker
import com.protonvpn.android.vpn.PrepareResult
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.ProtonVpnBackendProvider
import com.protonvpn.android.vpn.ReasonRestricted
import com.protonvpn.android.vpn.SwitchServerReason
import com.protonvpn.android.vpn.VpnBackend
import com.protonvpn.android.vpn.VpnConnectionErrorHandler
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnFallbackResult
import com.protonvpn.android.vpn.VpnPermissionDelegate
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnUiDelegate
import com.protonvpn.mocks.MockAgentProvider
import com.protonvpn.mocks.MockVpnBackend
import com.protonvpn.test.shared.MockNetworkManager
import com.protonvpn.test.shared.MockSharedPreferencesProvider
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
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import me.proton.core.network.domain.NetworkStatus
import me.proton.core.network.domain.session.SessionId
import org.junit.After
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
    private lateinit var scope: TestScope
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
    lateinit var vpnUser: VpnUser

    @RelaxedMockK
    lateinit var getNetZone: GetNetZone

    @RelaxedMockK
    lateinit var appFeaturesPrefs: AppFeaturesPrefs

    @MockK
    lateinit var foregroundActivityTracker: ForegroundActivityTracker

    @RelaxedMockK
    lateinit var mockLocalAgentUnreachableTracker: LocalAgentUnreachableTracker

    @RelaxedMockK
    lateinit var mockTelemetry: Telemetry

    private lateinit var mockOpenVpn: MockVpnBackend
    private lateinit var mockWireguard: MockVpnBackend
    private lateinit var supportsProtocol: SupportsProtocol

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
    private val trigger = ConnectTrigger.Auto("test")

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val testScheduler = TestCoroutineScheduler()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        scope = TestScope(testDispatcher)
        testDispatcherProvider = TestDispatcherProvider(testDispatcher)
        Dispatchers.setMain(testDispatcher)
        val clock = { testScheduler.currentTime }

        userData = spyk(UserData.create())

        every { appConfig.getSmartProtocols() } returns ProtocolSelection.REAL_PROTOCOLS
        supportsProtocol = SupportsProtocol(appConfig)
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
            userData = userData,
            supportsProtocol = supportsProtocol
        )

        monitor = VpnStateMonitor()

        val mockConnectivityMonitor = mockk<ConnectivityMonitor>()
        every { mockConnectivityMonitor.defaultNetworkTransports } returns setOf(ConnectivityMonitor.Transport.WIFI)
        val vpnConnectionTelemetry = VpnConnectionTelemetry(
            scope.backgroundScope,
            clock,
            mockTelemetry,
            monitor,
            mockConnectivityMonitor,
            currentUser,
            ServerListUpdaterPrefs(MockSharedPreferencesProvider())
        ).apply { start() }

        manager = VpnConnectionManager(permissionDelegate, userData, appConfig, backendProvider, networkManager, vpnErrorHandler, monitor,
            mockVpnBackgroundUiDelegate, serverManager, certificateRepository, scope.backgroundScope, clock,
            mockk(relaxed = true), currentUser, supportsProtocol, mockk(relaxed = true), vpnConnectionTelemetry)

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

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun setupMockAgent(action: suspend (NativeClient) -> Unit) {
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
    fun whenScanFailsForWireguardThenOpenVpnIsUsed() = scope.runTest {
        mockWireguard.failScanning = true
        manager.connect(mockVpnUiDelegate, profileSmart, trigger)

        Assert.assertEquals(VpnState.Connected, monitor.state)
        Assert.assertEquals(VpnProtocol.OpenVPN, monitor.status.value.connectionParams?.protocolSelection?.vpn)
    }

    @Test
    fun whenFeatureFlagIsOffNoConnectionIsMade() = scope.runTest {
        every { appConfig.getFeatureFlags() } returns FeatureFlags(wireguardTlsEnabled = false)
        manager.connect(mockVpnUiDelegate, profileWireguardTls, trigger)

        verify { mockVpnUiDelegate.onProtocolNotSupported() }
        Assert.assertEquals(VpnState.Disabled, monitor.state)
    }

    @Test
    fun whenScanForAllProtocolsFailsThenDefaultProtocolIsUsed() = scope.runTest {
        mockWireguard.failScanning = true
        mockOpenVpn.failScanning = true
        userData.protocol = ProtocolSelection(VpnProtocol.OpenVPN)
        manager.connect(mockVpnUiDelegate, profileSmart, trigger)

        // When scanning fails we'll fallback to attempt connecting with WireGuard regardless of
        // selected protocol
        coVerify(exactly = 1) {
            mockWireguard.prepareForConnection(any(), any(), any(),false)
            mockWireguard.connect(not(isNull()))
        }

        Assert.assertEquals(VpnState.Connected, monitor.state)
    }

    @Test
    fun whenNoInternetWhileConnectingUseWireguard() = scope.runTest {
        MockNetworkManager.currentStatus = NetworkStatus.Disconnected
        userData.protocol = ProtocolSelection(VpnProtocol.OpenVPN, null)
        manager.connect(mockVpnUiDelegate, profileSmart, trigger)

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
    fun localAgentIsUsedForWireguard() = scope.runTest {
        MockNetworkManager.currentStatus = NetworkStatus.Disconnected
        manager.connect(mockVpnUiDelegate, profileWireguard, trigger)

        coVerify(exactly = 1) {
            mockWireguard.prepareForConnection(any(), any(), any(),false)
            mockWireguard.createAgentConnection(any(), any(), any())
        }
        Assert.assertEquals(VpnState.Connected, monitor.state)
    }

    @Test
    fun localAgentIsNotUsedForGuesthole() = scope.runTest {
        MockNetworkManager.currentStatus = NetworkStatus.Disconnected
        coEvery { currentUser.sessionId() } returns null
        every { currentUser.sessionIdCached() } returns null
        manager.connect(mockVpnUiDelegate, profileWireguard, trigger)

        coVerify(exactly = 1) {
            mockWireguard.prepareForConnection(any(), any(), any(),false)
        }
        coVerify(exactly = 0) {
            mockWireguard.connectToLocalAgent()
        }
        Assert.assertEquals(VpnState.Connected, monitor.state)
    }

    @Test
    fun whenGuestholeIsTriggeredVpnConnectionIsEstablishedOnlyForTheCall() = scope.runTest {
        // Guest Hole requires no user is logged in.
        coEvery { currentUser.sessionId() } returns null
        every { currentUser.sessionIdCached() } returns null

        mockOpenVpn.stateOnConnect = VpnState.Connected
        val guestHole = GuestHole(
            backgroundScope,
            testDispatcherProvider,
            dagger.Lazy { serverManager },
            monitor,
            permissionDelegate,
            dagger.Lazy { manager },
            mockk(relaxed = true),
            foregroundActivityTracker,
            appFeaturesPrefs
        )

        every { foregroundActivityTracker.foregroundActivity } returns mockk<ComponentActivity>()
        var wasExecuted = false
        val block: suspend () -> Unit = {
            Assert.assertTrue(monitor.isConnected)
            wasExecuted = true
        }
        guestHole.onAlternativesUnblock(block)

        Assert.assertTrue(wasExecuted)
        Assert.assertTrue(monitor.isDisabled)
    }

    @Test
    fun whenUserActionTriggeredGuestholeIsCanceled() = scope.runTest {
        val guestHole = GuestHole(
            backgroundScope,
            testDispatcherProvider,
            dagger.Lazy { serverManager },
            monitor,
            permissionDelegate,
            dagger.Lazy { manager },
            mockk(relaxed = true),
            foregroundActivityTracker,
            appFeaturesPrefs
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

        Assert.assertTrue(wasExecuted)
    }

    @Test fun dontConnectAfterFailedPingForGuestHole() = scope.runTest {
        coEvery { mockWireguard.prepareForConnection(any(), any(), any(), true) } returns emptyList()
        manager.connect(mockVpnUiDelegate, profileWireguard.copy(isGuestHoleProfile = true), trigger)
        coVerify(exactly = 0) { mockWireguard.connect(any()) }
    }

    @Test
    fun whenVpnIsConnectedGuestholeIsNotTriggered() = scope.runTest {
        mockOpenVpn.stateOnConnect = VpnState.Connected
        val guestHole = GuestHole(
            backgroundScope,
            testDispatcherProvider,
            dagger.Lazy { serverManager },
            monitor,
            permissionDelegate,
            dagger.Lazy { manager },
            mockk(relaxed = true),
            foregroundActivityTracker,
            appFeaturesPrefs
        )

        manager.connect(mockVpnUiDelegate, profileWireguard, trigger)

        every { foregroundActivityTracker.foregroundActivity } returns mockk<ComponentActivity>()
        guestHole.onAlternativesUnblock {
            Assert.fail()
        }

        Assert.assertTrue(monitor.isConnected)
    }

    @Test
    fun whenAuthErrorRequiresFallbackThenVpnConnectionIsReestablished() = scope.runTest {
        mockWireguard.stateOnConnect = VpnState.Error(ErrorType.AUTH_FAILED_INTERNAL, isFinal = false)
        mockOpenVpn.stateOnConnect = VpnState.Connected
        val fallbackResult = VpnFallbackResult.Switch.SwitchProfile(
            serverWireguard,
            fallbackServer,
            fallbackOpenVpnProfile,
            SwitchServerReason.Downgrade("PLUS", "FREE")
        )
        coEvery { vpnErrorHandler.onAuthError(any()) } returns fallbackResult

        val fallbacks = runWhileCollecting(monitor.vpnConnectionNotificationFlow) {
            manager.connect(mockVpnUiDelegate, profileWireguard, trigger)
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
    fun whenAuthErrorDueToMaxSessionsThenDisconnectAndReportError() = scope.runTest {
        mockWireguard.stateOnConnect = VpnState.Error(ErrorType.AUTH_FAILED_INTERNAL, isFinal = false)
        coEvery { vpnErrorHandler.onAuthError(any()) } returns VpnFallbackResult.Error(ErrorType.MAX_SESSIONS)

        val fallbacks = runWhileCollecting(monitor.vpnConnectionNotificationFlow) {
            manager.connect(mockVpnUiDelegate, profileWireguard, trigger)
        }

        coVerify(exactly = 1) {
            mockWireguard.connect(not(isNull()))
        }

        Assert.assertEquals(VpnState.Disabled, monitor.state)
        Assert.assertEquals(listOf(VpnFallbackResult.Error(ErrorType.MAX_SESSIONS)), fallbacks)
    }

    @Test
    fun whenLocalAgentReportsMaxSessionsThenDisconnectAndReportError() = scope.runTest {
        setupMockAgent { client ->
            client.onState(agentConsts.stateHardJailed)
            client.onError(agentConsts.errorCodeMaxSessionsPlus, "")
        }
        val notifications = runWhileCollecting(monitor.vpnConnectionNotificationFlow) {
            manager.connect(mockVpnUiDelegate, profileWireguard, trigger)
        }

        assertEquals(VpnState.Disabled, monitor.state)
        assertEquals(listOf(VpnFallbackResult.Error(ErrorType.MAX_SESSIONS)), notifications)
    }

    @Test
    fun whenUnreachableInternalStateIsReachedThenSwitchServer() = scope.runTest {
        mockWireguard.stateOnConnect = VpnState.Error(ErrorType.UNREACHABLE_INTERNAL, isFinal = false)

        val fallbackConnection = mockOpenVpn.prepareForConnection(
            fallbackOpenVpnProfile, fallbackServer, emptySet(), true).first()
        val fallbackResult = VpnFallbackResult.Switch.SwitchServer(serverWireguard,
            fallbackOpenVpnProfile, fallbackConnection, SwitchServerReason.ServerUnreachable,
            compatibleProtocol = false, switchedSecureCore = false, notifyUser = true)
        coEvery { vpnErrorHandler.onUnreachableError(any()) } returns fallbackResult

        val fallbacks = runWhileCollecting(monitor.vpnConnectionNotificationFlow) {
            manager.connect(mockVpnUiDelegate, profileWireguard, trigger)
        }

        coVerify(exactly = 1) {
            mockWireguard.connect(not(isNull()))
        }

        Assert.assertEquals(listOf(fallbackResult), fallbacks)
    }

    @Test
    fun whenErrorHandlerEmitsServerSwitchThenConnectToNewServer() = scope.runTest {
        val fallbacks = mutableListOf<VpnFallbackResult>()
        val collectJob = backgroundScope.launch {
            monitor.vpnConnectionNotificationFlow.collect {
                fallbacks += it
            }
        }

        manager.connect(mockVpnUiDelegate, profileWireguard, trigger)
        Assert.assertEquals(VpnState.Connected, monitor.state)

        val fallbackResult = VpnFallbackResult.Switch.SwitchProfile(
            serverWireguard,
            fallbackServer,
            fallbackOpenVpnProfile,
            SwitchServerReason.Downgrade("PLUS", "FREE")
        )
        switchServerFlow.emit(fallbackResult)

        collectJob.cancel()

        Assert.assertEquals(VpnState.Connected, monitor.state)
        Assert.assertEquals(listOf(fallbackResult), fallbacks)
    }

    @Test
    fun whenConnectingToOfflineServerThenSwitchToOtherServer() = scope.runTest {
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

        manager.connect(mockVpnUiDelegate, profile, trigger)

        Assert.assertEquals(VpnState.Connected, monitor.state)
        Assert.assertEquals(profileWireguard, monitor.connectionProfile)

        verify { mockVpnUiDelegate.onServerRestricted(ReasonRestricted.Maintenance) }
    }

    @Test
    fun whenErrorHandlerEmitsServerSwitchWhileDisconnectedThenDontConnectToNewServer() = scope.runTest {
        val fallbacks = mutableListOf<VpnFallbackResult>()
        val collectJob = backgroundScope.launch {
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

        collectJob.cancel()

        Assert.assertEquals(VpnState.Disabled, monitor.state)
        Assert.assertTrue(fallbacks.isEmpty())
    }

    @Test
    fun whenLocalAgentReportsLowPlanThenEnter_POLICY_VIOLATION_LOW_PLAN_State() = scope.runTest {
        setupMockAgent { client ->
            client.onState(agentConsts.stateHardJailed)
            client.onError(agentConsts.errorCodePolicyViolationLowPlan, "")
        }
        manager.connect(mockVpnUiDelegate, profileWireguard, trigger)

        assertEquals(ErrorType.POLICY_VIOLATION_LOW_PLAN, (mockWireguard.selfState as? VpnState.Error)?.type)
    }

    @Test
    fun whenLocalAgentReportsBadCertSignatureThenGenerateNewKeyAndReconnect() = scope.runTest {
        mockLocalAgentErrorAndAssertStates(
            agentErrorState = { client ->
                client.onState(agentConsts.stateHardJailed)
                client.onError(agentConsts.errorCodeBadCertSignature, "")
            },
            expectedVpnStates = listOf(
                VpnState.Disabled,
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
    fun whenLocalAgentReportsCertificateRevokedThenGenerateNewKeyAndReconnect() = scope.runTest {
        mockLocalAgentErrorAndAssertStates(
            agentErrorState = { client ->
                client.onState(agentConsts.stateHardJailed)
                client.onError(agentConsts.errorCodeCertificateRevoked, "")
            },
            expectedVpnStates = listOf(
                VpnState.Disabled,
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
    fun whenLocalAgentReportsCertificateExpiredThenGetCertificateAndReconnectLocalAgent() = scope.runTest {
        mockLocalAgentErrorAndAssertStates(
            agentErrorState = { client ->
                client.onState(agentConsts.stateClientCertificateExpiredError)
            },
            expectedVpnStates = listOf(
                VpnState.Disabled,
                VpnState.Connecting,
                VpnState.Connected
            )
        )
        coVerify(exactly = 2) { certificateRepository.getCertificate(any(), any()) }
    }

    @Test
    fun whenLocalAgentJailedWithExpiredCertThenGetCertificateAndReconnectLocalAgent() = scope.runTest {
        mockLocalAgentErrorAndAssertStates(
            agentErrorState = { client ->
                client.onState(agentConsts.stateHardJailed)
                client.onError(agentConsts.errorCodeCertificateExpired, "")
            },
            expectedVpnStates = listOf(
                VpnState.Disabled,
                VpnState.Connecting,
                VpnState.Connected
            )
        )
        coVerify(exactly = 2) { certificateRepository.getCertificate(any(), any()) }
    }

    @Test
    fun whenLocalAgentReportsUnknownCaThenUpdateCertificateAndReconnectLocalAgent() = scope.runTest {
        mockLocalAgentErrorAndAssertStates(
            agentErrorState = { client ->
                client.onState(agentConsts.stateClientCertificateUnknownCA)
            },
            expectedVpnStates = listOf(
                VpnState.Disabled,
                VpnState.Connecting,
                VpnState.Connected
            )
        )
        // Unknown CA forces update.
        coVerify(exactly = 1) { certificateRepository.updateCertificate(any(), any()) }
    }

    private fun TestScope.mockLocalAgentErrorAndAssertStates(
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
            manager.connect(mockVpnUiDelegate, profileWireguard, trigger)
        }
        assertEquals(2, localAgentConnectAttempt)
        assertEquals(expectedVpnStates, vpnStates.map { it.state })
        assertEquals(VpnState.Connected, mockWireguard.selfState)
    }

    @Test
    fun whenFreeUserConnectsToSecureCoreServerThenUserIsNotified() = scope.runTest {
        val secureCoreProfile =
            MockedServers.getProfile(MockedServers.serverList.find { it.isSecureCoreServer }!!, VpnProtocol.WireGuard)
        every { vpnUser.userTier } returns 0
        every { mockVpnUiDelegate.onServerRestricted(any()) } returns true

        manager.connect(mockVpnUiDelegate, secureCoreProfile, trigger)

        verify { mockVpnUiDelegate.onServerRestricted(ReasonRestricted.SecureCoreUpgradeNeeded) }
    }

    @Test
    fun testUnreachableInternalWhenLocalAgentUnreachableTrackerSignalsFallback() = scope.runTest {
        var nativeClient: VpnBackend.VpnAgentClient? = null
        mockWireguard.setAgentProvider { certificate, _, client ->
            nativeClient = client
            mockAgent
        }
        manager.connect(mockVpnUiDelegate, profileWireguard, trigger)
        assertNotNull(nativeClient)
        nativeClient!!.onState(agentConsts.stateConnected)
        assertEquals(VpnState.Connected, mockWireguard.selfState)

        every { mockLocalAgentUnreachableTracker.onUnreachable() } returns false
        nativeClient!!.onState(agentConsts.stateServerUnreachable)
        assertEquals(ErrorType.UNREACHABLE, (mockWireguard.selfState as? VpnState.Error)?.type)

        every { mockLocalAgentUnreachableTracker.onUnreachable() } returns true
        nativeClient!!.onState(agentConsts.stateServerUnreachable)
        assertEquals(ErrorType.UNREACHABLE_INTERNAL, (mockWireguard.selfState as? VpnState.Error)?.type)
    }

    @Test
    fun whenMaxSessionsOnConnectThenTelemetryReportsConnectFailure() = scope.runTest {
        setupMockAgent { client ->
            client.onState(agentConsts.stateHardJailed)
            client.onError(agentConsts.errorCodeMaxSessionsPlus, "")
        }
        manager.connect(mockVpnUiDelegate, profileWireguard, trigger)

        val dimensions = slot<Map<String, String>>()
        verify(exactly = 1) { mockTelemetry.event(VpnConnectionTelemetry.MEASUREMENT_GROUP, "vpn_connection", any(), capture(dimensions)) }
        assertEquals("failure", dimensions.captured["outcome"])
    }

    @Test
    fun whenMaxSessionsAfterConnectingThenTelemetryReportsDisconnectFailure() = scope.runTest {
        setupMockAgent { client ->
            client.onState(agentConsts.stateConnecting)
            client.onState(agentConsts.stateConnected)
            yield()
            client.onState(agentConsts.stateHardJailed)
            client.onError(agentConsts.errorCodeMaxSessionsPlus, "")
        }
        manager.connect(mockVpnUiDelegate, profileWireguard, trigger)

        val connectDimensions = slot<Map<String, String>>()
        val disconnectDimensions = slot<Map<String, String>>()
        verifyOrder {
            mockTelemetry.event(VpnConnectionTelemetry.MEASUREMENT_GROUP, "vpn_connection", any(), capture(connectDimensions))
            mockTelemetry.event(VpnConnectionTelemetry.MEASUREMENT_GROUP, "vpn_disconnection", any(), capture(disconnectDimensions))
        }
        assertEquals("success", connectDimensions.captured["outcome"])
        assertEquals("failure", disconnectDimensions.captured["outcome"])
        assertEquals("auto", disconnectDimensions.captured["vpn_trigger"])
    }

    @Test
    fun whenProfileFallbackOnConnectThenTelemetryReportsASingleEvent() = scope.runTest {
        val switch = VpnFallbackResult.Switch.SwitchProfile(
            null, MockedServers.server, profileOpenVPN, SwitchServerReason.ServerUnavailable
        )
        fallbackOnConnectReportsSingleEventTest(switch, "success")
    }

    @Test
    fun whenCompatibleServerFallbackOnConnectThenTelemetryReportsASingleEvent() = scope.runTest {
        val switch = createServerSwitch(
            profileOpenVPN, VpnProtocol.OpenVPN, mockOpenVpn, MockedServers.server, compatibleProtocol = true
        )
        fallbackOnConnectReportsSingleEventTest(switch, "success")
    }

    @Test
    fun whenIncompatibleServerFallbackOnConnectThenTelemetryReportsASingleEvent() = scope.runTest {
        val switch = createServerSwitch(
            profileOpenVPN, VpnProtocol.OpenVPN, mockOpenVpn, MockedServers.server, compatibleProtocol = false
        )
        fallbackOnConnectReportsSingleEventTest(switch, "failure")
    }

    @Test
    fun whenReconnectingToFallbackThenTelemetryReportsDisconnectWithSuccess() = scope.runTest {
        manager.connect(mockVpnUiDelegate, profileWireguard, trigger)

        val switch = VpnFallbackResult.Switch.SwitchProfile(
            null, MockedServers.server, profileOpenVPN, SwitchServerReason.ServerUnavailable
        )
        switchServerFlow.emit(switch)

        val disconnectDimensions = slot<Map<String, String>>()
        verify {
            mockTelemetry.event(VpnConnectionTelemetry.MEASUREMENT_GROUP, "vpn_disconnection", any(), capture(disconnectDimensions))
        }
        assertEquals("success", disconnectDimensions.captured["outcome"])
    }

    @Test
    fun whenGuestHoleFailsThenTelemetryReportsFailure() = scope.runTest {
        val guestHoleProfile =
            Profile.getTempProfile(MockedServers.server).apply {
                isGuestHoleProfile = true
                setProtocol(ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP))
            }
        coEvery { mockWireguard.prepareForConnection(guestHoleProfile, any(), any(), any()) } returns emptyList()

        manager.connect(mockVpnUiDelegate, guestHoleProfile, trigger)

        val dimensions = slot<Map<String, String>>()
        verifyOrder {
            mockTelemetry.event(VpnConnectionTelemetry.MEASUREMENT_GROUP, "vpn_connection", any(), capture(dimensions))
        }
        assertEquals("failure", dimensions.captured["outcome"])
    }

    private fun TestScope.fallbackOnConnectReportsSingleEventTest(
        fallbackSwitch: VpnFallbackResult.Switch,
        expectedOutcome: String
    ) {
        every { serverManager.getServerForProfile(profileWireguard, any()) } returns null
        coEvery {
            vpnErrorHandler.onServerNotAvailable(profileWireguard)
        } returns fallbackSwitch

        manager.connect(mockVpnUiDelegate, profileWireguard, ConnectTrigger.QuickConnect("test"))

        val dimensions = slot<Map<String, String>>()
        verify(exactly = 1) {
            mockTelemetry.event(VpnConnectionTelemetry.MEASUREMENT_GROUP, "vpn_connection", any(), capture(dimensions))
        }
        assertEquals(expectedOutcome, dimensions.captured["outcome"])
        assertEquals("quick", dimensions.captured["vpn_trigger"]) // Initial trigger is reported.
    }

    private fun createMockVpnBackend(protocol: VpnProtocol): MockVpnBackend =
        MockVpnBackend(
            scope.backgroundScope,
            testDispatcherProvider,
            networkManager,
            certificateRepository,
            userData,
            appConfig,
            protocol,
            mockLocalAgentUnreachableTracker,
            currentUser,
            getNetZone,
            foregroundActivityTracker,
            GetConnectingDomain(supportsProtocol)
        )

    private fun createServerSwitch(
        newProfile: Profile,
        protocol: VpnProtocol,
        backend: VpnBackend,
        newServer: Server,
        compatibleProtocol: Boolean
    ): VpnFallbackResult.Switch.SwitchServer {
        val fallbackConnectionParams =  ConnectionParams(
            newProfile,
            newServer,
            newServer.connectingDomains.first(),
            protocol
        )
        val fallbackConnection = PrepareResult(backend, fallbackConnectionParams)
        return VpnFallbackResult.Switch.SwitchServer(
            null,
            newProfile,
            fallbackConnection,
            SwitchServerReason.ServerUnavailable,
            compatibleProtocol = compatibleProtocol,
            switchedSecureCore = false,
            notifyUser = false
        )
    }
}
