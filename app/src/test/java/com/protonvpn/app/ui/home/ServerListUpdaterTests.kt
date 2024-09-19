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
import com.protonvpn.android.appconfig.ChangeServerConfig
import com.protonvpn.android.appconfig.Restrictions
import com.protonvpn.android.appconfig.RestrictionsConfig
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.android.models.vpn.UserLocation
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.ui.home.ServerListUpdaterRemoteConfig
import com.protonvpn.android.ui.home.updateTier
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestUser
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import me.proton.core.network.domain.ApiResult
import okhttp3.Headers
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import java.util.Date
import java.util.concurrent.TimeUnit

private const val TEST_IP = "1.2.3.4"
private const val OLD_IP = "10.0.0.1"
private const val BACKGROUND_DELAY_MS = 1000L
private const val FOREGROUND_DELAY_MS = 100L

private val FULL_LIST = MockedServers.serverList
private val FREE_LIST_MODIFIED = listOf(
    MockedServers.serverList.first { it.serverName == "SE#3" }.copy(load = 50f)
)
private val RESPONSE_304 : Response<ServerList> = Response.error("".toResponseBody(),
    okhttp3.Response.Builder()
        .request(Request.Builder().url("https://localhost").get().build())
        .code(304)
        .message("Not Modified")
        .protocol(Protocol.HTTP_1_1)
        .build()
)

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
    private lateinit var mockPlanManager: UserPlanManager
    @RelaxedMockK
    private lateinit var mockTelephonyManager: TelephonyManager
    @RelaxedMockK
    private lateinit var mockPeriodicUpdateManager: PeriodicUpdateManager

    private lateinit var remoteConfig: ServerListUpdaterRemoteConfig
    private lateinit var restrictionsFlow: MutableStateFlow<Restrictions>
    private lateinit var serverListUpdaterPrefs: ServerListUpdaterPrefs
    private lateinit var testScope: TestScope
    private lateinit var vpnStateMonitor: VpnStateMonitor

    private lateinit var serverListUpdater: ServerListUpdater
    private var lastModifiedOverride: Long? = null

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        lastModifiedOverride = null
        val testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)
        // Move wall clock away from epoch
        testScope.advanceTimeBy(TimeUnit.DAYS.toMillis(100))

        serverListUpdaterPrefs = ServerListUpdaterPrefs(MockSharedPreferencesProvider())
        serverListUpdaterPrefs.ipAddress = OLD_IP
        vpnStateMonitor = VpnStateMonitor()
        remoteConfig = ServerListUpdaterRemoteConfig(
            MutableStateFlow(
                ServerListUpdaterRemoteConfig.Config(
                    backgroundDelayMs = BACKGROUND_DELAY_MS,
                    foregroundDelayMs = FOREGROUND_DELAY_MS
                )
            )
        )
        restrictionsFlow = MutableStateFlow(Restrictions(false, ChangeServerConfig(100, 4, 60)))
        coEvery { guestHole.runWithGuestHoleFallback(any<suspend () -> Any?>()) } coAnswers { firstArg<suspend () -> Any?>()() }
        coEvery { mockCurrentUser.isLoggedIn() } returns true
        coEvery { mockCurrentUser.eventVpnLogin } returns emptyFlow()
        coEvery { mockCurrentUser.vpnUser() } returns TestUser.freeUser.vpnUser
        coEvery { mockServerManager.isDownloadedAtLeastOnce } returns true
        coEvery { mockServerManager.needsUpdate() } returns false
        var allServers = emptyList<Server>()
        coEvery { mockServerManager.setServers(any(), any()) } answers { allServers = firstArg() }
        every { mockServerManager.allServers } answers { allServers }
        coEvery { mockApi.getStreamingServices() } returns ApiResult.Error.Timeout(false)
        coEvery { mockApi.getServerList(any(), any(), any(), any(), any()) } answers {
            val freeOnly = arg<Boolean>(3)
            val lastModified = arg<Long>(4)
            val list = if (freeOnly) FREE_LIST_MODIFIED else FULL_LIST
            val serverLastModified = lastModifiedOverride ?: testScope.currentTime
            if (serverLastModified > lastModified) {
                val headers = Headers.Builder().add("Last-Modified", Date(serverLastModified)).build()
                ApiResult.Success(Response.success(ServerList(list), headers))
            } else {
                ApiResult.Success(RESPONSE_304)
            }
        }

        every { mockTelephonyManager.phoneType } returns TelephonyManager.PHONE_TYPE_GSM
        every { mockTelephonyManager.networkCountryIso } returns "ch"

        val getNetZone = GetNetZone(serverListUpdaterPrefs)
        serverListUpdater = ServerListUpdater(
            testScope.backgroundScope,
            mockApi,
            mockServerManager,
            mockCurrentUser,
            vpnStateMonitor,
            mockPlanManager,
            serverListUpdaterPrefs,
            getNetZone,
            guestHole,
            mockPeriodicUpdateManager,
            emptyFlow(),
            emptyFlow(),
            remoteConfig,
            RestrictionsConfig(testScope.backgroundScope, restrictionsFlow),
            testScope::currentTime
        )
    }

    @Test
    fun `location is updated when VPN is off`() = testScope.runTest {
        coEvery { mockApi.getLocation() } returns ApiResult.Success(UserLocation(TEST_IP, "pl", "ISP"))

        serverListUpdater.updateLocationIfVpnOff()
        assertEquals(TEST_IP, serverListUpdater.ipAddress.first())
        assertEquals("pl", serverListUpdater.lastKnownCountry)
        assertEquals("ISP", serverListUpdater.lastKnownIsp)
    }

    @Test
    fun `location is not updated when VPN is connected`() = testScope.runTest {
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, null))

        serverListUpdater.updateLocationIfVpnOff()
        assertEquals(OLD_IP, serverListUpdaterPrefs.ipAddress)

        coVerify { mockApi wasNot Called }
    }

    @Test
    fun `location update is cancelled when VPN starts connecting during update`() = testScope.runTest {
        coEvery { mockApi.getLocation() } coAnswers {
            delay(1000)
            ApiResult.Success(UserLocation(TEST_IP, "pl", "ISP"))
        }

        val updateLocationJob = launch {
            serverListUpdater.updateLocationIfVpnOff()
        }
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, null))
        updateLocationJob.join()

        assertEquals(OLD_IP, serverListUpdater.ipAddress.first())
        assertEquals(OLD_IP, serverListUpdaterPrefs.ipAddress)
        coVerify(exactly = 1) { mockApi.getLocation() }
    }

    @Test
    fun `location update triggers server list update`() = testScope.runTest {
        coEvery { mockApi.getLocation() } returns ApiResult.Success(UserLocation(TEST_IP, "pl", "ISP"))
        serverListUpdater.updateLocationIfVpnOff()

        coVerify { mockPeriodicUpdateManager.executeNow<Any, Any>(match { it.id == "server_list" }) }
    }

    @Test
    fun `free user gets light list refresh`() = testScope.runTest {
        // First update is full
        assertFalse(serverListUpdater.freeOnlyUpdateNeeded())
        serverListUpdater.updateServers()

        // Not enough time passed for full refresh
        advanceTimeBy(1)
        assertTrue(serverListUpdater.freeOnlyUpdateNeeded())
        serverListUpdater.updateServers()

        // Full update needed again
        advanceTimeBy(ServerListUpdater.FULL_SERVER_LIST_CALL_DELAY)
        assertFalse(serverListUpdater.freeOnlyUpdateNeeded())
        serverListUpdater.updateServers()

        coVerifyOrder {
            mockApi.getServerList(any(), any(), any(), freeOnly = false, any())
            mockServerManager.setServers(withArg { assertEquals(FULL_LIST, it) }, any())

            mockApi.getServerList(any(), any(), any(), freeOnly = true, any())
            mockServerManager.setServers(withArg { assertTrue(it.isModifiedList()) }, any())

            mockApi.getServerList(any(), any(), any(), freeOnly = false, any())
        }
    }

    @Test
    fun `no refresh if client already have newest version`() = testScope.runTest {
        val result1 = serverListUpdater.updateServers()
        assertTrue(result1 is ApiResult.Success && result1.value != null)

        // Version will not change for the next call
        lastModifiedOverride = serverListUpdaterPrefs.serverListLastModified

        val result2 = serverListUpdater.updateServers()
        assertTrue(result2 is ApiResult.Success && result2.value == null)

        // Make sure 304 will update server list refresh timestamp
        coVerify(exactly = 1) {
            mockServerManager.updateTimestamp()
        }

        // Make new version available
        lastModifiedOverride = lastModifiedOverride?.let { it + TimeUnit.HOURS.toMillis(1) }
        val result3 = serverListUpdater.updateServers()
        assertTrue(result3 is ApiResult.Success && result3.value != null)
        assertEquals(lastModifiedOverride, serverListUpdaterPrefs.serverListLastModified)

        coVerify(exactly = 2) {
            mockServerManager.setServers(any(), any())
        }
    }

    @Test
    fun updateTier() {
        // Updating tier 0 with empty list removes all tier 0 servers
        assertEquals(FULL_LIST.filter { it.tier != 0 }, FULL_LIST.updateTier(emptyList(), 0))
        assertTrue(FULL_LIST.updateTier(FREE_LIST_MODIFIED, 0).isModifiedList())
    }

    @Test
    fun `servers updated for free user on restrictions change`() = testScope.runTest {
        coEvery { mockCurrentUser.vpnUser() } returns TestUser.plusUser.vpnUser
        restrictionsFlow.value = Restrictions(true, ChangeServerConfig(100, 4, 60))
        // No update for plus user.
        coVerify(exactly = 0) {
            mockPeriodicUpdateManager.executeNow<Any, Any>(match { it.id == "server_list" })
        }

        coEvery { mockCurrentUser.vpnUser() } returns TestUser.freeUser.vpnUser
        restrictionsFlow.value = Restrictions(false, ChangeServerConfig(100, 4, 60))
        coVerify(exactly = 1) {
            mockPeriodicUpdateManager.executeNow<Any, Any>(match { it.id == "server_list" })
        }
    }

    private fun List<Server>.isModifiedList() =
        filter { it.tier != 0 } == FULL_LIST.filter { it.tier != 0 } // Same paid servers
            && filter { it.tier == 0 } == FREE_LIST_MODIFIED // Free servers come from new update
}
