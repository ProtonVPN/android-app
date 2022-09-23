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
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.LoadsResponse
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.android.models.vpn.UserLocation
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.utils.NetUtils
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.UserPlanManager
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import me.proton.core.network.domain.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private lateinit var testScope: TestCoroutineScope
    private lateinit var testDispatcher: TestCoroutineDispatcher
    private lateinit var serverListUpdaterPrefs: ServerListUpdaterPrefs

    private var clockMs: Long = 1_000_000

    private lateinit var serverListUpdater: ServerListUpdater

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        testDispatcher = TestCoroutineDispatcher()
        testScope = TestCoroutineScope(testDispatcher)
        serverListUpdaterPrefs = ServerListUpdaterPrefs(MockSharedPreferencesProvider())
        serverListUpdaterPrefs.ipAddress = OLD_IP
        clockMs = 1_000_000

        coEvery { mockCurrentUser.isLoggedIn() } returns true
        every { mockServerManager.isDownloadedAtLeastOnce } returns true
        every { mockServerManager.isOutdated } returns false
        coEvery { mockApi.getStreamingServices() } returns ApiResult.Error.Timeout(false)
        coEvery { mockApi.getServerList(any(), any(), any()) } returns ApiResult.Success(ServerList(emptyList()))

        every { mockTelephonyManager.phoneType } returns TelephonyManager.PHONE_TYPE_GSM
        every { mockTelephonyManager.networkCountryIso } returns "ch"

        val getNetZone = GetNetZone(serverListUpdaterPrefs, mockTelephonyManager, { clockMs }, { clockMs })
        serverListUpdater = ServerListUpdater(
            testScope,
            mockApi,
            mockServerManager,
            mockCurrentUser,
            mockVpnStateMonitor,
            mockPlanManager,
            serverListUpdaterPrefs,
            { clockMs },
            { clockMs },
            getNetZone
        )
    }

    @Test
    fun `location is updated when VPN is off`() = testScope.runBlockingTest {
        coEvery { mockApi.getLocation() } returns ApiResult.Success(UserLocation(TEST_IP, "pl", "ISP"))
        every { mockVpnStateMonitor.isDisabled } returns true

        val locationChanged = serverListUpdater.updateLocationIfVpnOff()
        assertTrue(locationChanged)
        assertEquals(TEST_IP, serverListUpdater.ipAddress.first())
        assertEquals("pl", serverListUpdater.lastKnownCountry)
        assertEquals("ISP", serverListUpdater.lastKnownIsp)
    }

    @Test
    fun `location is not updated when VPN is connected`() = testScope.runBlockingTest {
        every { mockVpnStateMonitor.isDisabled } returns false

        val locationChanged = serverListUpdater.updateLocationIfVpnOff()
        assertFalse(locationChanged)
        assertEquals(OLD_IP, serverListUpdaterPrefs.ipAddress)

        coVerify { mockApi wasNot Called }
    }

    @Test
    fun `location result is ignored if VPN connects during update`() = testScope.runBlockingTest {
        coEvery { mockApi.getLocation() } returns ApiResult.Success(UserLocation(TEST_IP, "pl", "ISP"))
        every { mockVpnStateMonitor.isDisabled } returnsMany listOf(true, false)

        val locationChanged = serverListUpdater.updateLocationIfVpnOff()
        assertFalse(locationChanged)
        assertEquals(OLD_IP, serverListUpdater.ipAddress.first())
        assertEquals(OLD_IP, serverListUpdaterPrefs.ipAddress)

        coVerify(exactly = 1) { mockApi.getLocation() }
    }

    @Test
    fun `update task updates location 4 minutes after previous check`() = testScope.runBlockingTest {
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
    fun `update task updates server list when outdated`() = testScope.runBlockingTest {
        val servers = listOf(MockedServers.server)
        coEvery { mockApi.getLocation() } returns ApiResult.Success(UserLocation(OLD_IP, "pl", "ISP"))
        coEvery { mockApi.getServerList(any(), any(), any()) } returns ApiResult.Success(ServerList(servers))
        every { mockVpnStateMonitor.isDisabled } returns true
        serverListUpdater.updateTask()

        every { mockServerManager.isOutdated } returns true
        serverListUpdater.updateTask()
        verify { mockServerManager.setServers(servers, any()) }
    }

    @Test
    fun `fresh IP is preferred to MCC`() = testScope.runBlockingTest {
        every { mockVpnStateMonitor.isDisabled } returns true
        coEvery { mockApi.getLocation() } returns ApiResult.Success(UserLocation(TEST_IP, "pl", "ISP"))
        serverListUpdater.updateTask()
        coVerify { mockApi.getServerList(any(), NetUtils.stripIP(TEST_IP), any()) }
    }

    @Test
    fun `MCC is preferred to old IP`() = testScope.runBlockingTest {
        every { mockVpnStateMonitor.isDisabled } returns true
        coEvery { mockApi.getLocation() } returns ApiResult.Success(UserLocation(TEST_IP, "pl", "ISP"))
        serverListUpdater.updateLocationIfVpnOff()

        serverListUpdater.setInForegroundForTest(true)
        every { mockVpnStateMonitor.isDisabled } returns false
        clockMs += ServerListUpdater.IP_VALIDITY_MS + 1
        serverListUpdater.updateTask()
        coVerify { mockApi.getServerList(any(), "ch", any()) }
    }

    @Test
    fun `old IP is used if MCC not available`() = testScope.runBlockingTest {
        every { mockTelephonyManager.phoneType } returns TelephonyManager.PHONE_TYPE_CDMA
        every { mockVpnStateMonitor.isDisabled } returns true
        coEvery { mockApi.getLocation() } returns ApiResult.Success(UserLocation(TEST_IP, "pl", "ISP"))
        serverListUpdater.updateLocationIfVpnOff()

        serverListUpdater.setInForegroundForTest(true)
        every { mockVpnStateMonitor.isDisabled } returns false
        clockMs += ServerListUpdater.IP_VALIDITY_MS + 1
        serverListUpdater.updateTask()
        coVerify { mockApi.getServerList(any(), NetUtils.stripIP(TEST_IP), any()) }
    }

    @Test
    fun `update task updates loads when in foreground`() = testScope.runBlockingTest {
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
