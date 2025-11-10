/*
 * Copyright (c) 2017 Proton AG
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

import com.protonvpn.android.appconfig.ApiNotificationsResponse
import com.protonvpn.android.appconfig.AppConfigResponse
import com.protonvpn.android.appconfig.ForkedSessionResponse
import com.protonvpn.android.appconfig.SessionForkSelectorResponse
import com.protonvpn.android.appconfig.globalsettings.GlobalSettingsResponse
import com.protonvpn.android.appconfig.globalsettings.UpdateGlobalTelemetry
import com.protonvpn.android.models.config.bugreport.DynamicReportModel
import com.protonvpn.android.models.login.FeatureResponse
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.models.login.SessionForkBody
import com.protonvpn.android.models.login.SessionForkResponse
import com.protonvpn.android.models.login.SessionListResponse
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.models.vpn.CertificateRequestBody
import com.protonvpn.android.models.vpn.CertificateResponse
import com.protonvpn.android.servers.api.ConnectingDomainResponse
import com.protonvpn.android.servers.api.LoadsResponse
import com.protonvpn.android.models.vpn.PromoCodesBody
import com.protonvpn.android.servers.api.ServerListV1
import com.protonvpn.android.models.vpn.ServerSearchResponse
import com.protonvpn.android.servers.api.ServersCountResponse
import com.protonvpn.android.servers.api.StreamingServicesResponse
import com.protonvpn.android.models.vpn.UserLocation
import com.protonvpn.android.servers.api.LogicalsResponse
import com.protonvpn.android.telemetry.StatsBody
import com.protonvpn.android.ui.promooffers.usecase.PostNps
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import me.proton.core.network.domain.TimeoutOverride
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import retrofit2.http.Tag

@Suppress("ComplexInterface", "TooManyFunctions")
interface ProtonVPNRetrofit : BaseRetrofitApi {

    @GET("vpn/v1/logicals")
    suspend fun getServersV1(
        @Tag timeoutOverride: TimeoutOverride,
        @HeaderMap headers: Map<String, String>,
        @Query("WithTranslations") language: String,
        @Query("WithEntriesForProtocols") protocols: String,
        @Query("WithState") withState: Boolean,
        @Query("Tier") userTier: Int?,
        @Query("IncludeID[]", encoded = true) includeIDs: Set<String>?,
    ): Response<ServerListV1>

    @GET("vpn/v2/logicals")
    suspend fun getServers(
        @Tag timeoutOverride: TimeoutOverride,
        @HeaderMap headers: Map<String, String>,
        @Query("WithTranslations") language: String,
        @Query("WithEntriesForProtocols") protocols: String,
        @Query("WithState") withState: Boolean,
        @Query("IncludeID[]", encoded = true) includeIDs: Set<String>?,
    ): Response<LogicalsResponse>

    @GET("vpn/v1/logicals/lookup/{nameQuery}")
    suspend fun getServerByName(
        @Path(value = "nameQuery", encoded = true) nameQuery: String
    ): ServerSearchResponse

    @GET("vpn/v1/loads")
    suspend fun getLoads(
        @HeaderMap headers: Map<String, String>,
        @Query("Tier") userTier: Int?
    ): LoadsResponse

    @GET("vpn/v2/status/{id}/binary")
    suspend fun getBinaryStatus(@Path(value = "id", encoded = true) id: String): ByteArray

    @GET("vpn/v1/streamingservices")
    suspend fun getStreamingServices(): StreamingServicesResponse

    @GET("vpn/v1/servers/{serverId}")
    suspend fun getServerDomain(@Path(value = "serverId", encoded = true) serverId: String): ConnectingDomainResponse

    @GET("vpn/v1/servers-count")
    suspend fun getServersCount(): ServersCountResponse

    @DELETE("auth/v4")
    suspend fun postLogout(): GenericResponse

    @GET("auth/v4/sessions/forks")
    suspend fun getSessionForkSelector(): SessionForkSelectorResponse

    @GET("auth/v4/sessions/forks/{selector}")
    suspend fun getForkedSession(@Path(value = "selector", encoded = true) selector: String): ForkedSessionResponse

    @POST("auth/v4/sessions/forks")
    suspend fun postSessionFork(@Body body: SessionForkBody): SessionForkResponse

    @GET("vpn/v2")
    suspend fun getVPNInfo(): VpnInfoResponse

    @GET("vpn/v1/sessions")
    suspend fun getSession(): SessionListResponse

    @GET("vpn/v1/location")
    suspend fun getLocation(): UserLocation

    @POST("core/v4/reports/bug")
    suspend fun postBugReport(@Tag timeoutOverride: TimeoutOverride, @Body params: RequestBody): GenericResponse

    @GET("vpn/v2/clientconfig")
    suspend fun getAppConfig(@HeaderMap headers: Map<String, String>): AppConfigResponse

    @POST("vpn/v1/nps/submit")
    suspend fun postNps(@Body data: PostNps.NpsData): GenericResponse

    @POST("vpn/v1/nps/dismiss")
    suspend fun postDismissNps(): GenericResponse

    @GET("core/v4/notifications")
    suspend fun getApiNotifications(
        @Query("FullScreenImageSupport") supportedFormats: String,
        @Query("FullScreenImageWidth") fullScreenImageWidthPx: Int,
        @Query("FullScreenImageHeight") fullScreenImageHeightPx: Int
    ): ApiNotificationsResponse

    @GET("core/v4/domains/available")
    suspend fun getAvailableDomains(@Query("Type") type: String = "login"): GenericResponse

    @POST("vpn/v1/certificate")
    suspend fun getCertificate(@Body params: CertificateRequestBody): CertificateResponse

    @GET("vpn/v1/featureconfig/dynamic-bug-reports")
    suspend fun getDynamicReportConfig(): DynamicReportModel

    @GET("internal/tests/humanverification")
    suspend fun triggerHumanVerification(): GenericResponse

    @GET("core/v4/features/{id}")
    suspend fun getFeature(@Path("id") id: String): FeatureResponse

    @POST("payments/v4/promocode")
    suspend fun postPromoCode(@Body params: PromoCodesBody): GenericResponse

    @POST("data/v1/stats/multiple")
    suspend fun postStats(@Body data: StatsBody): GenericResponse

    @PUT("core/v4/settings/telemetry")
    suspend fun putTelemetryGlobalSetting(@Body body: UpdateGlobalTelemetry): GlobalSettingsResponse

    companion object {
        const val HEADER_NETZONE = "x-pm-netzone"
        const val HEADER_COUNTRY = "x-pm-country"
    }
}
