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
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.login.Session
import com.protonvpn.android.models.login.SessionListResponse
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.ConnectingDomainResponse
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.ConnectionParamsIKEv2
import com.protonvpn.android.models.vpn.ConnectionParamsOpenVpn
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.PhysicalServer
import com.protonvpn.android.vpn.PrepareResult
import com.protonvpn.android.vpn.SwitchServerReason
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnConnectionErrorHandler
import com.protonvpn.android.vpn.VpnErrorUIManager
import com.protonvpn.android.vpn.VpnFallbackResult
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.mockVpnUser
import io.mockk.CapturingSlot
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.NetworkManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class VpnConnectionErrorHandlerTests {

    private lateinit var handler: VpnConnectionErrorHandler
    private lateinit var directProfile: Profile
    private lateinit var directConnectionParams: ConnectionParams
    private val defaultFallbackConnection = Profile("fastest", null, mockk(), null)
    private val infoChangeFlow = MutableSharedFlow<List<UserPlanManager.InfoChange>>()

    @MockK private lateinit var api: ProtonApiRetroFit
    @MockK private lateinit var userData: UserData
    @MockK private lateinit var userPlanManager: UserPlanManager
    @MockK private lateinit var vpnStateMonitor: VpnStateMonitor
    @MockK private lateinit var appConfig: AppConfig
    @RelaxedMockK private lateinit var serverManager: ServerManager
    @RelaxedMockK private lateinit var serverListUpdater: ServerListUpdater
    @RelaxedMockK private lateinit var networkManager: NetworkManager
    @RelaxedMockK private lateinit var vpnBackendProvider: VpnBackendProvider
    @RelaxedMockK private lateinit var currentUser: CurrentUser
    @RelaxedMockK private lateinit var vpnUser: VpnUser
    @RelaxedMockK private lateinit var errorUIManager: VpnErrorUIManager

    @get:Rule var rule = InstantTaskExecutorRule()

    private fun prepareServerManager() {
        val servers = MockedServers.serverList.sortedBy { it.score }
        every { serverManager.getOnlineAccessibleServers(false) } returns servers.filter { !it.isSecureCoreServer }
        every { serverManager.getOnlineAccessibleServers(true) } returns servers.filter { it.isSecureCoreServer }
        val wrapperSlot = slot<ServerWrapper>()
        every { serverManager.getServer(capture(wrapperSlot)) } answers {
            val id = wrapperSlot.captured.serverId
            MockedServers.serverList.find { it.serverId == id }
        }
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        ProtonApplication.setAppContextForTest(mockk(relaxed = true))

        mockkObject(CountryTools)
        val countryCapture = slot<String>()
        every { CountryTools.getFullName(capture(countryCapture)) } answers { countryCapture.captured }

        every { userPlanManager.infoChangeFlow } returns infoChangeFlow
        every { serverManager.defaultFallbackConnection } returns defaultFallbackConnection
        currentUser.mockVpnUser { vpnUser }
        every { vpnUser.maxConnect } returns 2
        every { vpnUser.userTier } returns 2
        every { appConfig.isMaintenanceTrackerEnabled() } returns true
        every { appConfig.getFeatureFlags().vpnAccelerator } returns true
        every { networkManager.isConnectedToNetwork() } returns true
        every { userData.secureCoreEnabled } returns false
        every { userData.selectedProtocol } returns VpnProtocol.Smart
        every { vpnStateMonitor.isEstablishingOrConnected } returns false
        coEvery { api.getSession() } returns ApiResult.Success(SessionListResponse(1000, listOf()))
        prepareServerManager()

        val server = MockedServers.server
        directProfile = Profile.getTempProfile(server, serverManager)
        directProfile.setProtocol(VpnProtocol.IKEv2)
        directConnectionParams = ConnectionParamsIKEv2(directProfile, server, server.getRandomConnectingDomain())

        handler = VpnConnectionErrorHandler(TestCoroutineScope(), api, appConfig,
            userData, userPlanManager, serverManager, vpnStateMonitor, serverListUpdater,
            networkManager, vpnBackendProvider, currentUser, errorUIManager)
    }

    @Test
    fun testAuthErrorDelinquent() = runBlockingTest {
        coEvery { userPlanManager.refreshVpnInfo() } returns listOf(UserPlanManager.InfoChange.UserBecameDelinquent)
        assertEquals(
            VpnFallbackResult.Switch.SwitchProfile(directConnectionParams.server, defaultFallbackConnection, SwitchServerReason.UserBecameDelinquent),
            handler.onAuthError(directConnectionParams))
    }

    @Test
    fun testAuthErrorDowngrade() = runBlockingTest {
        coEvery { userPlanManager.refreshVpnInfo() } returns listOf(UserPlanManager.InfoChange.PlanChange.Downgrade("vpnplus", "free"))
        every { vpnUser.isFreeUser } returns false
        every { vpnUser.isBasicUser } returns true

        assertEquals(
            VpnFallbackResult.Switch.SwitchProfile(
                directConnectionParams.server,
                defaultFallbackConnection,
                SwitchServerReason.Downgrade("vpnplus", "free")
            ),
            handler.onAuthError(directConnectionParams))

        every { vpnUser.isFreeUser } returns true
        every { vpnUser.isBasicUser } returns false

        assertEquals(
            VpnFallbackResult.Switch.SwitchProfile(
                directConnectionParams.server,
                defaultFallbackConnection,
                SwitchServerReason.Downgrade("vpnplus", "free")
            ),
            handler.onAuthError(directConnectionParams))
    }

    @Test
    fun testAuthErrorVpnCredentials() = runBlockingTest {
        coEvery { userPlanManager.refreshVpnInfo() } returns listOf(UserPlanManager.InfoChange.VpnCredentials)
        assertEquals(
            VpnFallbackResult.Switch.SwitchProfile(directConnectionParams.server, directProfile, null),
            handler.onAuthError(directConnectionParams))
    }

    @Test
    fun testAuthErrorMaxSessions() = runBlockingTest {
        coEvery { userPlanManager.refreshVpnInfo() } returns listOf()
        coEvery { api.getSession() } returns ApiResult.Success(SessionListResponse(1000,
            listOf(Session("1", "1"), Session("2", "2"))))
        assertEquals(
            VpnFallbackResult.Error(ErrorType.MAX_SESSIONS),
            handler.onAuthError(directConnectionParams))
    }

    private fun preparePings(
        failCountry: String? = null,
        failServerName: String? = null,
        failAll: Boolean = false,
        useOpenVPN: Boolean = false,
        failSecureCore: Boolean = false,
    ): CapturingSlot<PrepareResult> {
        val result = CapturingSlot<PrepareResult>()
        val pingedServers = slot<List<PhysicalServer>>()
        coEvery { vpnBackendProvider.pingAll(capture(pingedServers), any()) } answers {
            if (failAll)
                null
            else {
                val server = pingedServers.captured.first {
                    !(failSecureCore && it.server.isSecureCoreServer) &&
                        it.server.exitCountry != failCountry && it.server.serverName != failServerName
                }
                val profile = Profile.getTempProfile(server.server, serverManager)
                val connectionParams = if (useOpenVPN) {
                    ConnectionParamsOpenVpn(
                        profile, server.server, server.connectingDomain, TransmissionProtocol.UDP, 443)
                } else {
                    ConnectionParamsIKEv2(profile, server.server, server.connectingDomain)
                }
                result.captured = PrepareResult(mockk(), connectionParams)
                VpnBackendProvider.PingResult(profile, server, listOf(result.captured))
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
    fun testAuthErrorMaintenanceFallback() = runBlockingTest {
        coEvery { userPlanManager.refreshVpnInfo() } returns listOf()

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
            fallback)

        coVerify(exactly = 1) { serverListUpdater.updateServerList() }
        coVerify(exactly = 1) { serverManager.updateServerDomainStatus(any()) }
    }

    @Test
    fun testUnreachableFallback() = runBlockingTest {
        val pingResult = preparePings(failServerName = directConnectionParams.server.serverName, failSecureCore = true)

        val fallback = handler.onUnreachableError(directConnectionParams) as VpnFallbackResult.Switch.SwitchServer
        assertEquals("CA#1", directConnectionParams.server.serverName)
        assertEquals("CA#2", fallback.toProfile.directServer?.serverName)
        assertEquals(
            VpnFallbackResult.Switch.SwitchServer(
                directConnectionParams.server,
                pingResult.captured.connectionParams.profile,
                pingResult.captured,
                SwitchServerReason.ServerUnreachable,
                notifyUser = false, // CA#2 is compatible with CA#1, switch silently
                compatibleProtocol = true,
                switchedSecureCore = false),
            fallback)
    }

    @Test
    fun testUnreachableNoneResponded() = runBlockingTest {
        preparePings(failAll = true, failSecureCore = true)
        assertEquals(VpnFallbackResult.Error(ErrorType.UNREACHABLE), handler.onUnreachableError(directConnectionParams))
    }

    @Test
    fun testUnreachableOrgServerResponded() = runBlockingTest {
        preparePings(failSecureCore = true) // All servers respond
        assertEquals(VpnFallbackResult.Error(ErrorType.UNREACHABLE), handler.onUnreachableError(directConnectionParams))
    }

    @Test
    fun testUnreachableOrgServerRespondsWithDifferentProtocol() = runBlockingTest {
        preparePings(useOpenVPN = true, failSecureCore = true)
        val fallback = handler.onUnreachableError(directConnectionParams) as VpnFallbackResult.Switch.SwitchServer
        assertFalse(fallback.compatibleProtocol)
        assertEquals(SwitchServerReason.ServerUnreachable, fallback.reason)
        assertEquals("CA#1", fallback.preparedConnection.connectionParams.server.serverName)
        assertTrue(fallback.preparedConnection.connectionParams is ConnectionParamsOpenVpn)
    }

    @Test
    fun testUnreachableSecureCoreSwitch() = runBlockingTest {
        val secureCoreServer = MockedServers.serverList.find { it.serverName == "SE-FI#1" }!!
        val secureCoreProfile = Profile.getTempProfile(secureCoreServer, serverManager)
        secureCoreProfile.setProtocol(VpnProtocol.IKEv2)
        val scConnectionParams = ConnectionParamsIKEv2(
            secureCoreProfile, secureCoreServer, secureCoreServer.connectingDomains.first())

        preparePings(failServerName = secureCoreServer.serverName)
        val fallback = handler.onUnreachableError(scConnectionParams) as VpnFallbackResult.Switch.SwitchServer
        assertEquals("FR-FI#1", fallback.preparedConnection.connectionParams.server.serverName)
        assertFalse(fallback.notifyUser) // fallback is compatible
    }

    @Test
    fun testUnreachableSecureCoreSwitchToNonSecureCore() = runBlockingTest {
        val scServer = MockedServers.serverList.find { it.serverName == "SE-FI#1" }!!
        val scProfie = Profile.getTempProfile(scServer, serverManager)
        scProfie.setProtocol(VpnProtocol.IKEv2)
        val scConnectionParams = ConnectionParamsIKEv2(scProfie, scServer, scServer.connectingDomains.first())

        // All secure core servers failed to respond, switch to non-sc in the same country.
        preparePings(failSecureCore = true)
        val fallback = handler.onUnreachableError(scConnectionParams) as VpnFallbackResult.Switch.SwitchServer
        assertEquals("FI#1", fallback.preparedConnection.connectionParams.server.serverName)
        assertEquals(SwitchServerReason.ServerUnreachable, fallback.reason)
        assertTrue(fallback.switchedSecureCore)
    }

    @Test
    fun testTrackingVpnInfoChanges() = runBlockingTest {
        every { vpnStateMonitor.isEstablishingOrConnected } returns true
        every { vpnStateMonitor.connectionParams } returns directConnectionParams
        val mockedServer: Server = mockk()

        every { vpnUser.isFreeUser } returns true
        every { vpnUser.isBasicUser } returns false
        testTrackingVpnInfoChanges(
            listOf(UserPlanManager.InfoChange.PlanChange.Downgrade("vpnplus", "free")),
            VpnFallbackResult.Switch.SwitchProfile(mockedServer, defaultFallbackConnection, SwitchServerReason.Downgrade("vpnplus", "free"))
        )

        testTrackingVpnInfoChanges(
            listOf(UserPlanManager.InfoChange.UserBecameDelinquent),
            VpnFallbackResult.Switch.SwitchProfile(mockedServer, defaultFallbackConnection, SwitchServerReason.UserBecameDelinquent)
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
