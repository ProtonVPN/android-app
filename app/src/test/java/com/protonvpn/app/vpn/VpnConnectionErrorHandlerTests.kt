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
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.login.Session
import com.protonvpn.android.models.login.SessionListResponse
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.ConnectingDomainResponse
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.SwitchServerReason
import com.protonvpn.android.vpn.VpnConnectionErrorHandler
import com.protonvpn.android.vpn.VpnFallbackResult
import com.protonvpn.android.vpn.VpnStateMonitor
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import me.proton.core.network.domain.ApiResult
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class VpnConnectionErrorHandlerTests {

    private lateinit var handler: VpnConnectionErrorHandler
    private val profile = Profile("p", "", mockk())
    private val fastest = Profile("fastest", "", mockk())
    private val infoChangeFlow = MutableSharedFlow<List<UserPlanManager.InfoChange>>()

    @MockK private lateinit var api: ProtonApiRetroFit
    @MockK private lateinit var userData: UserData
    @MockK private lateinit var userPlanManager: UserPlanManager
    @MockK private lateinit var vpnStateMonitor: VpnStateMonitor
    @MockK private lateinit var appConfig: AppConfig
    @RelaxedMockK private lateinit var serverManager: ServerManager
    @RelaxedMockK private lateinit var serverListUpdater: ServerListUpdater

    @get:Rule var rule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { userPlanManager.infoChangeFlow } returns infoChangeFlow
        every { serverManager.defaultFallbackConnection } returns fastest
        every { userData.vpnInfoResponse?.maxSessionCount } returns 2
        every { appConfig.isMaintenanceTrackerEnabled() } returns true
        coEvery { api.getSession() } returns ApiResult.Success(SessionListResponse(1000, listOf()))

        ProtonApplication.setAppContextForTest(mockk(relaxed = true))
        handler = VpnConnectionErrorHandler(TestCoroutineScope(), mockk(relaxed = true), api, appConfig,
            userData, userPlanManager, serverManager, vpnStateMonitor, serverListUpdater, mockk(relaxed = true))
    }

    @Test
    fun testAuthErrorTrialEnd() = runBlockingTest {
        coEvery { userPlanManager.refreshVpnInfo() } returns listOf(UserPlanManager.InfoChange.PlanChange.TrialEnded)
        assertEquals(
            VpnFallbackResult.SwitchProfile(fastest, SwitchServerReason.TrialEnded),
            handler.onAuthError(profile))
    }

    @Test
    fun testAuthErrorDelinquent() = runBlockingTest {
        coEvery { userPlanManager.refreshVpnInfo() } returns listOf(UserPlanManager.InfoChange.UserBecameDelinquent)
        assertEquals(
            VpnFallbackResult.SwitchProfile(fastest, SwitchServerReason.UserBecameDelinquent),
            handler.onAuthError(profile))
    }

    @Test
    fun testAuthErrorDowngrade() = runBlockingTest {
        coEvery { userPlanManager.refreshVpnInfo() } returns listOf(UserPlanManager.InfoChange.PlanChange.Downgrade)
        every { userData.isFreeUser } returns false
        every { userData.isBasicUser } returns true

        assertEquals(
            VpnFallbackResult.SwitchProfile(fastest, SwitchServerReason.DowngradeToBasic),
            handler.onAuthError(profile))

        every { userData.isFreeUser } returns true
        every { userData.isBasicUser } returns false

        assertEquals(
            VpnFallbackResult.SwitchProfile(fastest, SwitchServerReason.DowngradeToFree),
            handler.onAuthError(profile))
    }

    @Test
    fun testAuthErrorVpnCredentials() = runBlockingTest {
        coEvery { userPlanManager.refreshVpnInfo() } returns listOf(UserPlanManager.InfoChange.VpnCredentials)
        assertEquals(
            VpnFallbackResult.SwitchProfile(profile, null),
            handler.onAuthError(profile))
    }

    @Test
    fun testAuthErrorMaxSessions() = runBlockingTest {
        coEvery { userPlanManager.refreshVpnInfo() } returns listOf()
        coEvery { api.getSession() } returns ApiResult.Success(SessionListResponse(1000,
            listOf(Session("1", "1"), Session("2", "2"))))
        assertEquals(
            VpnFallbackResult.Error(ErrorType.MAX_SESSIONS),
            handler.onAuthError(profile))
    }

    @Test
    fun testAuthErrorMaintenance() = runBlockingTest {
        coEvery { userPlanManager.refreshVpnInfo() } returns listOf()

        val mockedDomain = mockk<ConnectingDomain>(relaxed = true)
        every { mockedDomain.isOnline } returns false
        every { vpnStateMonitor.connectionParams } returns ConnectionParams(profile, mockk(relaxed = true), mockedDomain, VpnProtocol.IKEv2)
        coEvery { api.getConnectingDomain(any()) } returns ApiResult.Success(ConnectingDomainResponse(mockedDomain))
        assertEquals(
            VpnFallbackResult.SwitchProfile(fastest, SwitchServerReason.ServerInMaintenance),
            handler.onAuthError(profile))

        coVerify(exactly = 1) { serverListUpdater.updateServerList() }
    }

    @Test
    fun testTrackingVpnInfoChanges() = runBlockingTest {
        testTrackingVpnInfoChanges(
            listOf(UserPlanManager.InfoChange.PlanChange.TrialEnded),
            VpnFallbackResult.SwitchProfile(fastest, SwitchServerReason.TrialEnded)
        )

        every { userData.isFreeUser } returns true
        every { userData.isBasicUser } returns false
        testTrackingVpnInfoChanges(
            listOf(UserPlanManager.InfoChange.PlanChange.Downgrade),
            VpnFallbackResult.SwitchProfile(fastest, SwitchServerReason.DowngradeToFree)
        )

        testTrackingVpnInfoChanges(
            listOf(UserPlanManager.InfoChange.UserBecameDelinquent),
            VpnFallbackResult.SwitchProfile(fastest, SwitchServerReason.UserBecameDelinquent)
        )
    }

    private suspend fun testTrackingVpnInfoChanges(
        infoChange: List<UserPlanManager.InfoChange>,
        fallback: VpnFallbackResult.SwitchProfile
    ) = coroutineScope {
        launch {
            val event = handler.switchConnectionFlow.first()
            assertEquals(fallback, event)
        }
        infoChangeFlow.emit(infoChange)
    }
}
