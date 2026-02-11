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
import com.protonvpn.android.servers.FakeIsBinaryServerStatusFeatureFlagEnabled
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.servers.api.LogicalServer
import com.protonvpn.android.servers.api.LogicalsResponse
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.vpn.usecases.FakeServerListTruncationEnabled
import com.protonvpn.app.testRules.RobolectricHiltAndroidRule
import com.protonvpn.mocks.TestProtonApiRetroFitWrapper
import com.protonvpn.test.shared.createLogicalServer
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import me.proton.core.network.domain.ApiResult
import me.proton.core.util.kotlin.DispatcherProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import java.util.Base64
import javax.inject.Inject
import kotlin.test.assertEquals

private const val StatusId = "StatusID"

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
    lateinit var serverManager2: ServerManager2
    @Inject
    lateinit var testApiWrapper: TestProtonApiRetroFitWrapper
    @Inject
    lateinit var testScope: TestScope

    private class ApiOverrides(realApi: ProtonApiRetroFit) : ProtonApiRetroFit by realApi {
        var statusId: String = StatusId
        var logicals: List<LogicalServer> =
            listOf(
                createLogicalServer(serverId = "Server 1", statusIndex = 0u),
                createLogicalServer(serverId = "Server 2", statusIndex = 1u),
            )
        var binaryStatus = "AQAAAAMyAACAPwNLAACAPw=="

        override suspend fun getServerList(
            netzone: String?,
            protocols: List<String>,
            lastModified: Long,
            enableTruncation: Boolean,
            mustHaveIDs: Set<String>?
        ) = ApiResult.Success<Response<LogicalsResponse>>(
            Response.success(LogicalsResponse(statusId, logicals))
        )

        override suspend fun getBinaryStatus(statusId: String): ApiResult<ByteArray> {
            val bytes = Base64.getDecoder().decode(binaryStatus)
            return ApiResult.Success(bytes)
        }
    }

    @Before
    fun setup() {
        hiltRule.inject()
        fakeIsBinaryServerStatusFeatureFlagEnabled.setEnabled(true)
        fakeIsServerListTruncationEnabled.setEnabled(true)
        testApiWrapper.api = ApiOverrides(testApiWrapper.api)
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
}