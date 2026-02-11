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

package com.protonvpn.mocks

import com.protonvpn.android.promooffers.data.ApiNotificationsResponse
import com.protonvpn.android.models.config.bugreport.DynamicReportModel
import com.protonvpn.android.models.vpn.CertificateResponse
import com.protonvpn.android.servers.api.LogicalsResponse
import com.protonvpn.android.servers.api.SERVER_FEATURE_SECURE_CORE
import com.protonvpn.android.servers.api.ServerListV1
import com.protonvpn.android.servers.api.ServersCountResponse
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createLogicalServer
import me.proton.core.featureflag.data.remote.response.GetUnleashTogglesResponse
import me.proton.core.network.data.protonApi.GenericResponse
import me.proton.core.network.domain.HttpResponseCodes
import me.proton.core.network.domain.ResponseCodes
import okio.Buffer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

sealed class TestApiConfig {
    object Backend : TestApiConfig()

    class Mocked(
        private val testUser: TestUser? = null,
        private val additionalRules: (MockRuleBuilder.() -> Unit)? = null
    ) : TestApiConfig() {

        fun addDefaultRules(dispatcher: MockRequestDispatcher) {
            if (additionalRules != null) dispatcher.addRules(additionalRules)
            addBasicRules(dispatcher)
            if (testUser != null) addVpnInfoRule(dispatcher, testUser)
        }

        private fun addVpnInfoRule(dispatcher: MockRequestDispatcher, testUser: TestUser) {
            dispatcher.addRules {
                rule(get, path eq "/vpn/v2") {
                    respond(testUser.vpnInfoResponse)
                }
            }
        }

        @OptIn(ExperimentalEncodingApi::class)
        private fun addBasicRules(dispatcher: MockRequestDispatcher) {
            dispatcher.addRules {
                rule(get, path eq "/vpn/v1/featureconfig/dynamic-bug-reports") {
                    respond(DynamicReportModel(emptyList()))
                }

                rule(get, path eq "/core/v4/notifications") {
                    respond(ApiNotificationsResponse(emptyList()))
                }

                rule(get, path startsWith "/payments/v4/status") {
                    respond("""{"Code":1000,"Card":0,"Paypal":0,"Bitcoin":0,"InApp":0}""")
                }

                rule(get, path eq "/vpn/v1/logicals") { request ->
                    val tier = request.requestUrl?.queryParameter("Tier")?.toInt()
                    val servers = if (tier != null) {
                        MockedServers.logicalsList.filter { it.tier == tier }
                    } else {
                        MockedServers.logicalsList
                    }
                    respond(ServerListV1(servers))
                }
                rule(get, path eq "/vpn/v2/logicals") {
                    val servers = listOf(
                        createLogicalServer(
                            "CH1",
                            exitCountry = "CH",
                            entryCountry = "US",
                            statusIndex = 0u,
                            features = SERVER_FEATURE_SECURE_CORE
                        ),
                        createLogicalServer("PL1", exitCountry = "PL", statusIndex = 1u),
                        createLogicalServer("US1", exitCountry = "US", statusIndex = 2u),
                        createLogicalServer("AR1", exitCountry = "AR", statusIndex = 3u),
                    )
                    respond(LogicalsResponse("StatusID", servers))
                }
                rule(get, path startsWith "/vpn/v2/status/StatusID/binary") {
                    // Status for 4 servers.
                    val bytes = Base64.decode("AQAAAAMyAAAAPwMZAABAPwMyAAAAPwMZAABAPw==")
                    respond(Buffer().write(bytes))
                }

                rule(post, path eq "/vpn/v1/certificate") {
                    respond(CertificateResponse("dummy data", Int.MAX_VALUE.toLong(), Int.MAX_VALUE.toLong()))
                }

                rule(delete, path eq "/auth/v4") {
                    respond(GenericResponse(ResponseCodes.OK))
                }

                rule(get, path eq "/feature/v2/frontend") {
                    respond(GetUnleashTogglesResponse(ResponseCodes.OK, emptyList()))
                }

                rule(get, path eq "/vpn/v1/servers-count") {
                    respond(ServersCountResponse(countryCount = 100, serverCount = 6000))
                }

                // Endpoints that require a simple 1000 response code
                listOf(
                    "/tests/ping",
                    "/core/v4/domains/available",
                    "/data/v1/stats",
                ).forEach { code1000Path ->
                    rule(path eq code1000Path) { respond(GenericResponse(ResponseCodes.OK)) }
                }

                // Endpoints that are called by the app during tests but can be ignored by returning 422 code.
                listOf(
                    "/vpn/v2/clientconfig",
                    "/vpn/v1/cities/names",
                    "/vpn/v1/streamingservices",
                    "/vpn/v1/partners",
                    "/vpn/v1/location",
                    "/vpn/v1/loads",
                    "/core/v4/experiments/NetShield",
                    "/core/v4/pushes/active",
                    "/core/v4/settings",
                    "/core/v4/users",
                    "/core/v4/features"
                ).forEach { unimportantPath ->
                    rule(path eq unimportantPath) { respond(HttpResponseCodes.HTTP_UNPROCESSABLE) }
                }
            }
        }
    }
}
