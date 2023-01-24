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

package com.protonvpn.app.ui.home

import android.telephony.TelephonyManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.LoadsResponse
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.android.models.vpn.UserLocation
import com.protonvpn.android.partnerships.PartnershipsRepository
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.netshield.NetShieldExperiment
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.utils.NetUtils
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.MockedServers
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.proton.core.network.domain.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

private const val TEST_IP = "1.2.3.4"
private const val OLD_IP = "10.0.0.1"

@OptIn(ExperimentalCoroutinesApi::class)
class ServerListUpdaterTests {

    @get:Rule
    val instantTaskExecutor = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockApi: ProtonApiRetroFit
    @MockK
    private lateinit var guestHole: GuestHole
    @RelaxedMockK
    private lateinit var mockServerManager: ServerManager
    @MockK
    private lateinit var mockCurrentUser: CurrentUser
    @RelaxedMockK
    private lateinit var mockVpnStateMonitor: VpnStateMonitor
    @RelaxedMockK
    private lateinit var mockPlanManager: UserPlanManager
    @RelaxedMockK
    private lateinit var mockTelephonyManager: TelephonyManager
    @RelaxedMockK
    private lateinit var mockNetshieldExperiment: NetShieldExperiment
    @MockK
    private lateinit var mockPartnershipsRepository: PartnershipsRepository

    private lateinit var testScope: TestScope
    private lateinit var serverListUpdaterPrefs: ServerListUpdaterPrefs
    private lateinit var vpnStatusFlow: MutableStateFlow<VpnStateMonitor.Status>

    private var clockMs: Long = 1_000_000

    private lateinit var serverListUpdater: ServerListUpdater

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)
        serverListUpdaterPrefs = ServerListUpdaterPrefs(MockSharedPreferencesProvider())
        serverListUpdaterPrefs.ipAddress = OLD_IP
        clockMs = 1_000_000
        vpnStatusFlow = MutableStateFlow(VpnStateMonitor.Status(VpnState.Disabled, null))

        coEvery { guestHole.runWithGuestHoleFallback(any<suspend () -> Any?>()) } coAnswers { firstArg<suspend () -> Any?>()() }
        coEvery { mockCurrentUser.isLoggedIn() } returns true
        every { mockServerManager.isDownloadedAtLeastOnce } returns true
        every { mockServerManager.isOutdated } returns false
        coEvery { mockApi.getStreamingServices() } returns ApiResult.Error.Timeout(false)
        coEvery { mockApi.getServerList(any(), any(), any(), any()) } returns ApiResult.Success(ServerList(emptyList()))
        every { mockVpnStateMonitor.onDisconnectedByUser } returns MutableSharedFlow()
        every { mockVpnStateMonitor.status } returns vpnStatusFlow
        coEvery { mockPartnershipsRepository.refresh() } returns Unit

        every { mockTelephonyManager.phoneType } returns TelephonyManager.PHONE_TYPE_GSM
        every { mockTelephonyManager.networkCountryIso } returns "ch"

        val getNetZone = GetNetZone(serverListUpdaterPrefs, { clockMs })
        serverListUpdater = ServerListUpdater(
            testScope.backgroundScope,
            mockApi,
            mockServerManager,
            mockCurrentUser,
            mockVpnStateMonitor,
            mockPlanManager,
            mockNetshieldExperiment,
            serverListUpdaterPrefs,
            { clockMs },
            getNetZone,
            mockPartnershipsRepository,
            guestHole
        )
    }

    @Test
    fun `location is updated when VPN is off`() = testScope.runTest {
        coEvery { mockApi.getLocation() } returns ApiResult.Success(UserLocation(TEST_IP, "pl", "ISP"))
        every { mockVpnStateMonitor.isDisabled } returns true

        val newNetzone = serverListUpdater.updateLocationIfVpnOff()
        assertEquals(NetUtils.stripIP(TEST_IP), newNetzone)
        assertEquals(TEST_IP, serverListUpdater.ipAddress.first())
        assertEquals("pl", serverListUpdater.lastKnownCountry)
        assertEquals("ISP", serverListUpdater.lastKnownIsp)
    }

    @Test
    fun `location is not updated when VPN is connected`() = testScope.runTest {
        every { mockVpnStateMonitor.isDisabled } returns false

        val newNetzone = serverListUpdater.updateLocationIfVpnOff()
        assertEquals(null, newNetzone)
        assertEquals(OLD_IP, serverListUpdaterPrefs.ipAddress)

        coVerify { mockApi wasNot Called }
    }

    @Test
    fun `location result is ignored if VPN connects during update`() = testScope.runTest {
        coEvery { mockApi.getLocation() } returns ApiResult.Success(UserLocation(TEST_IP, "pl", "ISP"))
        every { mockVpnStateMonitor.isDisabled } returnsMany listOf(true, false)

        val newNetzone = serverListUpdater.updateLocationIfVpnOff()
        assertEquals(null, newNetzone)
        assertEquals(OLD_IP, serverListUpdater.ipAddress.first())
        assertEquals(OLD_IP, serverListUpdaterPrefs.ipAddress)

        coVerify(exactly = 1) { mockApi.getLocation() }
    }

    @Test
    fun `location update is cancelled when VPN starts connecting during update`() = testScope.runTest {
        coEvery { mockApi.getLocation() } coAnswers {
            delay(1000)
            ApiResult.Success(UserLocation(TEST_IP, "pl", "ISP"))
        }
        every { mockVpnStateMonitor.isDisabled } returns true

        val newNetzoneDeferred = async {
            serverListUpdater.updateLocationIfVpnOff()
        }
        vpnStatusFlow.value = VpnStateMonitor.Status(VpnState.Connecting, null)
        val newNetzone = newNetzoneDeferred.await()

        assertEquals(null, newNetzone)
        assertEquals(OLD_IP, serverListUpdater.ipAddress.first())
        assertEquals(OLD_IP, serverListUpdaterPrefs.ipAddress)
        coVerify(exactly = 1) { mockApi.getLocation() }
    }

    @Test
    fun `update task updates location 4 minutes after previous check`() = testScope.runTest {
        coEvery { mockApi.getLocation() } returnsMany listOf(
            ApiResult.Success(UserLocation(OLD_IP, "pl", "ISP")),
            ApiResult.Success(UserLocation(TEST_IP, "pl", "ISP")),
        )
        every { mockVpnStateMonitor.isDisabled } returns true
        serverListUpdater.updateTask()

        clockMs += TimeUnit.MINUTES.toMillis(2)
        serverListUpdater.updateTask()
        assertEquals(OLD_IP, serverListUpdater.ipAddress.first())
        coVerify(exactly = 1) { mockApi.getLocation() }

        clockMs += TimeUnit.MINUTES.toMillis(2)
        serverListUpdater.updateTask()
        assertEquals(TEST_IP, serverListUpdater.ipAddress.first())
    }

    @Test
    fun `update task updates server list when outdated`() = testScope.runTest {
        val servers = listOf(MockedServers.server)
        coEvery { mockApi.getLocation() } returns ApiResult.Success(UserLocation(OLD_IP, "pl", "ISP"))
        coEvery { mockApi.getServerList(any(), any(), any(), any()) } returns ApiResult.Success(ServerList(servers))
        every { mockVpnStateMonitor.isDisabled } returns true
        serverListUpdater.updateTask()

        every { mockServerManager.isOutdated } returns true
        serverListUpdater.updateTask()
        verify { mockServerManager.setServers(servers, any()) }
    }

    @Test
    fun `update task updates loads when in foreground`() = testScope.runTest {
        coEvery { mockApi.getLoads(any()) } returns ApiResult.Success(LoadsResponse(emptyList()))
        every { mockServerManager.lastUpdateTimestamp } returns clockMs - TimeUnit.MINUTES.toMillis(20)
        serverListUpdater.setInForegroundForTest(true)
        serverListUpdater.updateTask()
        coVerify(exactly = 1) { mockApi.getLoads(any()) }

        clockMs += TimeUnit.MINUTES.toMillis(10)
        serverListUpdater.updateTask()
        coVerify(exactly = 1) { mockApi.getLoads(any()) }

        clockMs += TimeUnit.MINUTES.toMillis(10)
        serverListUpdater.updateTask()
        verify { mockServerManager.updateLoads(emptyList()) }
    }
}
