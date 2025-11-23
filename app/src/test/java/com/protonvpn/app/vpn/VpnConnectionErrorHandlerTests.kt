/*
 * Copyright (c) 2021 Proton AG
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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.login.Session
import com.protonvpn.android.models.login.SessionListResponse
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.ConnectionParamsOpenVpn
import com.protonvpn.android.models.vpn.ConnectionParamsWireguard
import com.protonvpn.android.models.vpn.usecase.GetConnectingDomain
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.servers.UpdateServerListFromApi
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.servers.api.ConnectingDomain
import com.protonvpn.android.servers.api.ConnectingDomainResponse
import com.protonvpn.android.servers.api.SERVER_FEATURE_RESTRICTED
import com.protonvpn.android.servers.api.SERVER_FEATURE_TOR
import com.protonvpn.android.servers.api.ServerEntryInfo
import com.protonvpn.android.servers.toServers
import com.protonvpn.android.settings.data.ApplyEffectiveUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.GetOnlineServersForIntent
import com.protonvpn.android.vpn.PhysicalServer
import com.protonvpn.android.vpn.PrepareResult
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.StuckConnectionHandler
import com.protonvpn.android.vpn.SwitchServerReason
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnConnectionErrorHandler
import com.protonvpn.android.vpn.VpnConnectionErrorHandler.Companion.SERVER_ERROR_COOLDOWN_MS
import com.protonvpn.android.vpn.VpnErrorUIManager
import com.protonvpn.android.vpn.VpnFallbackResult
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.mocks.FakeGetProfileById
import com.protonvpn.mocks.FakeSettingsFeatureFlagsFlow
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.TestVpnUser
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createServer
import com.protonvpn.test.shared.mockVpnUser
import io.mockk.CapturingSlot
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.HttpResponseCodes
import me.proton.core.network.domain.NetworkManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.EnumSet
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

@ExperimentalCoroutinesApi
class VpnConnectionErrorHandlerTests {

    private lateinit var testScope: TestScope
    private lateinit var handler: VpnConnectionErrorHandler
    private lateinit var getOnlineServersForIntent: GetOnlineServersForIntent
    private val directConnectServer = MockedServers.server
    private val directConnectIntent = ConnectIntent.fromServer(directConnectServer, emptySet())
    private lateinit var directConnectionParams: ConnectionParams
    private val defaultFallbackConnection = ConnectIntent.Default
    private val defaultFallbackServer = MockedServers.serverList[1] // Use a different server than MockedServers.server
    private lateinit var infoChangeFlow: MutableSharedFlow<List<UserPlanManager.InfoChange>>
    private lateinit var userSettingsFlow: MutableStateFlow<LocalUserSettings>
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var serverListVersion: MutableStateFlow<Int>

    @MockK private lateinit var api: ProtonApiRetroFit
    @RelaxedMockK private lateinit var userPlanManager: UserPlanManager
    @MockK private lateinit var appConfig: AppConfig
    @MockK private lateinit var serverManager2: ServerManager2
    @RelaxedMockK private lateinit var serverListUpdater: ServerListUpdater
    @RelaxedMockK private lateinit var networkManager: NetworkManager
    @RelaxedMockK private lateinit var vpnBackendProvider: VpnBackendProvider
    @RelaxedMockK private lateinit var currentUser: CurrentUser
    @RelaxedMockK private lateinit var errorUIManager: VpnErrorUIManager

    @get:Rule var rule = InstantTaskExecutorRule()

    private fun prepareServerManager(serverList: List<Server>) {
        // TODO: consider using the real ServerManager
        val servers = serverList.sortedBy { it.score }
        coEvery { serverManager2.getOnlineAccessibleServers(false, any(), any(), any()) } returns
            servers.filter { !it.isSecureCoreServer }
        coEvery { serverManager2.getOnlineAccessibleServers(true, any(), any(), any()) } returns
            servers.filter { it.isSecureCoreServer }
        coEvery { serverManager2.getOnlineAccessibleServers(any(), any(), any(), any()) } answers {
            servers.filter { it.gatewayName == secondArg() }
        }
        coEvery { serverManager2.getBestServerForConnectIntent(defaultFallbackConnection, any(), any()) } returns defaultFallbackServer

        coEvery { serverManager2.getServerById(any()) } answers {
            servers.find { it.serverId == arg(0) }
        }
        coEvery { serverManager2.updateServerDomainStatus(any()) } just runs
        every { serverManager2.serverListVersion } returns serverListVersion
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        ProtonApplication.setAppContextForTest(mockk(relaxed = true))
        Storage.setPreferences(MockSharedPreference())

        mockkObject(CountryTools)
        val countryCapture = slot<String>()
        every { CountryTools.getFullName(capture(countryCapture)) } answers { countryCapture.captured }

        infoChangeFlow = MutableSharedFlow()
        every { userPlanManager.infoChangeFlow } returns infoChangeFlow
        currentUser.mockVpnUser { TestVpnUser.create(maxTier = 2, maxConnect = 2) }
        every { appConfig.isMaintenanceTrackerEnabled() } returns true
        every { appConfig.getSmartProtocols() } returns ProtocolSelection.REAL_PROTOCOLS
        every { networkManager.isConnectedToNetwork() } returns true
        coEvery { api.getSession() } returns ApiResult.Success(SessionListResponse(1000, listOf()))
        serverListVersion = MutableStateFlow(0)
        prepareServerManager(MockedServers.serverList)

        val supportsProtocol = SupportsProtocol(createGetSmartProtocols())
        val getConnectingDomain = GetConnectingDomain(supportsProtocol)

        val protocol = ProtocolSelection(VpnProtocol.WireGuard)
        val connectingDomain = getConnectingDomain.random(directConnectServer, protocol)!!
        directConnectionParams = ConnectionParamsWireguard(directConnectIntent, directConnectServer, 443,
            connectingDomain, connectingDomain.getEntryIp(protocol), protocol.transmission!!, ipv6SettingEnabled = true)

        vpnStateMonitor = VpnStateMonitor()
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(
            VpnState.Error(ErrorType.UNREACHABLE_INTERNAL, isFinal = false),
            directConnectionParams
        ))

        testScope = TestScope(UnconfinedTestDispatcher())
        val vpnStatusProviderUI = VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor)
        userSettingsFlow = MutableStateFlow(LocalUserSettings.Default)
        getOnlineServersForIntent = GetOnlineServersForIntent(serverManager2, supportsProtocol)
        val settingsForConnection = SettingsForConnection(
            rawSettingsFlow = userSettingsFlow,
            getProfileById = FakeGetProfileById(),
            applyEffectiveUserSettings = ApplyEffectiveUserSettings(
                mainScope = testScope.backgroundScope,
                currentUser = currentUser,
                isTv = mockk(relaxed = true),
                flags = FakeSettingsFeatureFlagsFlow(),
            ),
            vpnStatusProviderUI = vpnStatusProviderUI
        )
        handler = VpnConnectionErrorHandler(testScope.backgroundScope, api, appConfig,
            settingsForConnection, userPlanManager, serverManager2, vpnStateMonitor, serverListUpdater,
            networkManager, dagger.Lazy { vpnBackendProvider }, currentUser, getConnectingDomain, getOnlineServersForIntent, testScope::currentTime, errorUIManager)
    }

    @Test
    fun testAuthErrorDelinquent() = testScope.runTest {
        coEvery {
            userPlanManager.computeUserInfoChanges(any(), any())
        } returns listOf(UserPlanManager.InfoChange.UserBecameDelinquent)
        assertEquals(
            VpnFallbackResult.Switch.SwitchConnectIntent(
                directConnectionParams.server,
                defaultFallbackServer,
                directConnectIntent,
                defaultFallbackConnection,
                SwitchServerReason.UserBecameDelinquent
            ),
            handler.onAuthError(directConnectionParams)
        )
    }

    @Test
    fun testAuthErrorDowngrade() = testScope.runTest {
        coEvery {
            userPlanManager.computeUserInfoChanges(any(), any())
        } returns listOf(UserPlanManager.InfoChange.PlanChange(TestUser.plusUser.vpnUser, TestUser.freeUser.vpnUser))
        currentUser.mockVpnUser { TestVpnUser.create(maxTier = 1) }

        assertEquals(
            VpnFallbackResult.Switch.SwitchConnectIntent(
                directConnectionParams.server,
                defaultFallbackServer,
                directConnectIntent,
                defaultFallbackConnection,
                SwitchServerReason.Downgrade("vpnplus", "free")
            ),
            handler.onAuthError(directConnectionParams))

        currentUser.mockVpnUser { TestVpnUser.create(maxTier = 0) }

        assertEquals(
            VpnFallbackResult.Switch.SwitchConnectIntent(
                directConnectionParams.server,
                defaultFallbackServer,
                directConnectionParams.connectIntent as ConnectIntent,
                defaultFallbackConnection,
                SwitchServerReason.Downgrade("vpnplus", "free")
            ),
            handler.onAuthError(directConnectionParams)
        )
    }

    @Test
    fun testAuthErrorVpnCredentials() = testScope.runTest {
        coEvery {
            userPlanManager.computeUserInfoChanges(any(), any())
        } returns listOf(UserPlanManager.InfoChange.VpnCredentials)
        assertEquals(
            VpnFallbackResult.Switch.SwitchConnectIntent(
                directConnectionParams.server,
                directConnectionParams.server,
                directConnectIntent,
                directConnectIntent,
                null
            ),
            handler.onAuthError(directConnectionParams)
        )
    }

    @Test
    fun testAuthErrorMaxSessions() = testScope.runTest {
        coEvery { userPlanManager.computeUserInfoChanges(any(), any()) } returns listOf()
        coEvery { api.getSession() } returns ApiResult.Success(
            SessionListResponse(1000, listOf(Session("1", "1"), Session("2", "2")))
        )
        assertEquals(
            VpnFallbackResult.Error(directConnectionParams, ErrorType.MAX_SESSIONS, reason = null),
            handler.onAuthError(directConnectionParams)
        )
    }

    private fun preparePings(
        failCountry: String? = null,
        failServerName: String? = null,
        failServerEntryIp: String? = null,
        failAll: Boolean = false,
        useOpenVPN: Boolean = false,
        failSecureCore: Boolean = false,
    ): CapturingSlot<PrepareResult> {
        val result = CapturingSlot<PrepareResult>()
        coEvery { vpnBackendProvider.pingAll(any(), any(), any(), any()) } answers {
            val originalConnectIntent = firstArg<AnyConnectIntent>()
            val pingedServers = thirdArg<List<PhysicalServer>>()
            if (failAll)
                null
            else {
                val server = pingedServers.firstOrNull { physicalServer ->
                    with(physicalServer.server) {
                        !(failSecureCore && isSecureCoreServer) &&
                            exitCountry != failCountry && serverName != failServerName &&
                            (failServerEntryIp == null || connectingDomains.any { it.getEntryIp(null) != failServerEntryIp })
                    }
                }
                if (server == null)
                    null
                else {
                    val connectionParams = if (useOpenVPN) {
                        ConnectionParamsOpenVpn(
                            originalConnectIntent,
                            server.server,
                            server.connectingDomain,
                            server.connectingDomain.getEntryIp(
                                ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.UDP)),
                            TransmissionProtocol.UDP,
                            443,
                            ipv6SettingEnabled = true,
                        )
                    } else {
                        ConnectionParamsWireguard(
                            originalConnectIntent,
                            server.server,
                            443,
                            server.connectingDomain,
                            server.connectingDomain.getEntryIp(
                                ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP)),
                            TransmissionProtocol.UDP,
                            ipv6SettingEnabled = true,
                        )
                    }
                    result.captured = PrepareResult(mockk(), connectionParams)
                    VpnBackendProvider.PingResult(server, listOf(result.captured))
                }
            }
        }
        return result
    }

    private fun putDomainInMaintenance(maintenanceId: String) {
        val idSlot = slot<String>()
        coEvery { api.getConnectingDomain(capture(idSlot)) } answers {
            val mockedDomain = mockk<ConnectingDomain>(relaxed = true)
            every { mockedDomain.isOnline } returns (idSlot.captured != maintenanceId)
            every { mockedDomain.id } returns idSlot.captured
            ApiResult.Success(ConnectingDomainResponse(mockedDomain))
        }
    }

    @Test
    fun testAuthErrorMaintenanceFallback() = testScope.runTest {
        coEvery { userPlanManager.computeUserInfoChanges(any(), any()) } returns listOf()

        val maintenanceDomain = directConnectionParams.connectingDomain!!
        putDomainInMaintenance(maintenanceDomain.id!!)

        val pingResult = preparePings(failCountry = directConnectServer.exitCountry, failSecureCore = true)

        val fallback = handler.onAuthError(directConnectionParams)
        println(fallback)
        assertEquals(
            VpnFallbackResult.Switch.SwitchServer(
                directConnectionParams.server,
                pingResult.captured.connectionParams.connectIntent as ConnectIntent,
                pingResult.captured,
                SwitchServerReason.ServerInMaintenance,
                notifyUser = true, // Country is not compatible
                compatibleProtocol = true,
                switchedSecureCore = false),
            fallback
        )

        coVerify(exactly = 1) { serverListUpdater.updateServerList() }
        coVerify(exactly = 1) { serverManager2.updateServerDomainStatus(any()) }
    }

    @Test
    fun testAuthErrorServerRemovedFallback() = testScope.runTest {
        coEvery { userPlanManager.computeUserInfoChanges(any(), any()) } returns listOf()

        val maintenanceDomain = directConnectionParams.connectingDomain!!
        coEvery { api.getConnectingDomain(maintenanceDomain.id!!) } answers {
            ApiResult.Error.Http(HttpResponseCodes.HTTP_UNPROCESSABLE, "domain doesn't exist")
        }

        val pingResult = preparePings(failCountry = directConnectServer.exitCountry, failSecureCore = true)
        val fallback = handler.onAuthError(directConnectionParams)
        assertEquals(
            VpnFallbackResult.Switch.SwitchServer(
                directConnectionParams.server,
                pingResult.captured.connectionParams.connectIntent as ConnectIntent,
                pingResult.captured,
                SwitchServerReason.ServerInMaintenance,
                notifyUser = true, // Country is not compatible
                compatibleProtocol = true,
                switchedSecureCore = false),
            fallback
        )

        coVerify(exactly = 1) { serverListUpdater.updateServerList() }
    }

    @Test
    fun testUnreachableFallback() = testScope.runTest {
        val pingResult = preparePings(failServerName = directConnectionParams.server.serverName, failSecureCore = true)

        val fallback = handler.onUnreachableError(directConnectionParams) as VpnFallbackResult.Switch.SwitchServer
        assertEquals("CA#1", directConnectionParams.server.serverName)
        assertEquals("CA#2", fallback.toServer.serverName)
        assertEquals(
            VpnFallbackResult.Switch.SwitchServer(
                directConnectionParams.server,
                pingResult.captured.connectionParams.connectIntent as ConnectIntent,
                pingResult.captured,
                SwitchServerReason.ServerUnreachable,
                notifyUser = false, // CA#2 is compatible with CA#1, switch silently
                compatibleProtocol = true,
                switchedSecureCore = false),
            fallback
        )
    }

    @Test
    fun testUnreachableNoneResponded() = testScope.runTest {
        preparePings(failAll = true, failSecureCore = true)
        assertEquals(
            VpnFallbackResult.Error(directConnectionParams, ErrorType.UNREACHABLE, SwitchServerReason.ServerUnreachable),
            handler.onUnreachableError(directConnectionParams)
        )
    }

    @Test
    fun testUnreachableOrgServerResponded() = testScope.runTest {
        preparePings(failSecureCore = true) // All servers respond
        assertEquals(
            VpnFallbackResult.Error(directConnectionParams, ErrorType.UNREACHABLE, SwitchServerReason.ServerUnreachable),
            handler.onUnreachableError(directConnectionParams)
        )
    }

    @Test
    fun testSwitchOnServerError() = testScope.runTest {
        preparePings(failSecureCore = true) // All servers respond
        val fallback = handler.onServerError(directConnectionParams)

        // After server error we should switch to a different one
        fallback.let {
            assertIs<VpnFallbackResult.Switch.SwitchServer>(it)
            assertNotEquals(directConnectServer.serverId, it.toServer.serverName)
        }
    }

    @Test
    fun testServerErrorCooldown() = testScope.runTest {
        preparePings(failSecureCore = true) // All servers respond
        var fallback = handler.onServerError(directConnectionParams)
        assertIs<VpnFallbackResult.Switch.SwitchServer>(fallback)

        advanceTimeBy(SERVER_ERROR_COOLDOWN_MS / 2)
        fallback = handler.onServerError(directConnectionParams)
        assertIs<VpnFallbackResult.Error>(fallback)
        assertEquals(ErrorType.UNREACHABLE, fallback.type)

        advanceTimeBy(SERVER_ERROR_COOLDOWN_MS / 2)
        fallback = handler.onServerError(directConnectionParams)
        assertIs<VpnFallbackResult.Switch.SwitchServer>(fallback)
    }

    @Test
    fun testUnreachableOrgServerStuck() = testScope.runTest {
        preparePings(failSecureCore = true) // All servers respond

        handler.onUnreachableError(directConnectionParams)
        advanceTimeBy(StuckConnectionHandler.STUCK_DURATION_MS)
        val fallback = handler.onUnreachableError(directConnectionParams)

        // After we're stuck in current server, we should switch to a different one
        fallback.let {
            assertIs<VpnFallbackResult.Switch.SwitchServer>(it)
            assertNotEquals(directConnectServer.serverId, it.toServer.serverName)
        }
    }

    @Test
    fun testUnreachableOrgServerStuckResetsOnConnection() = testScope.runTest {
        preparePings(failSecureCore = true) // All servers respond

        handler.onUnreachableError(directConnectionParams)
        advanceTimeBy(StuckConnectionHandler.STUCK_DURATION_MS * 3 / 4)
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, directConnectionParams))
        advanceTimeBy(StuckConnectionHandler.STUCK_DURATION_MS * 3 / 4)
        val fallback = handler.onUnreachableError(directConnectionParams)
        assertEquals(
            VpnFallbackResult.Error(directConnectionParams, ErrorType.UNREACHABLE, SwitchServerReason.ServerUnreachable),
            fallback
        )
    }

    @Test
    fun testUnreachableOrgServerStuckResetsOnNoNetwork() = testScope.runTest {
        preparePings(failSecureCore = true) // All servers respond

        handler.onUnreachableError(directConnectionParams)
        advanceTimeBy(StuckConnectionHandler.STUCK_DURATION_MS * 3 / 4)
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.WaitingForNetwork, directConnectionParams))
        advanceTimeBy(StuckConnectionHandler.STUCK_DURATION_MS * 3 / 4)
        val fallback = handler.onUnreachableError(directConnectionParams)

        assertEquals(
            VpnFallbackResult.Error(directConnectionParams, ErrorType.UNREACHABLE, SwitchServerReason.ServerUnreachable),
            fallback
        )
    }

    @Test
    fun testUnreachableOrgServerRespondsWithDifferentProtocol() = testScope.runTest {
        userSettingsFlow.value =
            userSettingsFlow.value.copy(protocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP))
        preparePings(useOpenVPN = true, failSecureCore = true)
        val fallback = handler.onUnreachableError(directConnectionParams) as VpnFallbackResult.Switch.SwitchServer
        assertFalse(fallback.compatibleProtocol)
        assertEquals(SwitchServerReason.ServerUnreachable, fallback.reason)
        assertEquals("CA#1", fallback.preparedConnection.connectionParams.server.serverName)
        assertTrue(fallback.preparedConnection.connectionParams is ConnectionParamsOpenVpn)
    }

    @Test
    fun testFallbackUsesTheOriginalUserIntent() = testScope.runTest {
        val connectIntent =
            ConnectIntent.FastestInCity(CountryId.switzerland, cityEn = "Zurich", EnumSet.of(ServerFeature.P2P))
        val connectionParams = ConnectionParamsWireguard(
            connectIntent,
            directConnectServer,
            443,
            directConnectServer.connectingDomains.first(),
            null,
            TransmissionProtocol.UDP,
            ipv6SettingEnabled = true
        )
        preparePings(failServerName = directConnectServer.serverName, failSecureCore = true)
        val fallback = handler.onUnreachableError(connectionParams)
        assertIs<VpnFallbackResult.Switch.SwitchServer>(fallback)
        assertEquals(connectIntent, fallback.connectIntent)
    }

    @Test
    fun testUnreachableSecureCoreSwitch() = testScope.runTest {
        val secureCoreServer = MockedServers.serverList.find { it.serverName == "SE-FI#1" }!!
        val secureCoreIntent = ConnectIntent.SecureCore(entryCountry = CountryId("se"), exitCountry = CountryId("fi"))
        val protocol = ProtocolSelection(VpnProtocol.WireGuard)
        val connectingDomain = secureCoreServer.connectingDomains.first()
        val scConnectionParams = ConnectionParamsWireguard(secureCoreIntent, secureCoreServer, 443,
            connectingDomain, connectingDomain.getEntryIp(protocol), protocol.transmission!!, ipv6SettingEnabled = true)

        preparePings(failServerName = secureCoreServer.serverName)
        val fallback = handler.onUnreachableError(scConnectionParams) as VpnFallbackResult.Switch.SwitchServer
        assertEquals("CH-FI#1", fallback.preparedConnection.connectionParams.server.serverName)
        assertFalse(fallback.notifyUser) // fallback is compatible
    }

    @Test
    fun testUnreachableSecureCoreSwitchToNonSecureCore() = testScope.runTest {
        val scServer = MockedServers.serverList.find { it.serverName == "SE-FI#1" }!!
        val scIntent =
            ConnectIntent.SecureCore(exitCountry = CountryId("fi"), entryCountry = CountryId("se"))
        val protocol = ProtocolSelection(VpnProtocol.WireGuard)
        val connectingDomain = scServer.connectingDomains.first()
        val scConnectionParams = ConnectionParamsWireguard(scIntent, scServer, 443,
            connectingDomain, connectingDomain.getEntryIp(protocol), protocol.transmission!!, ipv6SettingEnabled = true)

        // All secure core servers failed to respond, switch to non-sc in the same country.
        preparePings(failSecureCore = true)
        val fallback = handler.onUnreachableError(scConnectionParams) as VpnFallbackResult.Switch.SwitchServer
        assertEquals("FI#1", fallback.preparedConnection.connectionParams.server.serverName)
        assertEquals(SwitchServerReason.ServerUnreachable, fallback.reason)
        assertTrue(fallback.switchedSecureCore)
    }

    @Test
    fun testUnreachableSwitchesToSameServerWithDifferentIp() = testScope.runTest {
        val initialLogicals = listOf(MockedServers.logicalsList[0], MockedServers.logicalsList[1])
        assertEquals(1, initialLogicals[0].connectingDomains.size)
        val initialServer1Domain = initialLogicals[0].connectingDomains[0]
        val updatedLogicals = listOf(
            initialLogicals[0].copy(
                connectingDomains = listOf(
                    initialLogicals[0].connectingDomains[0].copy(
                        entryIp = "123.0.0.3"
                    )
                )
            ),
            initialLogicals[1].copy()
        )
        val initialServers = initialLogicals.toServers()
        val updatedServers = updatedLogicals.toServers()
        coEvery { serverListUpdater.needsUpdate() } returns true
        coEvery { serverListUpdater.updateServerList() } answers {
            prepareServerManager(updatedServers)
            UpdateServerListFromApi.Result.Success
        }

        prepareServerManager(initialServers)
        val fastestIntent = ConnectIntent.FastestInCountry(CountryId.fastest, emptySet())
        val connectionParams = ConnectionParams(fastestIntent, initialServers[0], initialServer1Domain, null)

        val prepareResult = preparePings()
        val fallback = handler.onUnreachableError(connectionParams)
        assertEquals(
            VpnFallbackResult.Switch.SwitchServer(
                initialServers[0],
                fastestIntent,
                prepareResult.captured,
                SwitchServerReason.ServerUnreachable,
                compatibleProtocol = true,
                switchedSecureCore = false,
                notifyUser = false
            ),
            fallback
        )
        assertEquals(updatedServers[0], prepareResult.captured.connectionParams.server)
    }

    @Test
    fun testIgnoringTorServers() = testScope.runTest {
        val server1 = MockedServers.serverList[0]
        val server2 = MockedServers.serverList[1].copy(features = SERVER_FEATURE_TOR)
        val servers = listOf(server1, server2)
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.fastest, emptySet())
        prepareServerManager(servers)
        preparePings(failServerName = server1.serverName)
        val connectionParams = ConnectionParams(connectIntent, server1, server1.connectingDomains.first(), VpnProtocol.WireGuard)
        val result = handler.onUnreachableError(connectionParams)
        assertEquals(
            VpnFallbackResult.Error(connectionParams, ErrorType.UNREACHABLE, SwitchServerReason.ServerUnreachable),
            result
        )
    }

    @Test
    fun testAcceptingTorWhenOriginalIsTor() = testScope.runTest {
        val server1 = MockedServers.serverList[0].copy(features = SERVER_FEATURE_TOR)
        val server2 = MockedServers.serverList[1].copy(features = SERVER_FEATURE_TOR)
        val servers = listOf(server1, server2)
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.fastest, EnumSet.of(ServerFeature.Tor))
        prepareServerManager(servers)
        preparePings(failServerName = server1.serverName)
        val result = handler.onUnreachableError(
            ConnectionParams(connectIntent, server1, server1.connectingDomains.first(), VpnProtocol.WireGuard)
        )
        assertEquals(server2, (result as VpnFallbackResult.Switch.SwitchServer).toServer)
    }

    @Test
    fun testSwitchingToServerSupportingOrgProtocol() = testScope.runTest {
        val server1 = MockedServers.serverList[0]
        val orgServer2 = MockedServers.serverList[1]
        val protocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP)
        val server2 = orgServer2.copy(connectingDomains = listOf(orgServer2.connectingDomains.first().copy(
            entryIp = null,
            entryIpPerProtocol = mapOf(protocol.apiName to ServerEntryInfo("7.7.7.7"))
        )))
        val servers = listOf(server1, server2)
        prepareServerManager(servers)
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.fastest, emptySet())
        preparePings(failServerName = server1.serverName)
        val result = handler.onUnreachableError(
            ConnectionParams(connectIntent, server1, server1.connectingDomains.first(), VpnProtocol.WireGuard)
        )
        assertEquals(server2, (result as VpnFallbackResult.Switch.SwitchServer).toServer)
    }

    @Test
    fun testNotSwitchingToServerNotSupportingOrgProtocol() = testScope.runTest {
        val server1 = MockedServers.serverList[0]
        val orgServer2 = MockedServers.serverList[1]
        val protocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP)
        val server2 = orgServer2.copy(connectingDomains = listOf(orgServer2.connectingDomains.first().copy(
            entryIp = null,
            entryIpPerProtocol = mapOf(protocol.apiName to ServerEntryInfo("7.7.7.7"))
        )))
        val servers = listOf(server1, server2)
        val connectIntent = ConnectIntent.fromServer(server1, emptySet())
        prepareServerManager(servers)
        preparePings(failServerName = server1.serverName)
        val connectionParams = ConnectionParams(
            connectIntent,
            server1,
            server1.connectingDomains.first(),
            VpnProtocol.OpenVPN
        )
        val result = handler.onUnreachableError(connectionParams)
        assertEquals(
            VpnFallbackResult.Error(connectionParams, ErrorType.UNREACHABLE, SwitchServerReason.ServerUnreachable),
            result
        )
    }

    @Test
    fun testSwitchingFromGatewayServerDoesntFallBackToRegularServer() = testScope.runTest {
        val gatewayServer = createServer(
            "gateway",
            serverName = "Gateway#1",
            gatewayName = "GatewayName",
            features = SERVER_FEATURE_RESTRICTED
        )
        val servers = listOf(MockedServers.serverList.first(), gatewayServer)
        prepareServerManager(servers)
        preparePings(failServerName = gatewayServer.serverName)
        val connectIntent = ConnectIntent.Gateway("GatewayName", null)
        val connectionParams = ConnectionParams(
            connectIntent,
            gatewayServer,
            gatewayServer.connectingDomains.first(),
            VpnProtocol.WireGuard
        )
        val result = handler.onUnreachableError(connectionParams)
        assertEquals(
            VpnFallbackResult.Error(connectionParams, ErrorType.UNREACHABLE, SwitchServerReason.ServerUnreachable),
            result
        )
    }

    @Test
    fun testTrackingVpnInfoChanges() = testScope.runTest {
        val currentServer: Server = createServer("id")
        val currentConnectIntent = ConnectIntent.fromServer(currentServer, emptySet())

        currentUser.mockVpnUser { TestVpnUser.create(maxTier = 0) }
        testTrackingVpnInfoChanges(
            listOf(UserPlanManager.InfoChange.PlanChange(TestUser.plusUser.vpnUser, TestUser.freeUser.vpnUser)),
            VpnFallbackResult.Switch.SwitchConnectIntent(
                currentServer,
                defaultFallbackServer,
                currentConnectIntent,
                defaultFallbackConnection,
                SwitchServerReason.Downgrade("vpnplus", "free")
            )
        )

        testTrackingVpnInfoChanges(
            listOf(UserPlanManager.InfoChange.UserBecameDelinquent),
            VpnFallbackResult.Switch.SwitchConnectIntent(
                currentServer,
                defaultFallbackServer,
                currentConnectIntent,
                defaultFallbackConnection,
                SwitchServerReason.UserBecameDelinquent
            )
        )
    }

    private suspend fun testTrackingVpnInfoChanges(
        infoChange: List<UserPlanManager.InfoChange>,
        fallback: VpnFallbackResult.Switch.SwitchConnectIntent
    ) = coroutineScope {
        launch {
            val event = handler.switchConnectionFlow.first() as VpnFallbackResult.Switch.SwitchConnectIntent
            assertEquals(fallback.reason, event.reason)
        }
        infoChangeFlow.emit(infoChange)
    }
}
