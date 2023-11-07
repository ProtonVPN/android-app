/*
 * Copyright (c) 2021 Proton Technologies AG
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
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.ConnectingDomainResponse
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.ConnectionParamsOpenVpn
import com.protonvpn.android.models.vpn.ConnectionParamsWireguard
import com.protonvpn.android.models.vpn.SERVER_FEATURE_RESTRICTED
import com.protonvpn.android.models.vpn.SERVER_FEATURE_TOR
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.ServerEntryInfo
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.android.models.vpn.usecase.GetConnectingDomain
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.PhysicalServer
import com.protonvpn.android.vpn.PrepareResult
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.SwitchServerReason
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnConnectionErrorHandler
import com.protonvpn.android.vpn.VpnErrorUIManager
import com.protonvpn.android.vpn.VpnFallbackResult
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.TestVpnUser
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createServer
import com.protonvpn.test.shared.mockVpnUser
import io.mockk.CapturingSlot
import io.mockk.MockKAnnotations
import io.mockk.called
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

@ExperimentalCoroutinesApi
class VpnConnectionErrorHandlerTests {

    private lateinit var testScope: TestScope
    private lateinit var handler: VpnConnectionErrorHandler
    private lateinit var directProfile: Profile
    private lateinit var directConnectionParams: ConnectionParams
    private val defaultFallbackConnection = Profile("fastest", null, mockk(), null, null)
    private val defaultFallbackServer = MockedServers.serverList[1] // Use a different server than MockedServers.server
    private lateinit var infoChangeFlow: MutableSharedFlow<List<UserPlanManager.InfoChange>>
    private lateinit var userSettingsFlow: MutableStateFlow<LocalUserSettings>

    @MockK private lateinit var api: ProtonApiRetroFit
    @RelaxedMockK private lateinit var userPlanManager: UserPlanManager
    @MockK private lateinit var vpnStateMonitor: VpnStateMonitor
    @MockK private lateinit var appConfig: AppConfig
    @MockK private lateinit var serverManager2: ServerManager2
    @MockK private lateinit var profileManager: ProfileManager
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
        every { profileManager.fallbackProfile } returns defaultFallbackConnection
        coEvery { serverManager2.getServerForProfile(defaultFallbackConnection, any()) } returns defaultFallbackServer

        coEvery { serverManager2.getServerById(any()) } answers {
            servers.find { it.serverId == arg(0) }
        }
        coEvery { serverManager2.updateServerDomainStatus(any()) } just runs
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        ProtonApplication.setAppContextForTest(mockk(relaxed = true))

        mockkObject(CountryTools)
        val countryCapture = slot<String>()
        every { CountryTools.getFullName(capture(countryCapture)) } answers { countryCapture.captured }

        infoChangeFlow = MutableSharedFlow()
        every { userPlanManager.infoChangeFlow } returns infoChangeFlow
        currentUser.mockVpnUser { TestVpnUser.create(maxTier = 2, maxConnect = 2) }
        every { appConfig.isMaintenanceTrackerEnabled() } returns true
        every { appConfig.getFeatureFlags().vpnAccelerator } returns true
        every { appConfig.getSmartProtocols() } returns ProtocolSelection.REAL_PROTOCOLS
        every { networkManager.isConnectedToNetwork() } returns true
        every { vpnStateMonitor.isEstablishingOrConnected } returns false
        coEvery { api.getSession() } returns ApiResult.Success(SessionListResponse(1000, listOf()))
        prepareServerManager(MockedServers.serverList)

        val supportsProtocol = SupportsProtocol(createGetSmartProtocols())
        val getConnectingDomain = GetConnectingDomain(supportsProtocol)

        val server = MockedServers.server
        val protocol = ProtocolSelection(VpnProtocol.WireGuard)
        val connectingDomain = getConnectingDomain.random(server, protocol)!!
        directProfile = Profile.getTempProfile(server)
        directProfile.setProtocol(protocol)
        directConnectionParams = ConnectionParamsWireguard(directProfile, server, 443,
            connectingDomain, connectingDomain.getEntryIp(protocol), protocol.transmission!!)

        testScope = TestScope(UnconfinedTestDispatcher())

        userSettingsFlow = MutableStateFlow(LocalUserSettings.Default)
        val userSettings = EffectiveCurrentUserSettings(testScope.backgroundScope, userSettingsFlow)
        handler = VpnConnectionErrorHandler(testScope.backgroundScope, api, appConfig,
            userSettings, userPlanManager, serverManager2, profileManager, vpnStateMonitor, serverListUpdater,
            networkManager, vpnBackendProvider, currentUser, getConnectingDomain, errorUIManager)
    }

    @Test
    fun testAuthErrorDelinquent() = testScope.runTest {
        coEvery {
            userPlanManager.computeUserInfoChanges(any(), any())
        } returns listOf(UserPlanManager.InfoChange.UserBecameDelinquent)
        assertEquals(
            VpnFallbackResult.Switch.SwitchProfile(
                directConnectionParams.server,
                defaultFallbackServer,
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
            VpnFallbackResult.Switch.SwitchProfile(
                directConnectionParams.server,
                defaultFallbackServer,
                defaultFallbackConnection,
                SwitchServerReason.Downgrade("vpnplus", "free")
            ),
            handler.onAuthError(directConnectionParams))

        currentUser.mockVpnUser { TestVpnUser.create(maxTier = 0) }

        assertEquals(
            VpnFallbackResult.Switch.SwitchProfile(
                directConnectionParams.server,
                defaultFallbackServer,
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
            VpnFallbackResult.Switch.SwitchProfile(
                directConnectionParams.server,
                directConnectionParams.server,
                directProfile,
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
            VpnFallbackResult.Error(ErrorType.MAX_SESSIONS),
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
        val pingedServers = slot<List<PhysicalServer>>()
        coEvery { vpnBackendProvider.pingAll(any(), capture(pingedServers), any()) } answers {
            if (failAll)
                null
            else {
                val server = pingedServers.captured.firstOrNull { physicalServer ->
                    with(physicalServer.server) {
                        !(failSecureCore && isSecureCoreServer) &&
                            exitCountry != failCountry && serverName != failServerName &&
                            (failServerEntryIp == null || connectingDomains.any { it.getEntryIp(null) != failServerEntryIp })
                    }
                }
                if (server == null)
                    null
                else {
                    val profile = Profile.getTempProfile(server.server)
                    val connectionParams = if (useOpenVPN) {
                        ConnectionParamsOpenVpn(
                            profile,
                            server.server,
                            server.connectingDomain,
                            server.connectingDomain.getEntryIp(
                                ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.UDP)),
                            TransmissionProtocol.UDP,
                            443)
                    } else {
                        ConnectionParamsWireguard(
                            profile,
                            server.server,
                            443,
                            server.connectingDomain,
                            server.connectingDomain.getEntryIp(
                                ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP)),
                            TransmissionProtocol.UDP)
                    }
                    result.captured = PrepareResult(mockk(), connectionParams)
                    VpnBackendProvider.PingResult(profile, server, listOf(result.captured))
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

        val pingResult = preparePings(failCountry = directProfile.country, failSecureCore = true)

        val fallback = handler.onAuthError(directConnectionParams)
        println(fallback)
        assertEquals(
            VpnFallbackResult.Switch.SwitchServer(
                directConnectionParams.server,
                pingResult.captured.connectionParams.profile,
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

        val pingResult = preparePings(failCountry = directProfile.country, failSecureCore = true)
        val fallback = handler.onAuthError(directConnectionParams)
        assertEquals(
            VpnFallbackResult.Switch.SwitchServer(
                directConnectionParams.server,
                pingResult.captured.connectionParams.profile,
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
                pingResult.captured.connectionParams.profile,
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
        assertEquals(VpnFallbackResult.Error(ErrorType.UNREACHABLE), handler.onUnreachableError(directConnectionParams))
    }

    @Test
    fun testUnreachableOrgServerResponded() = testScope.runTest {
        preparePings(failSecureCore = true) // All servers respond
        assertEquals(VpnFallbackResult.Error(ErrorType.UNREACHABLE), handler.onUnreachableError(directConnectionParams))
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
    fun testUnreachableSecureCoreSwitch() = testScope.runTest {
        val secureCoreServer = MockedServers.serverList.find { it.serverName == "SE-FI#1" }!!
        val secureCoreProfile = Profile.getTempProfile(secureCoreServer, true)
        val protocol = ProtocolSelection(VpnProtocol.WireGuard)
        secureCoreProfile.setProtocol(protocol)
        val connectingDomain = secureCoreServer.connectingDomains.first()
        val scConnectionParams = ConnectionParamsWireguard(secureCoreProfile, secureCoreServer, 443,
            connectingDomain, connectingDomain.getEntryIp(protocol), protocol.transmission!!)

        preparePings(failServerName = secureCoreServer.serverName)
        val fallback = handler.onUnreachableError(scConnectionParams) as VpnFallbackResult.Switch.SwitchServer
        assertEquals("CH-FI#1", fallback.preparedConnection.connectionParams.server.serverName)
        assertFalse(fallback.notifyUser) // fallback is compatible
    }

    @Test
    fun testUnreachableSecureCoreSwitchToNonSecureCore() = testScope.runTest {
        val scServer = MockedServers.serverList.find { it.serverName == "SE-FI#1" }!!
        val scProfie = Profile.getTempProfile(scServer, true)
        val protocol = ProtocolSelection(VpnProtocol.WireGuard)
        val connectingDomain = scServer.connectingDomains.first()
        scProfie.setProtocol(protocol)
        val scConnectionParams = ConnectionParamsWireguard(scProfie, scServer, 443,
            connectingDomain, connectingDomain.getEntryIp(protocol), protocol.transmission!!)

        // All secure core servers failed to respond, switch to non-sc in the same country.
        preparePings(failSecureCore = true)
        val fallback = handler.onUnreachableError(scConnectionParams) as VpnFallbackResult.Switch.SwitchServer
        assertEquals("FI#1", fallback.preparedConnection.connectionParams.server.serverName)
        assertEquals(SwitchServerReason.ServerUnreachable, fallback.reason)
        assertTrue(fallback.switchedSecureCore)
    }

    @Test
    fun testUnreachableSwitchesToSameServerWithDifferentIp() = testScope.runTest {
        val initialServers = listOf(MockedServers.serverList[0], MockedServers.serverList[1])
        assertEquals(1, initialServers[0].connectingDomains.size)
        val initialServer1Domain = initialServers[0].connectingDomains[0]
        val updatedServers = listOf(
            initialServers[0].copy(
                connectingDomains = listOf(
                    initialServers[0].connectingDomains[0].copy(
                        entryIp = "123.0.0.3"
                    )
                )
            ),
            initialServers[1].copy()
        )
        every { serverListUpdater.needsUpdate } returns true
        coEvery { serverListUpdater.updateServerList(any()) } answers {
            prepareServerManager(updatedServers)
            ApiResult.Success(ServerList(updatedServers))
        }

        prepareServerManager(initialServers)
        val fastestProfile = Profile.getTempProfile(ServerWrapper.makePreBakedFastest(), null)
        val connectionParams = ConnectionParams(fastestProfile, initialServers[0], initialServer1Domain, null)

        val prepareResult = preparePings()
        val fallback = handler.onUnreachableError(connectionParams)
        val preparedProfileId = prepareResult.captured.connectionParams.profile.id
        assertEquals(
            VpnFallbackResult.Switch.SwitchServer(
                initialServers[0],
                Profile("", null, ServerWrapper.makeWithServer(updatedServers[0]), null, null, null, null, preparedProfileId),
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
        prepareServerManager(servers)
        preparePings(failServerName = server1.serverName)
        val result = handler.onUnreachableError(ConnectionParams(
            Profile.getTempProfile(server1), server1, server1.connectingDomains.first(), VpnProtocol.WireGuard))
        assertEquals(VpnFallbackResult.Error(ErrorType.UNREACHABLE), result)
    }

    @Test
    fun testAcceptingTorWhenOriginalIsTor() = testScope.runTest {
        val server1 = MockedServers.serverList[0].copy(features = SERVER_FEATURE_TOR)
        val server2 = MockedServers.serverList[1].copy(features = SERVER_FEATURE_TOR)
        val servers = listOf(server1, server2)
        prepareServerManager(servers)
        preparePings(failServerName = server1.serverName)
        val result = handler.onUnreachableError(ConnectionParams(
            Profile.getTempProfile(server1), server1, server1.connectingDomains.first(), VpnProtocol.WireGuard))
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
        preparePings(failServerName = server1.serverName)
        val profile = Profile.getTempProfile(server1).copy(
            protocol = protocol.vpn.name,
            transmissionProtocol = protocol.transmission?.name)
        val result = handler.onUnreachableError(ConnectionParams(profile, server1,
            server1.connectingDomains.first(), VpnProtocol.WireGuard))
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
        prepareServerManager(servers)
        preparePings(failServerName = server1.serverName)
        val profile = Profile.getTempProfile(server1).copy(
            protocol = VpnProtocol.OpenVPN.name)
        val result = handler.onUnreachableError(ConnectionParams(profile, server1,
            server1.connectingDomains.first(), VpnProtocol.OpenVPN))
        assertEquals(VpnFallbackResult.Error(ErrorType.UNREACHABLE), result)
    }

    @Test
    fun testSwitchingFromGatewayServerDoesntFallBackToRegularServer() = testScope.runTest {
        val gatewayServer =
            createServer("gateway", serverName = "Gateway#1", features = SERVER_FEATURE_RESTRICTED)
        val servers = listOf(MockedServers.serverList.first(), gatewayServer)
        prepareServerManager(servers)
        preparePings(failServerName = gatewayServer.serverName)
        val profile = Profile.getTempProfile(gatewayServer)
        val result = handler.onUnreachableError(
            ConnectionParams(profile, gatewayServer, gatewayServer.connectingDomains.first(), VpnProtocol.WireGuard)
        )
        assertEquals(VpnFallbackResult.Error(ErrorType.UNREACHABLE), result)
    }

    @Test
    fun testTrackingVpnInfoChanges() = testScope.runTest {
        every { vpnStateMonitor.isEstablishingOrConnected } returns true
        every { vpnStateMonitor.connectionParams } returns directConnectionParams
        val mockedServer: Server = mockk()

        currentUser.mockVpnUser { TestVpnUser.create(maxTier = 0) }
        testTrackingVpnInfoChanges(
            listOf(UserPlanManager.InfoChange.PlanChange(TestUser.plusUser.vpnUser, TestUser.freeUser.vpnUser)),
            VpnFallbackResult.Switch.SwitchProfile(
                mockedServer,
                defaultFallbackServer,
                defaultFallbackConnection,
                SwitchServerReason.Downgrade("vpnplus", "free")
            )
        )

        testTrackingVpnInfoChanges(
            listOf(UserPlanManager.InfoChange.UserBecameDelinquent),
            VpnFallbackResult.Switch.SwitchProfile(
                mockedServer,
                defaultFallbackServer,
                defaultFallbackConnection,
                SwitchServerReason.UserBecameDelinquent
            )
        )
    }

    private suspend fun testTrackingVpnInfoChanges(
        infoChange: List<UserPlanManager.InfoChange>,
        fallback: VpnFallbackResult.Switch.SwitchProfile
    ) = coroutineScope {
        launch {
            val event = handler.switchConnectionFlow.first()
            assertEquals(fallback.reason, event.reason)
        }
        infoChangeFlow.emit(infoChange)
    }
}
