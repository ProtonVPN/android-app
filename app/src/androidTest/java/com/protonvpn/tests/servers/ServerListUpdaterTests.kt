/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.tests.servers

import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.servers.api.LogicalsResponse
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createLogicalServer
import com.protonvpn.testRules.ProtonHiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import me.proton.core.featureflag.data.remote.resource.UnleashToggleResource
import me.proton.core.featureflag.data.remote.resource.UnleashVariantResource
import me.proton.core.featureflag.data.remote.response.GetUnleashTogglesResponse
import me.proton.core.network.domain.ResponseCodes
import me.proton.core.util.kotlin.DispatcherProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import kotlin.test.assertEquals

@HiltAndroidTest
class ServerListUpdaterTests {

    private val statusId = "StatusID"

    @get:Rule
    val appHiltTest = ProtonHiltAndroidRule(
        testInstance = this,
        apiConfig = TestApiConfig.Mocked(
            testUser = TestUser.plusUser
        ) {
            rule(get, path eq "/vpn/v2/logicals") {
                respond(
                    LogicalsResponse(
                        statusId = statusId,
                        serverList = listOf(
                            createLogicalServer(serverId = "Server 1", statusIndex = 0u),
                            createLogicalServer(serverId = "Server 2", statusIndex = 1u),
                        ),
                    )
                )
            }
            rule(get, path eq "/vpn/v2/status/$statusId/binary") {
                respondBinary("AQAAAAMyAACAPwNLAACAPw==")
            }
            rule(get, path eq "/feature/v2/frontend") {
                val flags = listOf(
                    UnleashToggleResource("ServerListTruncation", UnleashVariantResource("", true)),
                    UnleashToggleResource("BinaryServerStatus", UnleashVariantResource("", true)),
                )
                respond(GetUnleashTogglesResponse(ResponseCodes.OK, flags))
            }
        }
    )

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider
    @Inject
    lateinit var serverListUpdater: ServerListUpdater
    @Inject
    lateinit var serverManager2: ServerManager2

    @Before
    fun setup() {
        appHiltTest.inject()
    }

    @Test
    fun testServerListFirstFetch() {
        runTest {
            withContext(dispatcherProvider.Main) {
                // This test assumes its run on a clear installation, as is the case with Android Orchestrator.
                serverListUpdater.updateServerList()
                val updatedServersList = serverManager2.allServersFlow.first().sortedBy { it.serverId }
                assertEquals(listOf("Server 1", "Server 2"), updatedServersList.map { it.serverId })
                assertEquals(listOf(50f, 75f), updatedServersList.map { it.load })
            }
        }
    }
}
