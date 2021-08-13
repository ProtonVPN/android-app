/*
 * Copyright (c) 2017 Proton Technologies AG
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
package com.protonvpn.android.api

import com.protonvpn.android.appconfig.AppConfigResponse
import com.protonvpn.android.appconfig.ApiNotificationsResponse
import com.protonvpn.android.appconfig.ForkedSessionResponse
import com.protonvpn.android.appconfig.SessionForkSelectorResponse
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.models.login.LoginBody
import com.protonvpn.android.models.login.LoginInfoBody
import com.protonvpn.android.models.login.LoginInfoResponse
import com.protonvpn.android.models.login.LoginResponse
import com.protonvpn.android.models.login.SessionListResponse
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.models.vpn.CertificateRequestBody
import com.protonvpn.android.models.vpn.CertificateResponse
import com.protonvpn.android.models.vpn.ConnectingDomainResponse
import com.protonvpn.android.models.vpn.LoadsResponse
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.android.models.vpn.StreamingServicesResponse
import com.protonvpn.android.models.vpn.UserLocation
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@Suppress("ComplexInterface")
interface ProtonVPNRetrofit : BaseRetrofitApi {

    @GET("vpn/logicals")
    suspend fun getServers(@Query("IP") ip: String?): ServerList

    @GET("vpn/loads")
    suspend fun getLoads(@Query("IP") ip: String?): LoadsResponse

    @GET("vpn/streamingservices")
    suspend fun getStreamingServices(): StreamingServicesResponse

    @GET("vpn/servers/{serverId}")
    suspend fun getServerDomain(@Path(value = "serverId", encoded = true) serverId: String): ConnectingDomainResponse

    @POST("auth/info")
    suspend fun postLoginInfo(@Body body: LoginInfoBody): LoginInfoResponse

    @POST("auth")
    suspend fun postLogin(@Body body: LoginBody): LoginResponse

    @DELETE("auth")
    suspend fun postLogout(): GenericResponse

    @GET("auth/sessions/forks")
    suspend fun getSessionForkSelector(): SessionForkSelectorResponse

    @GET("auth/sessions/forks/{selector}")
    suspend fun getForkedSession(@Path(value = "selector", encoded = true) selector: String):
            ForkedSessionResponse

    @GET("vpn")
    suspend fun getVPNInfo(): VpnInfoResponse

    @GET("vpn/sessions")
    suspend fun getSession(): SessionListResponse

    @GET("vpn/location")
    suspend fun getLocation(): UserLocation

    @POST("reports/bug")
    suspend fun postBugReport(@Body params: RequestBody): GenericResponse

    @GET("vpn/v2/clientconfig")
    suspend fun getAppConfig(): AppConfigResponse

    @GET("core/v4/notifications")
    suspend fun getApiNotifications(): ApiNotificationsResponse

    @GET("domains/available")
    suspend fun getAvailableDomains(@Query("Type") type: String = "login"): GenericResponse

    @POST("vpn/v1/certificate")
    suspend fun getCertificate(@Body params: CertificateRequestBody): CertificateResponse
}
