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

import com.protonvpn.android.appconfig.ApiNotificationsResponse
import com.protonvpn.android.models.config.bugreport.DynamicReportModel
import com.protonvpn.android.models.vpn.CertificateResponse
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestUser
import me.proton.core.network.data.protonApi.GenericResponse
import me.proton.core.network.domain.HttpResponseCodes
import me.proton.core.network.domain.ResponseCodes

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

        private fun addBasicRules(dispatcher: MockRequestDispatcher) {
            dispatcher.addRules {
                rule(get, path eq "/vpn/featureconfig/dynamic-bug-reports") {
                    respond(DynamicReportModel(emptyList()))
                }

                rule(get, path eq "/core/v4/notifications") {
                    respond(ApiNotificationsResponse(emptyList()))
                }

                rule(get, path eq "/payments/v4/status/fdroid") {
                    respond("""{"Code":1000,"Card":0,"Paypal":0,"Bitcoin":0,"InApp":0}""")
                }

                rule(get, path eq "/vpn/logicals") {
                    respond(ServerList(MockedServers.serverList))
                }

                rule(post, path eq "/vpn/v1/certificate") {
                    respond(CertificateResponse("dummy data", Int.MAX_VALUE.toLong(), Int.MAX_VALUE.toLong()))
                }

                rule(delete, path eq "/auth") {
                    respond(GenericResponse(ResponseCodes.OK))
                }

                // Endpoints that require a simple 1000 response code
                listOf(
                    "/tests/ping",
                    "/domains/available"
                ).forEach { code1000Path ->
                    rule(path eq code1000Path) { respond(GenericResponse(ResponseCodes.OK)) }
                }

                // Endpoints that are called by the app during tests but can be ignored by returning 422 code.
                listOf(
                    "/vpn/v2/clientconfig",
                    "/vpn/streamingservices",
                    "/vpn/v1/partners",
                    "/vpn/location",
                    "/vpn/loads",
                    "/core/v4/experiments/NetShield"
                ).forEach { unimportantPath ->
                    rule(path eq unimportantPath) { respond(HttpResponseCodes.HTTP_UNPROCESSABLE) }
                }
            }
        }
    }
}
