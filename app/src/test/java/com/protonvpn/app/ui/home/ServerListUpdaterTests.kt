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
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.android.models.vpn.UserLocation
import com.protonvpn.android.partnerships.PartnershipsRepository
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.proton.core.network.domain.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
    private lateinit var mockPlanManager: UserPlanManager
    @RelaxedMockK
    private lateinit var mockTelephonyManager: TelephonyManager
    @MockK
    private lateinit var mockPartnershipsRepository: PartnershipsRepository
    @RelaxedMockK
    private lateinit var mockPeriodicUpdateManager: PeriodicUpdateManager

    private lateinit var testScope: TestScope
    private lateinit var serverListUpdaterPrefs: ServerListUpdaterPrefs
    private lateinit var vpnStateMonitor: VpnStateMonitor

    private lateinit var serverListUpdater: ServerListUpdater

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)
        serverListUpdaterPrefs = ServerListUpdaterPrefs(MockSharedPreferencesProvider())
        serverListUpdaterPrefs.ipAddress = OLD_IP
        vpnStateMonitor = VpnStateMonitor()

        coEvery { guestHole.runWithGuestHoleFallback(any<suspend () -> Any?>()) } coAnswers { firstArg<suspend () -> Any?>()() }
        coEvery { mockCurrentUser.isLoggedIn() } returns true
        coEvery { mockCurrentUser.eventVpnLogin } returns emptyFlow()
        every { mockServerManager.isDownloadedAtLeastOnce } returns true
        every { mockServerManager.isOutdated } returns false
        coEvery { mockApi.getStreamingServices() } returns ApiResult.Error.Timeout(false)
        coEvery { mockApi.getServerList(any(), any(), any(), any()) } returns ApiResult.Success(ServerList(emptyList()))
        coEvery { mockPartnershipsRepository.refresh() } returns Unit

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
            mockPartnershipsRepository,
            guestHole,
            mockPeriodicUpdateManager,
            emptyFlow(),
            emptyFlow()
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
}
