/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.app.servers

import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.httpHeaderDateFormatter
import com.protonvpn.android.servers.FakeIsBinaryServerStatusFeatureFlagEnabled
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.servers.ServersDataManager
import com.protonvpn.android.servers.UpdateServerListFromApi
import com.protonvpn.android.servers.api.LogicalServer
import com.protonvpn.android.servers.api.LogicalsResponse
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.usecases.FakeServerListTruncationEnabled
import com.protonvpn.app.testRules.RobolectricHiltAndroidRule
import com.protonvpn.mocks.TestProtonApiRetroFitWrapper
import com.protonvpn.test.shared.createLogicalServer
import com.protonvpn.test.shared.createServer
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import me.proton.core.network.domain.ApiResult
import me.proton.core.util.kotlin.DispatcherProvider
import okhttp3.Headers
import okhttp3.MediaType
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import java.time.Instant
import java.util.Base64
import java.util.Date
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private const val StatusId = "StatusID"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class ServerListUpdaterTests {

    @get:Rule
    val hiltRule = RobolectricHiltAndroidRule(this)

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var fakeIsBinaryServerStatusFeatureFlagEnabled: FakeIsBinaryServerStatusFeatureFlagEnabled

    @Inject
    lateinit var fakeIsServerListTruncationEnabled: FakeServerListTruncationEnabled

    @Inject
    lateinit var serverListUpdater: ServerListUpdater

    @Inject
    lateinit var serverListUpdaterPrefs: ServerListUpdaterPrefs


    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var serverManager2: ServerManager2

    @Inject
    lateinit var serversDataManager: ServersDataManager

    @Inject
    lateinit var testApiWrapper: TestProtonApiRetroFitWrapper

    @Inject
    lateinit var testScope: TestScope

    private lateinit var apiOverrides: ApiOverrides
    private val response304 = Response.error<LogicalsResponse>(
        object : okhttp3.ResponseBody() {
            override fun contentLength(): Long = 0
            override fun contentType(): MediaType? = null
            override fun source(): BufferedSource = Buffer()
        },
        okhttp3.Response.Builder().code(304)
            .message("Not Modified")
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("http://localhost/").build())
            .build()
    )

    @Before
    fun setup() {
        hiltRule.inject()
        fakeIsBinaryServerStatusFeatureFlagEnabled.setEnabled(true)
        fakeIsServerListTruncationEnabled.setEnabled(true)
        apiOverrides = ApiOverrides(testApiWrapper.api)
        testApiWrapper.api = apiOverrides

        // Timestamp 0 is special, run tests with a common value of the clock.
        testScope.advanceTimeBy(1.days)
    }

    @Test
    fun testServerListFirstFetch() = testScope.runTest {
        withContext(dispatcherProvider.Main) {
            // This test assumes its run on a clear installation, as is the case with Android Orchestrator.
            serverListUpdater.updateServerList()
            val updatedServersList =
                serverManager2.allServersFlow.first().sortedBy { it.serverId }
            assertEquals(listOf("Server 1", "Server 2"), updatedServersList.map { it.serverId })
            assertEquals(listOf(50f, 75f), updatedServersList.map { it.load })
        }
    }

    @Test
    fun `when logicals is successful but binary status fails then no servers are updated`() = testScope.runTest {
        apiOverrides.binaryStatus = ApiResult.Error.Connection(true)
        serverListUpdater.updateServers()
        assertTrue(serverManager.allServers.isEmpty())
    }

    @Test
    fun `no refresh if client already have newest version`() = testScope.runTest {
        val successResult = UpdateServerListFromApi.Result.Success
        val firstUpdateTimestamp = currentTime
        apiOverrides.logicalsResponse = logicalsResponse(
            listOf(createLogicalServer("id1")),
            lastModified = firstUpdateTimestamp
        )
        val result1 = serverListUpdater.updateServers()
        Assert.assertEquals(successResult, result1.result)
        Assert.assertEquals(listOf("id1"), serverManager.allServers.map { it.serverId })
        Assert.assertEquals(firstUpdateTimestamp, serversDataManager.lastUpdateTimestamp)

        advanceTimeBy(5.minutes)
        apiOverrides.logicalsResponse = response304
        val result2 = serverListUpdater.updateServers()
        Assert.assertEquals(successResult, result2.result)
        // 304 does not result in a call to setServers but will refresh timestamp.
        Assert.assertEquals(listOf("id1"), serverManager.allServers.map { it.serverId })
        Assert.assertEquals(firstUpdateTimestamp, serverListUpdaterPrefs.serverListLastModified)
        Assert.assertEquals(currentTime, serversDataManager.lastUpdateTimestamp)

        // Make new version available
        val secondServerListLastModifier = currentTime
        apiOverrides.logicalsResponse =
            logicalsResponse(
                listOf(createLogicalServer("id2")),
                lastModified = secondServerListLastModifier
            )
        advanceTimeBy(1.hours)
        val result3 = serverListUpdater.updateServers()
        Assert.assertEquals(successResult, result3.result)
        Assert.assertEquals(
            secondServerListLastModifier,
            serverListUpdaterPrefs.serverListLastModified
        )
        Assert.assertEquals(currentTime, serversDataManager.lastUpdateTimestamp)
        Assert.assertEquals(listOf("id2"), serverManager.allServers.map { it.serverId })
    }
}

private fun logicalsResponse(
    serverList: List<LogicalServer>,
    statusId: String = StatusId,
    lastModified: Long = 0
) = Response.success<LogicalsResponse>(
    LogicalsResponse(statusId, serverList),
    Headers.headersOf(
        "Last-modified",
        httpHeaderDateFormatter.format(Instant.ofEpochMilli(lastModified))
    ),
)

private class ApiOverrides(realApi: ProtonApiRetroFit) : ProtonApiRetroFit by realApi {
    var logicalsResponse: Response<LogicalsResponse> = logicalsResponse(
        listOf(
            createLogicalServer(serverId = "Server 1", statusIndex = 0u),
            createLogicalServer(serverId = "Server 2", statusIndex = 1u),
        )
    )
    var binaryStatus: ApiResult<ByteArray> =
        ApiResult.Success(Base64.getDecoder().decode("AQAAAAMyAACAPwNLAACAPw=="))

    override suspend fun getServerList(
        netzone: String?,
        protocols: List<String>,
        lastModified: Long,
        enableTruncation: Boolean,
        mustHaveIDs: Set<String>?
    ) = ApiResult.Success(logicalsResponse)

    override suspend fun getBinaryStatus(statusId: String): ApiResult<ByteArray> = binaryStatus
}