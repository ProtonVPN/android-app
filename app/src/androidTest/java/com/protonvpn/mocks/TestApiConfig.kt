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
import okhttp3.mock.MockInterceptor
import okhttp3.mock.delete
import okhttp3.mock.endsWith
import okhttp3.mock.get
import okhttp3.mock.path
import okhttp3.mock.post
import okhttp3.mock.respond
import okhttp3.mock.rule

class UnmockedApiCallException(message: String): IllegalArgumentException(message)

sealed class TestApiConfig {
    object Backend : TestApiConfig()

    class Mocked(
        private val testUser: TestUser? = null,
        private val additionalRules: (MockInterceptor.() -> Unit)? = null
    ) : TestApiConfig() {

        fun addDefaultRules(interceptor: MockInterceptorWrapper) {
            if (additionalRules != null) interceptor.addRules(additionalRules)
            addBasicRules(interceptor)
            if (testUser != null) addVpnInfoRule(interceptor, testUser)

            // Make sure that we mocked everything - this has to be the last rule.
            interceptor.addRules {
                rule(times = Int.MAX_VALUE) {
                    respond {
                        // If you get this exception it probably means some new API call has been added to the
                        // application. This API call needs to be mocked either in this method (if it's generic) or in a
                        // specific test case that triggers the call.
                        throw UnmockedApiCallException("Unmocked call: ${it.method} ${it.url}")
                    }
                }
            }
        }

        private fun addVpnInfoRule(interceptor: MockInterceptorWrapper, testUser: TestUser) {
            interceptor.addRules {
                rule(get, path endsWith "/vpn/v2", times = Int.MAX_VALUE) {
                    respond(testUser.vpnInfoResponse)
                }
            }
        }

        private fun addBasicRules(interceptor: MockInterceptorWrapper) {
            interceptor.addRules {
                rule(get, path endsWith "/vpn/featureconfig/dynamic-bug-reports", times = Int.MAX_VALUE) {
                    respond(DynamicReportModel(emptyList()))
                }

                rule(get, path endsWith "/core/v4/notifications", times = Int.MAX_VALUE) {
                    respond(ApiNotificationsResponse(emptyList()))
                }

                rule(get, path endsWith "/payments/v4/status/fdroid", times = Int.MAX_VALUE) {
                    respond("""{"Code":1000,"Card":0,"Paypal":0,"Bitcoin":0,"InApp":0}""")
                }

                rule(get, path endsWith "/vpn/logicals", times = Int.MAX_VALUE) {
                    respond(ServerList(MockedServers.serverList))
                }

                rule(post, path endsWith "/vpn/v1/certificate") {
                    respond(CertificateResponse("dummy data", Int.MAX_VALUE.toLong(), Int.MAX_VALUE.toLong()))
                }

                rule(delete, path endsWith "/auth", times = Int.MAX_VALUE) {
                    respond(GenericResponse(ResponseCodes.OK))
                }

                // Endpoints that require a simple 1000 response code
                listOf(
                    "/tests/ping",
                    "/domains/available"
                ).forEach { code1000Path ->
                    rule(path endsWith code1000Path, times = Int.MAX_VALUE) {
                        respond(GenericResponse(ResponseCodes.OK))
                    }
                }

                // Endpoints that are called by the app during tests but can be ignored by returning 422 code.
                listOf(
                    "/vpn/v2/clientconfig",
                    "/vpn/streamingservices",
                    "/vpn/location"
                ).forEach { unimportantPath ->
                    rule(path endsWith unimportantPath, times = Int.MAX_VALUE) {
                        respond(HttpResponseCodes.HTTP_UNPROCESSABLE)
                    }
                }
            }
        }
    }
}

