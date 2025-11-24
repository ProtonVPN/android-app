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
import com.protonvpn.android.models.vpn.UserLocation
import com.protonvpn.android.models.vpn.data.LogicalsMetadata
import com.protonvpn.android.servers.FakeIsBinaryServerStatusFeatureFlagEnabled
import com.protonvpn.android.servers.UpdateServerListFromApi
import com.protonvpn.android.servers.IsBinaryServerStatusEnabled
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.api.LogicalServer
import com.protonvpn.android.servers.api.LogicalServerV1
import com.protonvpn.android.servers.api.LogicalsResponse
import com.protonvpn.android.servers.api.LogicalsStatusId
import com.protonvpn.android.servers.api.ServerListV1
import com.protonvpn.android.servers.toPartialServer
import com.protonvpn.android.servers.toServers
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.ui.home.ServerListUpdaterRemoteConfig
import com.protonvpn.android.ui.home.updateTier
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.usecases.FakeServerListTruncationEnabled
import com.protonvpn.android.vpn.usecases.GetTruncationMustHaveIDs
import com.protonvpn.mocks.FakeUpdateServersWithBinaryStatus
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createLogicalServer
import com.protonvpn.test.shared.createServer
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
private val DUMMY_USER_LOCATION = UserLocation(TEST_IP, "pl", "ISP", latitude = 0f, longitude = 0f)
private const val OLD_IP = "10.0.0.1"
private const val BACKGROUND_DELAY_MS = 1000L
private const val FOREGROUND_DELAY_MS = 100L

private val FULL_LIST_LOGICALS_V1 = MockedServers.logicalsList
private val FREE_LIST_LOGICALS_V1_MODIFIED = listOf(
    MockedServers.logicalsList.first { it.serverName == "SE#3" }.copy(load = 50f)
)
private val LIST_LOGICALS_V2 = listOf(createLogicalServer("ID1"), createLogicalServer("ID2"))
private val STATUS_ID: LogicalsStatusId = "StatusId"
private val FULL_LIST = FULL_LIST_LOGICALS_V1.toServers()
private val FREE_LIST_MODIFIED  = FREE_LIST_LOGICALS_V1_MODIFIED.toServers()

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

    private lateinit var fakeUpdateWithBinaryStatus: FakeUpdateServersWithBinaryStatus
    private lateinit var fakeServerListV1Backend: FakeServerListV1Backend
    private lateinit var fakeServerListV2Backend: FakeServerListV2Backend
    private lateinit var remoteConfig: ServerListUpdaterRemoteConfig
    private lateinit var serverListUpdaterPrefs: ServerListUpdaterPrefs
    private lateinit var testScope: TestScope
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var mustHaveIDs: Set<String>
    private lateinit var binaryStatusFfEnabled: MutableStateFlow<Boolean>
    private lateinit var truncationEnabled: MutableStateFlow<Boolean>
    private lateinit var runWhileGettingServerList: () -> Unit

    private lateinit var serverListUpdater: ServerListUpdater

    @Before
    fun setup() {
        MockKAnnotations.init(this)

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
        fakeUpdateWithBinaryStatus = FakeUpdateServersWithBinaryStatus()
        fakeServerListV1Backend = FakeServerListV1Backend(
            serverLastModified = { testScope.currentTime },
            freeLogicals = FREE_LIST_LOGICALS_V1_MODIFIED,
            fullLogicals = FULL_LIST_LOGICALS_V1
        )
        fakeServerListV2Backend = FakeServerListV2Backend(
            serverLastModified = { testScope.currentTime },
            logicals = LIST_LOGICALS_V2,
            statusId = STATUS_ID
        )
        runWhileGettingServerList = {}
        coEvery { guestHole.runWithGuestHoleFallback(any<suspend () -> Any?>()) } coAnswers { firstArg<suspend () -> Any?>()() }
        coEvery { mockCurrentUser.isLoggedIn() } returns true
        coEvery { mockCurrentUser.eventVpnLogin } returns emptyFlow()
        coEvery { mockCurrentUser.vpnUser() } returns TestUser.freeUser.vpnUser
        coEvery { mockServerManager.isDownloadedAtLeastOnce } returns true
        coEvery { mockServerManager.needsUpdate() } returns false
        var allServers = emptyList<Server>()
        coEvery { mockServerManager.setServers(any(), any(), any()) } answers { allServers = firstArg() }
        every { mockServerManager.allServers } answers { allServers }
        coEvery { mockApi.getStreamingServices() } returns ApiResult.Error.Timeout(false)
        coEvery { mockApi.getServerListV1(any(), any(), any(), any(), any(), any(), any()) } answers {
            runWhileGettingServerList()
            fakeServerListV1Backend.createResponse(freeOnly = arg(3), lastModified = arg(4), enableTruncation = arg(5))
        }
        coEvery { mockApi.getServerList(any(), any(), any(), any(), any(), any()) } answers {
            runWhileGettingServerList()
            fakeServerListV2Backend.createResponse(lastModified = arg(3), enableTruncation = arg(4))
        }
        coEvery { mockApi.getBinaryStatus(any()) } returns ApiResult.Success(ByteArray(0))

        every { mockTelephonyManager.phoneType } returns TelephonyManager.PHONE_TYPE_GSM
        every { mockTelephonyManager.networkCountryIso } returns "ch"

        mustHaveIDs = emptySet()
        binaryStatusFfEnabled = MutableStateFlow(false)
        truncationEnabled = MutableStateFlow(true)
        val getNetZone = GetNetZone(serverListUpdaterPrefs)
        val serverListTruncationFF = FakeServerListTruncationEnabled(truncationEnabled)
        val binaryServerStatusEnabled = IsBinaryServerStatusEnabled(
            isBinaryServerStatusFeatureFlagEnabled = FakeIsBinaryServerStatusFeatureFlagEnabled(binaryStatusFfEnabled),
            isServerListTruncationFeatureFlagEnabled = serverListTruncationFF
        )
        val getTruncationMustHaveIds = GetTruncationMustHaveIDs { _, _ -> mustHaveIDs }
        val updateServerListFromApi = UpdateServerListFromApi(
            mockApi,
            TestDispatcherProvider(testDispatcher),
            testScope::currentTime,
            mockServerManager,
            serverListUpdaterPrefs,
            fakeUpdateWithBinaryStatus,
            binaryServerStatusEnabled,
            serverListTruncationFF,
            getTruncationMustHaveIds,
        )
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
            testScope::currentTime,
            updateServerListFromApi,
            binaryServerStatusEnabled,
        )
    }

    @Test
    fun `location is updated when VPN is off`() = testScope.runTest {
        coEvery { mockApi.getLocation() } returns ApiResult.Success(DUMMY_USER_LOCATION)

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
            ApiResult.Success(DUMMY_USER_LOCATION)
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
        coEvery { mockApi.getLocation() } returns ApiResult.Success(DUMMY_USER_LOCATION)
        serverListUpdater.updateLocationIfVpnOff()

        coVerify { mockPeriodicUpdateManager.executeNow<Any, Any>(match { it.id == "server_list" }) }
    }

    @Test
    fun `free user gets light list refresh`() = testScope.runTest {
        binaryStatusFfEnabled.value = false
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
            mockApi.getServerListV1(any(), any(), any(), freeOnly = false, any(), any(), any())
            mockServerManager.setServers(withArg { assertEquals(FULL_LIST, it) }, null, any())

            mockApi.getServerListV1(any(), any(), any(), freeOnly = true, any(), any(), any())
            mockServerManager.setServers(withArg { assertTrue(it.isModifiedList()) }, null, any())

            mockApi.getServerListV1(any(), any(), any(), freeOnly = false, any(), any(), any())
        }
    }

    @Test
    fun `free user light list refresh disabled when binary status enabled`() = testScope.runTest {
        fun applyStatus(server: Server) = server.copy(rawIsOnline = true, isVisible = true, load = 10f, score = 1.0)
        val expectedServers = LIST_LOGICALS_V2.map { applyStatus(it.toPartialServer()) }
        fakeUpdateWithBinaryStatus.mapsAllServers(::applyStatus)
        binaryStatusFfEnabled.value = true
        truncationEnabled.value = true

        // First update is full
        assertFalse(serverListUpdater.freeOnlyUpdateNeeded())
        serverListUpdater.updateServers()

        // Not enough time passed for full refresh
        advanceTimeBy(1)
        // Second update is also full
        assertFalse(serverListUpdater.freeOnlyUpdateNeeded())
        serverListUpdater.updateServers()

        coVerifyOrder {
            repeat(2) {
                mockApi.getServerList(any(), any(), any(), any(), any(), any())
                mockServerManager.setServers(
                    withArg { assertEquals(expectedServers, it) },
                    STATUS_ID,
                    any())
            }
        }
    }

    @Test
    fun `when logicals is successful but binary status fails then no servers are updated`() = testScope.runTest {
        binaryStatusFfEnabled.value = true
        truncationEnabled.value = true
        coEvery { mockApi.getBinaryStatus(any()) } returns ApiResult.Error.Connection()

        serverListUpdater.updateServers()
        coVerify(exactly = 1) { mockApi.getServerList(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { mockServerManager.setServers(any(), any(), any()) }
    }

    @Test
    fun `only additional must-have IDs are retained`() = testScope.runTest {
        truncationEnabled.value = true
        mustHaveIDs = setOf("1", "2")
        runWhileGettingServerList = { mustHaveIDs = setOf("1", "2", "3") }
        serverListUpdater.updateServers()

        coVerifyOrder {
            mockApi.getServerListV1(any(), any(), any(), any(), any(), enableTruncation = true, mustHaveIDs = setOf("1", "2"))
            mockServerManager.setServers(any(), null, any(), retainIDs = setOf("3"))
        }
    }

    @Test
    fun `no truncation when feature flag is off`() = testScope.runTest {
        truncationEnabled.value = false
        mustHaveIDs = setOf("1", "2", "3")
        serverListUpdater.updateServers()
        coVerifyOrder {
            mockApi.getServerListV1(any(), any(), any(), any(), any(), enableTruncation = false, mustHaveIDs = emptySet())
            mockServerManager.setServers(any(), null, any(), retainIDs = emptySet())
        }
    }

    @Test
    fun `don't retain server IDs if truncation not applied`() = testScope.runTest {
        truncationEnabled.value = true
        fakeServerListV1Backend.applyTruncation = false
        mustHaveIDs = setOf("1", "2", "3")
        serverListUpdater.updateServers()
        coVerifyOrder {
            mockApi.getServerListV1(any(), any(), any(), any(), any(), enableTruncation = true, mustHaveIDs = setOf("1", "2", "3"))
            mockServerManager.setServers(any(), null, any(), retainIDs = emptySet())
        }
    }

    @Test
    fun `no refresh if client already have newest version`() = testScope.runTest {
        val successResult = UpdateServerListFromApi.Result.Success
        val result1 = serverListUpdater.updateServers()
        assertEquals(successResult, result1.result)
        coVerify(exactly = 1) { mockServerManager.setServers(any(), null, any()) }

        // Version will not change for the next call
        var lastModifiedOverride = serverListUpdaterPrefs.serverListLastModified
        fakeServerListV1Backend.serverLastModified = { lastModifiedOverride }

        val result2 = serverListUpdater.updateServers()
        assertEquals(successResult, result2.result)
        // 304 does not result in a call to setServers but will refresh timestamp.
        coVerify(exactly = 1) { mockServerManager.setServers(any(), null, any()) }
        coVerify(exactly = 1) { mockServerManager.updateTimestamp() }

        // Make new version available
        lastModifiedOverride += TimeUnit.HOURS.toMillis(1)
        val result3 = serverListUpdater.updateServers()
        assertEquals(successResult, result3.result)
        assertEquals(lastModifiedOverride, serverListUpdaterPrefs.serverListLastModified)
        coVerify(exactly = 2) { mockServerManager.setServers(any(), null, any()) }
    }

    @Test
    fun updateTier() {
        // Updating tier 0 with empty list removes all tier 0 servers
        assertEquals(FULL_LIST.filter { it.tier != 0 }, FULL_LIST.updateTier(emptyList(), 0, emptySet()))
        assertTrue(FULL_LIST.updateTier(FREE_LIST_MODIFIED, 0, emptySet()).isModifiedList())
    }

    @Test
    fun updateTierWithIncludeID() {
        val initialList = listOf(
            createServer(serverId = "1", tier = 0, serverName = "1"),
            createServer(serverId = "2", tier = 0), // not included in update -> out
            createServer(serverId = "3", tier = 1), // tier=1 should stay
            createServer(serverId = "4", tier = 0), // Retained because of retainIDs
        )
        val update = listOf(
            createServer(serverId = "1", tier = 0, serverName = "1-modified"),
            createServer(serverId = "5", tier = 1), // tier=1 in update because of includeIDs
        )
        assertEquals(
            setOf(
                createServer(serverId = "1", tier = 0, serverName = "1-modified"),
                createServer(serverId = "3", tier = 1),
                createServer(serverId = "4", tier = 0),
                createServer(serverId = "5", tier = 1)
            ),
            initialList.updateTier(update, tier = 0, retainIDs = setOf("4")).toSet()
        )
    }

    private fun List<Server>.isModifiedList() =
        filter { it.tier != 0 } == FULL_LIST.filter { it.tier != 0 } // Same paid servers
            && filter { it.tier == 0 } == FREE_LIST_MODIFIED // Free servers come from new update
}

private abstract class FakeServerListBackend(
    var serverLastModified: () -> Long
) {

    var applyTruncation: Boolean = true

    protected fun <T> createResponse(
        lastModified: Long,
        enableTruncation: Boolean,
        buildResponse: (isListTruncated: Boolean, headers: Headers) -> Response<T>
    ) : ApiResult<Response<T>> {
        val headers = Headers.Builder().add("Last-Modified", Date(serverLastModified())).build()
        return if (serverLastModified() > lastModified) {
            ApiResult.Success(buildResponse(enableTruncation && applyTruncation, headers))
        } else {
            ApiResult.Success(response304<T>())
        }
    }

    private fun <T> response304() = Response.error<T>(
        "".toResponseBody(),
        okhttp3.Response.Builder()
            .request(Request.Builder().url("https://localhost").get().build())
            .code(304)
            .message("Not Modified")
            .protocol(Protocol.HTTP_1_1)
            .build()
    )
}

private class FakeServerListV1Backend(
    serverLastModified: () -> Long,
    var freeLogicals: List<LogicalServerV1>,
    var fullLogicals: List<LogicalServerV1>,
) : FakeServerListBackend(serverLastModified) {

    fun createResponse(
        freeOnly: Boolean,
        lastModified: Long,
        enableTruncation: Boolean,
    ) = createResponse(lastModified, enableTruncation) { isListTruncated, headers ->
        val list = if (freeOnly) freeLogicals else fullLogicals
        response(list, isListTruncated, headers)
    }

    private fun response(
        list: List<LogicalServerV1>,
        isListTruncated: Boolean,
        headers: Headers
    ): Response<ServerListV1> = Response.success(
            ServerListV1(list, LogicalsMetadata(listIsTruncated = isListTruncated)),
            headers
        )
}

private class FakeServerListV2Backend(
    serverLastModified: () -> Long,
    var logicals: List<LogicalServer>,
    var statusId: LogicalsStatusId,
) : FakeServerListBackend(serverLastModified) {

    fun createResponse(lastModified: Long, enableTruncation: Boolean) =
        createResponse(lastModified, enableTruncation) { isListTruncated, headers ->
            response(logicals, statusId, isListTruncated, headers)
        }

    private fun response(
        list: List<LogicalServer>,
        statusId: LogicalsStatusId,
        isListTruncated: Boolean,
        headers: Headers
    ): Response<LogicalsResponse> = Response.success(
        LogicalsResponse(statusId, list, LogicalsMetadata(isListTruncated)),
        headers
    )
}
