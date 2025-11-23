/*
 *
 *  * Copyright (c) 2023. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */
package com.protonvpn.tests.vpn

import androidx.activity.ComponentActivity
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.proton.gopenpgp.localAgent.LocalAgent
import com.proton.gopenpgp.localAgent.NativeClient
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.GuestHoleSuppressor
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.appconfig.GetFeatureFlags
import com.protonvpn.android.appconfig.SmartProtocolConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.AppDatabase.Companion.buildDatabase
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.usecase.GetConnectingDomain
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.profiles.data.profileSettingsOverrides
import com.protonvpn.android.profiles.usecases.GetProfileByIdImpl
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.data.ProtocolSelectionData
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.settings.data.ApplyEffectiveUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.telemetry.ConnectionTelemetrySentryDebugEnabled
import com.protonvpn.android.telemetry.DefaultCommonDimensions
import com.protonvpn.android.telemetry.DefaultTelemetryReporter
import com.protonvpn.android.telemetry.Telemetry
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import com.protonvpn.android.telemetry.VpnConnectionTelemetry
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.ui.vpn.VpnBackgroundUiDelegate
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.AgentConnectionInterface
import com.protonvpn.android.vpn.CAPABILITY_NOT_VPN
import com.protonvpn.android.vpn.CAPABILITY_VALIDATED
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.ConnectivityMonitor
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.LocalAgentUnreachableTracker
import com.protonvpn.android.vpn.NetworkCapabilitiesFlow
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
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import com.protonvpn.mocks.FakeSettingsFeatureFlagsFlow
import com.protonvpn.mocks.MockAgentProvider
import com.protonvpn.mocks.MockVpnBackend
import com.protonvpn.mocks.createInMemoryServerManager
import com.protonvpn.test.shared.MockNetworkManager
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createServer
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import me.proton.core.auth.test.fake.FakeIsCredentialLessEnabled
import me.proton.core.network.domain.NetworkStatus
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
class VpnConnectionTestsIntegration {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    private val permissionDelegate = VpnPermissionDelegate { null }
    private lateinit var scope: TestScope
    private lateinit var testDispatcherProvider: TestDispatcherProvider
    private lateinit var monitor: VpnStateMonitor
    private lateinit var manager: VpnConnectionManager
    private lateinit var networkManager: MockNetworkManager
    private lateinit var networkCapabilitiesFlow: MutableStateFlow<Map<String, Boolean>?>
    private lateinit var serverManager: ServerManager

    private lateinit var currentUserProvider: TestCurrentUserProvider

    @RelaxedMockK
    lateinit var appConfig: AppConfig

    @MockK
    lateinit var certificateRepository: CertificateRepository

    @MockK
    lateinit var vpnErrorHandler: VpnConnectionErrorHandler

    @RelaxedMockK
    lateinit var mockAgent: AgentConnectionInterface

    @MockK
    lateinit var mockGhSuppressor: GuestHoleSuppressor

    @RelaxedMockK
    lateinit var mockVpnUiDelegate: VpnUiDelegate

    @MockK
    lateinit var mockVpnBackgroundUiDelegate: VpnBackgroundUiDelegate

    @RelaxedMockK
    lateinit var getNetZone: GetNetZone

    @RelaxedMockK
    lateinit var appFeaturesPrefs: AppFeaturesPrefs

    @MockK
    lateinit var foregroundActivityTracker: ForegroundActivityTracker

    @RelaxedMockK
    lateinit var mockLocalAgentUnreachableTracker: LocalAgentUnreachableTracker

    @MockK
    private lateinit var mockConnectionTelemetrySentryDebugEnabled: ConnectionTelemetrySentryDebugEnabled

    @RelaxedMockK
    lateinit var mockTelemetry: Telemetry

    private lateinit var featureFlagsFlow: MutableStateFlow<FeatureFlags>
    private lateinit var mockOpenVpn: MockVpnBackend
    private lateinit var mockWireguard: MockVpnBackend
    private lateinit var settingsForConnection: SettingsForConnection
    private lateinit var supportsProtocol: SupportsProtocol
    private lateinit var vpnStatusProviderUI: VpnStatusProviderUI
    private lateinit var userSettingsFlow: MutableStateFlow<LocalUserSettings>

    private val connectIntentFastest = ConnectIntent.FastestInCountry(CountryId.fastest, emptySet())
    private val guestHoleIntent = AnyConnectIntent.GuestHole(MockedServers.server.serverId)
    private val connectIntentCountry =
        ConnectIntent.FastestInCountry(CountryId(MockedServers.server.exitCountry), emptySet())
    private val connectIntentGuestHole = AnyConnectIntent.GuestHole(MockedServers.server.serverId)
    private val connectIntentNoServer = ConnectIntent.Server("nonexistent", CountryId.sweden, emptySet())

    private val serverWireguard: Server = MockedServers.serverList[1]
    private val fallbackServer: Server = MockedServers.serverList[2]

    private lateinit var switchServerFlow: MutableSharedFlow<VpnFallbackResult>

    private val agentConsts = LocalAgent.constants()
    private val validCert =
        CertificateRepository.CertificateResult.Success("good_cert", "good_key")
    private lateinit var currentCert: CertificateRepository.CertificateResult.Success
    private val trigger = ConnectTrigger.Auto("test")
    private val guestHoleUiDelegate: VpnUiDelegate = GuestHole.GuestHoleVpnUiDelegate(mockk())

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val testScheduler = TestCoroutineScheduler()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        scope = TestScope(testDispatcher)
        val bgScope = scope.backgroundScope
        testDispatcherProvider = TestDispatcherProvider(testDispatcher)
        Dispatchers.setMain(testDispatcher)
        val clock = { testScheduler.currentTime }

        userSettingsFlow = MutableStateFlow(LocalUserSettings.Default)

        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .buildDatabase()

        val profilesDao = db.profilesDao()
        val smartProtocolsConfig = SmartProtocolConfig(
            openVPNUdpEnabled = true, openVPNTcpEnabledInternal = true, wireguardEnabled = true, wireguardTcpEnabled = true, wireguardTlsEnabled = true)
        every { appConfig.getSmartProtocolConfig() } returns smartProtocolsConfig

        featureFlagsFlow = MutableStateFlow(FeatureFlags())
        val getFeatureFlags = GetFeatureFlags(featureFlagsFlow)
        every { appConfig.getFeatureFlags() } answers { featureFlagsFlow.value }

        supportsProtocol = SupportsProtocol(createGetSmartProtocols(smartProtocolsConfig.getSmartProtocols()))
        currentUserProvider = TestCurrentUserProvider(vpnUser = TestUser.plusUser.vpnUser)
        val currentUser = CurrentUser(currentUserProvider)

        every { mockGhSuppressor.disableGh() } returns false
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

        monitor = VpnStateMonitor()
        vpnStatusProviderUI = VpnStatusProviderUI(bgScope, monitor)
        networkManager = MockNetworkManager()
        networkCapabilitiesFlow = MutableStateFlow(mapOf(
            CAPABILITY_VALIDATED to true,
            CAPABILITY_NOT_VPN to false
        ))

        settingsForConnection = SettingsForConnection(
            rawSettingsFlow = userSettingsFlow,
            getProfileById = GetProfileByIdImpl(profilesDao),
            applyEffectiveUserSettings = ApplyEffectiveUserSettings(
                mainScope = scope.backgroundScope,
                currentUser = currentUser,
                isTv = mockk(relaxed = true),
                flags = FakeSettingsFeatureFlagsFlow(),
            ),
            vpnStatusProviderUI = vpnStatusProviderUI
        )

        mockOpenVpn = spyk(createMockVpnBackend(currentUser, VpnProtocol.OpenVPN))
        mockWireguard = spyk(createMockVpnBackend(currentUser, VpnProtocol.WireGuard))

        switchServerFlow = MutableSharedFlow()
        coEvery { vpnErrorHandler.switchConnectionFlow } returns switchServerFlow

        val backendProvider = ProtonVpnBackendProvider(
            openVpn = mockOpenVpn,
            wireGuard = mockWireguard,
            config = appConfig,
            supportsProtocol = supportsProtocol
        )


        val serverListUpdaterPrefs = ServerListUpdaterPrefs(MockSharedPreferencesProvider())
        val mockConnectivityMonitor = mockk<ConnectivityMonitor>()
        every { mockConnectivityMonitor.defaultNetworkTransports } returns setOf(ConnectivityMonitor.Transport.WIFI)
        coEvery { mockConnectionTelemetrySentryDebugEnabled.invoke() } returns true
        val vpnConnectionTelemetry = VpnConnectionTelemetry(
            scope.backgroundScope,
            clock,
            DefaultCommonDimensions(currentUser, monitor, serverListUpdaterPrefs, FakeIsCredentialLessEnabled(true)),
            monitor,
            mockConnectivityMonitor,
            TelemetryFlowHelper(scope.backgroundScope, DefaultTelemetryReporter(mockTelemetry)),
            mockConnectionTelemetrySentryDebugEnabled,
        ).apply { start() }

        serverManager = createInMemoryServerManager(
            scope,
            testDispatcherProvider,
            supportsProtocol,
            MockedServers.serverList,
            builtInGuestHoles = MockedServers.serverList.filter { supportsProtocol(it, GuestHole.PROTOCOL) }
        )
        val serverManager2 = ServerManager2(serverManager, supportsProtocol)
        manager = VpnConnectionManager(permissionDelegate, getFeatureFlags, settingsForConnection,
            { backendProvider }, networkManager, vpnErrorHandler, monitor, mockVpnBackgroundUiDelegate,
            serverManager2, certificateRepository, scope.backgroundScope, clock, mockk(relaxed = true),
            currentUser, supportsProtocol, { mockk(relaxed = true) }, vpnConnectionTelemetry, mockk(relaxed = true),
            mockk(relaxed = true))

        MockNetworkManager.currentStatus = NetworkStatus.Unmetered

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
        val mockAgentProvider: MockAgentProvider = { certInfo, _, client ->
            scope.launch {
                yield() // Don't set state immediately, yield for mockAgent to be returned first.
                action(client)
            }
            every { mockAgent.lastState } answers { client.lastState }
            every { mockAgent.certInfo } answers { certInfo }
            mockAgent
        }

        mockWireguard.setAgentProvider(mockAgentProvider)
        mockOpenVpn.setAgentProvider(mockAgentProvider)
    }

    @Test
    fun whenScanFailsForWireguardThenOpenVpnIsUsed() = scope.runTest {
        mockWireguard.failScanning = true
        manager.connect(mockVpnUiDelegate, connectIntentFastest, trigger)

        Assert.assertEquals(VpnState.Connected, monitor.state)
        Assert.assertEquals(VpnProtocol.OpenVPN, monitor.status.value.connectionParams?.protocolSelection?.vpn)
    }

    @Test
    fun whenFeatureFlagIsOffNoConnectionIsMade() = scope.runTest {
        featureFlagsFlow.value = FeatureFlags(wireguardTlsEnabled = false)
        userSettingsFlow.update { it.copy(protocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS)) }
        manager.connect(mockVpnUiDelegate, connectIntentFastest, trigger)

        verify { mockVpnUiDelegate.onProtocolNotSupported() }
        Assert.assertEquals(VpnState.Disabled, monitor.state)
    }

    @Test
    fun whenScanForAllProtocolsFailsThenDefaultProtocolIsUsed() = scope.runTest {
        mockWireguard.failScanning = true
        mockOpenVpn.failScanning = true
        userSettingsFlow.update { it.copy(protocol = ProtocolSelection(VpnProtocol.Smart)) }
        manager.connect(mockVpnUiDelegate, connectIntentFastest, trigger)

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
        userSettingsFlow.update { it.copy(protocol = ProtocolSelection(VpnProtocol.Smart)) }
        manager.connect(mockVpnUiDelegate, connectIntentFastest, trigger)

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
        userSettingsFlow.update {
            it.copy(protocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP))
        }
        manager.connect(mockVpnUiDelegate, connectIntentFastest, trigger)

        coVerify(exactly = 1) {
            mockWireguard.prepareForConnection(any(), any(), any(),false)
            mockWireguard.createAgentConnection(any(), any(), any(), any())
        }
        Assert.assertEquals(VpnState.Connected, monitor.state)
    }

    @Test
    fun profileProtocolIsRespectedForConnection() = scope.runTest {
        userSettingsFlow.update {
            it.copy(protocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP))
        }
        val overrideProtocol = ProtocolSelectionData(VpnProtocol.OpenVPN, TransmissionProtocol.UDP)
        manager.connect(
            mockVpnUiDelegate,
            connectIntentFastest.copy(settingsOverrides = profileSettingsOverrides(protocolData = overrideProtocol)),
            trigger
        )
        assertEquals(VpnState.Connected, monitor.state)
        assertEquals(overrideProtocol.toProtocolSelection(), monitor.status.value.connectionParams?.protocolSelection)
    }

    @Test
    fun localAgentIsNotUsedForGuesthole() = scope.runTest {
        MockNetworkManager.currentStatus = NetworkStatus.Disconnected
        currentUserProvider.set(null, null)
        manager.connect(guestHoleUiDelegate, guestHoleIntent, ConnectTrigger.GuestHole)

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
        currentUserProvider.set(null, null)

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
            appFeaturesPrefs,
            mockGhSuppressor,
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
            appFeaturesPrefs,
            mockGhSuppressor,
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
        manager.connect(mockVpnUiDelegate, connectIntentGuestHole, trigger)
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
            appFeaturesPrefs,
            mockGhSuppressor,
        )

        manager.connect(mockVpnUiDelegate, connectIntentFastest, trigger)

        every { foregroundActivityTracker.foregroundActivity } returns mockk<ComponentActivity>()
        guestHole.onAlternativesUnblock {
            Assert.fail()
        }

        Assert.assertTrue(monitor.isConnected)
    }

    @Test
    fun whenAuthErrorRequiresFallbackThenVpnConnectionIsReestablished() = scope.runTest {
        mockWireguard.stateOnConnect = VpnState.Error(ErrorType.AUTH_FAILED_INTERNAL, isFinal = false)
        val fallbackResult = VpnFallbackResult.Switch.SwitchConnectIntent(
            serverWireguard,
            fallbackServer,
            connectIntentFastest,
            connectIntentFastest,
            SwitchServerReason.Downgrade("PLUS", "FREE")
        )
        coEvery { vpnErrorHandler.onAuthError(any()) } answers {
            // Next connect attempt should succeed
            mockWireguard.stateOnConnect = VpnState.Connected
            fallbackResult
        }

        val fallbacks = runWhileCollecting(monitor.vpnConnectionNotificationFlow) {
            manager.connect(mockVpnUiDelegate, connectIntentFastest, trigger)
        }

        coVerify(exactly = 2) {
            mockWireguard.connect(not(isNull()))
        }

        Assert.assertEquals(VpnState.Connected, monitor.state)
        Assert.assertEquals(connectIntentFastest, monitor.connectionIntent)
        Assert.assertEquals(fallbackServer, monitor.connectingToServer)
        Assert.assertEquals(listOf(fallbackResult), fallbacks)
    }

    @Test
    fun whenAuthErrorDueToMaxSessionsThenDisconnectAndReportError() = scope.runTest {
        mockWireguard.stateOnConnect = VpnState.Error(ErrorType.AUTH_FAILED_INTERNAL, isFinal = false)
        val connectionParams: ConnectionParams = mockk()
        coEvery { vpnErrorHandler.onAuthError(any()) } returns
            VpnFallbackResult.Error(connectionParams, ErrorType.MAX_SESSIONS, reason = null)

        val fallbacks = runWhileCollecting(monitor.vpnConnectionNotificationFlow) {
            manager.connect(mockVpnUiDelegate, connectIntentFastest, trigger)
        }

        coVerify(exactly = 1) {
            mockWireguard.connect(not(isNull()))
        }

        Assert.assertEquals(VpnState.Disabled, monitor.state)
        Assert.assertEquals(
            listOf(VpnFallbackResult.Error(connectionParams, ErrorType.MAX_SESSIONS, reason = null)),
            fallbacks
        )
    }

    @Test
    fun whenLocalAgentReportsMaxSessionsThenDisconnectAndReportError() = scope.runTest {
        setupMockAgent { client ->
            client.onState(agentConsts.stateHardJailed)
            client.onError(agentConsts.errorCodeMaxSessionsPlus, "")
        }
        val notifications = runWhileCollecting(monitor.vpnConnectionNotificationFlow) {
            manager.connect(mockVpnUiDelegate, connectIntentFastest, trigger)
        }

        assertEquals(VpnState.Disabled, monitor.state)
        val expectedError = ErrorType.MAX_SESSIONS
        val actualError = notifications
            .filterIsInstance<VpnFallbackResult.Error>()
            .firstOrNull()?.type

        assertEquals(expectedError, actualError)
    }

    @Test
    fun whenUnreachableInternalStateIsReachedThenSwitchServer() = scope.runTest {
        mockWireguard.stateOnConnect = VpnState.Error(ErrorType.UNREACHABLE_INTERNAL, isFinal = false)

        val fallbackConnection = mockOpenVpn
            .prepareForConnection(connectIntentFastest, fallbackServer, emptySet(), true)
            .first()
        val fallbackResult = VpnFallbackResult.Switch.SwitchServer(serverWireguard,
            connectIntentFastest, fallbackConnection, SwitchServerReason.ServerUnreachable,
            compatibleProtocol = false, switchedSecureCore = false, notifyUser = true)
        coEvery { vpnErrorHandler.onUnreachableError(any()) } returns fallbackResult

        val fallbacks = runWhileCollecting(monitor.vpnConnectionNotificationFlow) {
            manager.connect(mockVpnUiDelegate, connectIntentFastest, trigger)
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

        manager.connect(mockVpnUiDelegate, connectIntentFastest, trigger)
        Assert.assertEquals(VpnState.Connected, monitor.state)

        val fallbackResult = VpnFallbackResult.Switch.SwitchConnectIntent(
            serverWireguard,
            fallbackServer,
            connectIntentFastest,
            connectIntentFastest,
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
        val offlineServerConnectIntent = ConnectIntent.fromServer(offlineServer, emptySet())
        coEvery {
            vpnErrorHandler.onServerInMaintenance(offlineServerConnectIntent, null)
        } returns VpnFallbackResult.Switch.SwitchConnectIntent(
            offlineServer,
            serverWireguard,
            fromConnectIntent = offlineServerConnectIntent,
            toConnectIntent = offlineServerConnectIntent,
            SwitchServerReason.ServerInMaintenance
        )

        // Returning false means fallback will be used.
        every { mockVpnUiDelegate.onServerRestricted(any()) } returns false

        manager.connect(mockVpnUiDelegate, offlineServerConnectIntent, trigger)

        Assert.assertEquals(VpnState.Connected, monitor.state)
        Assert.assertEquals(offlineServerConnectIntent, monitor.connectionIntent)
        Assert.assertEquals(serverWireguard, monitor.connectingToServer)

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
        val fallbackResult = VpnFallbackResult.Switch.SwitchConnectIntent(
            serverWireguard,
            fallbackServer,
            connectIntentFastest,
            connectIntentFastest,
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
        val onAuthErrorDeferred = CompletableDeferred<Unit>()
        coEvery { vpnErrorHandler.onAuthError(any()) } coAnswers {
            onAuthErrorDeferred.await()
            VpnFallbackResult.Error(mockk(), ErrorType.GENERIC_ERROR, reason = null)
        }
        manager.connect(mockVpnUiDelegate, connectIntentFastest, trigger)

        // Checking the intermediate state is fragile, but currently there's no better way.
        assertEquals(ErrorType.POLICY_VIOLATION_LOW_PLAN, (mockWireguard.selfState as? VpnState.Error)?.type)

        onAuthErrorDeferred.complete(Unit)
        coVerify(exactly = 1) { vpnErrorHandler.onAuthError(any()) }
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
    fun whenLocalAgentReportsCertificateRevokedThenGenerateNewKeyAndReconnect() = scope.runTest {
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
    fun whenLocalAgentReportsCertificateExpiredThenGetCertificateAndReconnectLocalAgent() = scope.runTest {
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
        coVerify(exactly = 1) { certificateRepository.updateCertificate(any(), any()) }
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
                VpnState.ScanningPorts,
                VpnState.Connecting,
                VpnState.Connected
            )
        )
        coVerify(exactly = 1) { certificateRepository.updateCertificate(any(), any()) }
    }

    @Test
    fun whenLocalAgentJailedWithExpiredCertDontRefreshIfNewCertAlreadyAvailable() = scope.runTest {
        currentCert = CertificateRepository.CertificateResult.Success("bad_cert", "good_key")
        mockLocalAgentErrorAndAssertStates(
            agentErrorState = { client ->
                // Simulate that certificate was updated in the meantime.
                currentCert = validCert

                client.onState(agentConsts.stateHardJailed)
                client.onError(agentConsts.errorCodeCertificateExpired, "")
            },
            expectedVpnStates = listOf(
                VpnState.Disabled,
                VpnState.ScanningPorts,
                VpnState.Connecting,
                VpnState.Connected
            ),
        )

        // Should not update certificate as new certificate was provided in the meantime.
        coVerify(exactly = 0) { certificateRepository.updateCertificate(any(), any()) }
    }

    @Test
    fun whenLocalAgentReportsUnknownCaThenUpdateCertificateAndReconnectLocalAgent() = scope.runTest {
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
            manager.connect(mockVpnUiDelegate, connectIntentFastest, trigger)
        }
        assertEquals(2, localAgentConnectAttempt)
        assertEquals(expectedVpnStates, vpnStates.map { it.state })
        assertEquals(VpnState.Connected, mockWireguard.selfState)
    }

    @Test
    fun whenFreeUserConnectsToSecureCoreServerThenUserIsNotified() = scope.runTest {
        val secureCoreIntent =
            ConnectIntent.SecureCore(exitCountry = CountryId.fastest, entryCountry = CountryId.fastest)
        currentUserProvider.vpnUser = TestUser.freeUser.vpnUser
        every { mockVpnUiDelegate.onServerRestricted(any()) } returns true

        manager.connect(mockVpnUiDelegate, secureCoreIntent, trigger)

        verify { mockVpnUiDelegate.onServerRestricted(ReasonRestricted.SecureCoreUpgradeNeeded) }
    }

    @Test
    fun testUnreachableInternalWhenLocalAgentUnreachableTrackerSignalsFallback() = scope.runTest {
        var nativeClient: VpnBackend.VpnAgentClient? = null
        mockWireguard.setAgentProvider { certificate, _, client ->
            nativeClient = client
            mockAgent
        }
        coEvery { vpnErrorHandler.onUnreachableError(any()) } returns VpnFallbackResult.Switch.SwitchConnectIntent(
            fromServer = null,
            toServer = createServer(),
            fromConnectIntent = connectIntentFastest,
            toConnectIntent = ConnectIntent.Default,
        )

        manager.connect(mockVpnUiDelegate, connectIntentFastest, trigger)
        assertNotNull(nativeClient)
        nativeClient.onState(agentConsts.stateConnected)
        assertEquals(VpnState.Connected, mockWireguard.selfState)

        every {
            mockLocalAgentUnreachableTracker.onUnreachable()
        } returns LocalAgentUnreachableTracker.UnreachableAction.ERROR
        nativeClient.onState(agentConsts.stateServerUnreachable)
        coVerify(exactly = 0) { vpnErrorHandler.onUnreachableError(any()) }

        every {
            mockLocalAgentUnreachableTracker.onUnreachable()
        } returns  LocalAgentUnreachableTracker.UnreachableAction.FALLBACK
        nativeClient.onState(agentConsts.stateServerUnreachable)
        coVerify { vpnErrorHandler.onUnreachableError(any()) }
    }

    @Test
    fun whenMaxSessionsOnConnectThenTelemetryReportsConnectFailure() = scope.runTest {
        setupMockAgent { client ->
            client.onState(agentConsts.stateHardJailed)
            client.onError(agentConsts.errorCodeMaxSessionsPlus, "")
        }
        manager.connect(mockVpnUiDelegate, connectIntentFastest, trigger)

        val dimensions = slot<Map<String, String>>()
        verify(exactly = 1) {
            mockTelemetry.event(VpnConnectionTelemetry.MEASUREMENT_GROUP, "vpn_connection", any(), capture(dimensions))
        }
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
        manager.connect(mockVpnUiDelegate, connectIntentFastest, trigger)

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
        val switch = VpnFallbackResult.Switch.SwitchConnectIntent(
            null, MockedServers.server, connectIntentFastest, connectIntentFastest, SwitchServerReason.ServerUnavailable
        )
        fallbackOnConnectReportsSingleEventTest(switch, "success")
    }

    @Test
    fun whenCompatibleServerFallbackOnConnectThenTelemetryReportsASingleEvent() = scope.runTest {
        val switch = createServerSwitch(
            connectIntentFastest, VpnProtocol.OpenVPN, mockOpenVpn, MockedServers.server, compatibleProtocol = true
        )
        fallbackOnConnectReportsSingleEventTest(switch, "success")
    }

    @Test
    fun whenIncompatibleServerFallbackOnConnectThenTelemetryReportsASingleEvent() = scope.runTest {
        val switch = createServerSwitch(
            connectIntentFastest, VpnProtocol.OpenVPN, mockOpenVpn, MockedServers.server, compatibleProtocol = false
        )
        fallbackOnConnectReportsSingleEventTest(switch, "failure")
    }

    @Test
    fun whenReconnectingToFallbackThenTelemetryReportsDisconnectWithSuccess() = scope.runTest {
        manager.connect(mockVpnUiDelegate, connectIntentCountry, trigger)

        val switch = VpnFallbackResult.Switch.SwitchConnectIntent(
            null, MockedServers.server, connectIntentFastest, connectIntentFastest, SwitchServerReason.ServerUnavailable
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
        coEvery { mockWireguard.prepareForConnection(connectIntentGuestHole, any(), any(), any()) } returns emptyList()

        manager.connect(mockVpnUiDelegate, connectIntentGuestHole, ConnectTrigger.GuestHole)

        val dimensions = slot<Map<String, String>>()
        verifyOrder {
            mockTelemetry.event(VpnConnectionTelemetry.MEASUREMENT_GROUP, "vpn_connection", any(), capture(dimensions))
        }
        assertEquals("failure", dimensions.captured["outcome"])
    }

    private fun fallbackOnConnectReportsSingleEventTest(
        fallbackSwitch: VpnFallbackResult.Switch,
        expectedOutcome: String
    ) {
        coEvery {
            vpnErrorHandler.onServerNotAvailable(connectIntentNoServer)
        } returns fallbackSwitch

        manager.connect(mockVpnUiDelegate, connectIntentNoServer, ConnectTrigger.QuickConnect("test"))

        val dimensions = slot<Map<String, String>>()
        verify(exactly = 1) {
            mockTelemetry.event(VpnConnectionTelemetry.MEASUREMENT_GROUP, "vpn_connection", any(), capture(dimensions))
        }
        assertEquals(expectedOutcome, dimensions.captured["outcome"])
        assertEquals("quick", dimensions.captured["vpn_trigger"]) // Initial trigger is reported.
    }

    private fun createMockVpnBackend(
        currentUser: CurrentUser,
        protocol: VpnProtocol,
    ): MockVpnBackend =
        MockVpnBackend(
            scope.backgroundScope,
            testDispatcherProvider,
            networkManager,
            NetworkCapabilitiesFlow(networkCapabilitiesFlow),
            certificateRepository,
            settingsForConnection,
            protocol,
            mockLocalAgentUnreachableTracker,
            currentUser,
            getNetZone,
            foregroundActivityTracker,
            GetConnectingDomain(supportsProtocol),
        )

    private fun createServerSwitch(
        orgConnectIntent: ConnectIntent,
        protocol: VpnProtocol,
        backend: VpnBackend,
        newServer: Server,
        compatibleProtocol: Boolean
    ): VpnFallbackResult.Switch.SwitchServer {
        val fallbackConnectionParams =  ConnectionParams(
            orgConnectIntent,
            newServer,
            newServer.connectingDomains.first(),
            protocol
        )
        val fallbackConnection = PrepareResult(backend, fallbackConnectionParams)
        return VpnFallbackResult.Switch.SwitchServer(
            null,
            orgConnectIntent,
            fallbackConnection,
            SwitchServerReason.ServerUnavailable,
            compatibleProtocol = compatibleProtocol,
            switchedSecureCore = false,
            notifyUser = false
        )
    }
}
