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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.UserLocation
import com.protonvpn.android.models.vpn.data.LogicalsMetadata
import com.protonvpn.android.servers.FakeIsBinaryServerStatusFeatureFlagEnabled
import com.protonvpn.android.servers.IsBinaryServerStatusEnabled
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.ServersDataManager
import com.protonvpn.android.servers.UpdateServerListFromApi
import com.protonvpn.android.servers.api.LogicalServer
import com.protonvpn.android.servers.api.LogicalServerV1
import com.protonvpn.android.servers.api.LogicalsResponse
import com.protonvpn.android.servers.api.LogicalsStatusId
import com.protonvpn.android.servers.api.ServerListV1
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.ui.home.ServerListUpdaterRemoteConfig
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.usecases.FakeServerListTruncationEnabled
import com.protonvpn.android.vpn.usecases.GetTruncationMustHaveIDs
import com.protonvpn.mocks.FakeUpdateServersWithBinaryStatus
import com.protonvpn.mocks.createInMemoryServerManager
import com.protonvpn.mocks.createInMemoryServersDataManager
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createLogicalServer
import com.protonvpn.test.shared.createServer
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
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
private val LIST_LOGICALS_V2 = listOf(createLogicalServer("ID1"), createLogicalServer("ID2"))
private val STATUS_ID: LogicalsStatusId = "StatusId"

@OptIn(ExperimentalCoroutinesApi::class)
class ServerListUpdaterTests {

    @get:Rule
    val instantTaskExecutor = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockApi: ProtonApiRetroFit
    @MockK
    private lateinit var guestHole: GuestHole
    @RelaxedMockK
    private lateinit var mockPlanManager: UserPlanManager
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
    private lateinit var serversDataManager: ServersDataManager
    private lateinit var serverManager: ServerManager
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

        Storage.setPreferences(MockSharedPreference())
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
            fullLogicals = FULL_LIST_LOGICALS_V1
        )
        fakeServerListV2Backend = FakeServerListV2Backend(
            serverLastModified = { testScope.currentTime },
            logicals = LIST_LOGICALS_V2,
            statusId = STATUS_ID
        )
        runWhileGettingServerList = {}
        coEvery { guestHole.runWithGuestHoleFallback(any<suspend () -> Any?>()) } coAnswers { firstArg<suspend () -> Any?>()() }
        val currentUser = CurrentUser(TestCurrentUserProvider(TestUser.freeUser.vpnUser))
        coEvery { mockApi.getServerListV1(any(), any(), any(), any(), any()) } answers {
            runWhileGettingServerList()
            fakeServerListV1Backend.createResponse(lastModified = arg(2), enableTruncation = arg(3))
        }
        coEvery { mockApi.getServerList(any(), any(), any(), any(), any()) } answers {
            runWhileGettingServerList()
            fakeServerListV2Backend.createResponse(lastModified = arg(2), enableTruncation = arg(3))
        }
        coEvery { mockApi.getBinaryStatus(any()) } returns ApiResult.Success(ByteArray(0))

        mustHaveIDs = emptySet()
        serversDataManager = createInMemoryServersDataManager(testScope, TestDispatcherProvider(testDispatcher))
        serverManager = createInMemoryServerManager(testScope, serversDataManager)
        truncationEnabled = MutableStateFlow(true)
        val getNetZone = GetNetZone(serverListUpdaterPrefs)
        val serverListTruncationFF = FakeServerListTruncationEnabled(truncationEnabled)
        val binaryServerStatusEnabled = IsBinaryServerStatusEnabled(
            isBinaryServerStatusFeatureFlagEnabled = FakeIsBinaryServerStatusFeatureFlagEnabled(true),
            isServerListTruncationFeatureFlagEnabled = serverListTruncationFF
        )
        val getTruncationMustHaveIds = GetTruncationMustHaveIDs { _, _ -> mustHaveIDs }
        val updateServerListFromApi = UpdateServerListFromApi(
            mockApi,
            TestDispatcherProvider(testDispatcher),
            serverManager,
            serversDataManager,
            serverListUpdaterPrefs,
            fakeUpdateWithBinaryStatus,
            binaryServerStatusEnabled,
            serverListTruncationFF,
            getTruncationMustHaveIds,
        )
        serverListUpdater = ServerListUpdater(
            scope = testScope.backgroundScope,
            api = mockApi,
            currentUser = currentUser,
            vpnStateMonitor = vpnStateMonitor,
            userPlanManager = mockPlanManager,
            prefs = serverListUpdaterPrefs,
            getNetZone = getNetZone,
            guestHole = guestHole,
            periodicUpdateManager = mockPeriodicUpdateManager,
            loggedIn = emptyFlow(),
            inForeground = emptyFlow(),
            remoteConfig = remoteConfig,
            updateServerListFromApi = updateServerListFromApi,
            updateLoadsFromApi = mockk(relaxed = true),
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
    fun `only additional must-have IDs are retained`() = testScope.runTest {
        truncationEnabled.value = true
        serverManager.setServers(
            listOf(createServer("1"), createServer("2"), createServer("3")),
            statusId = null
        )
        mustHaveIDs = setOf("1", "2")
        fakeServerListV2Backend.logicals = listOf(createLogicalServer("2"))
        runWhileGettingServerList = { mustHaveIDs = setOf("1", "2", "3") }
        serverListUpdater.updateServers()

        // "1" is not retained:
        assertEquals(setOf("2", "3"), serverManager.allServers.mapTo(HashSet()) { it.serverId })
        coVerify {
            mockApi.getServerList(any(), any(), any(), enableTruncation = true, mustHaveIDs = setOf("1", "2"))
        }
    }

    @Test
    fun `no truncation when feature flag is off`() = testScope.runTest {
        truncationEnabled.value = false
        mustHaveIDs = setOf("1", "2", "3")
        serverListUpdater.updateServers()
        coVerify {
            mockApi.getServerListV1(any(), any(), any(), enableTruncation = false, mustHaveIDs = emptySet())
        }
    }

    @Test
    fun `don't retain server IDs if truncation not applied`() = testScope.runTest {
        truncationEnabled.value = true
        fakeServerListV2Backend.applyTruncation = false
        fakeServerListV2Backend.logicals = listOf(createLogicalServer("1"))
        serverManager.setServers(
            listOf(createServer("1"), createServer("2"), createServer("3")),
            statusId = null
        )
        mustHaveIDs = setOf("1", "2", "3")
        serverListUpdater.updateServers()

        assertEquals(setOf("1"), serverManager.allServers.toIds())
        coVerify {
            mockApi.getServerList(any(), any(), any(), enableTruncation = true, mustHaveIDs = setOf("1", "2", "3"))
        }
    }

    private fun List<Server>.toIds() = mapTo(HashSet()) { it.serverId }
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
    var fullLogicals: List<LogicalServerV1>,
) : FakeServerListBackend(serverLastModified) {

    fun createResponse(
        lastModified: Long,
        enableTruncation: Boolean,
    ) = createResponse(lastModified, enableTruncation) { isListTruncated, headers ->
        response(fullLogicals, isListTruncated, headers)
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
