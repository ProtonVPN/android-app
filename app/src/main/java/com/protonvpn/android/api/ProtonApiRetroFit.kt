/*
 * Copyright (c) 2020 Proton AG
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

import android.os.Build
import com.protonvpn.android.api.data.DebugApiPrefs
import com.protonvpn.android.appconfig.AppConfigResponse
import com.protonvpn.android.appconfig.ForkedSessionResponse
import com.protonvpn.android.appconfig.SessionForkSelectorResponse
import com.protonvpn.android.appconfig.UserCountryPhysical
import com.protonvpn.android.appconfig.UserCountryTelephonyBased
import com.protonvpn.android.appconfig.globalsettings.GlobalSettingsResponse
import com.protonvpn.android.appconfig.globalsettings.UpdateGlobalTelemetry
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.bugreport.DynamicReportModel
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.models.login.SessionForkBody
import com.protonvpn.android.models.login.SessionForkResponse
import com.protonvpn.android.models.login.SessionListResponse
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.models.vpn.CertificateRequestBody
import com.protonvpn.android.models.vpn.CertificateResponse
import com.protonvpn.android.models.vpn.PromoCodesBody
import com.protonvpn.android.models.vpn.ServerSearchResponse
import com.protonvpn.android.models.vpn.UserLocation
import com.protonvpn.android.promooffers.data.ApiNotificationsResponse
import com.protonvpn.android.promooffers.usecase.PostNps
import com.protonvpn.android.servers.api.CityTranslationsResponse
import com.protonvpn.android.servers.api.ConnectingDomainResponse
import com.protonvpn.android.servers.api.LoadsResponse
import com.protonvpn.android.servers.api.LogicalsResponse
import com.protonvpn.android.servers.api.ServerListV1
import com.protonvpn.android.servers.api.ServersCountResponse
import com.protonvpn.android.servers.api.StreamingServicesResponse
import com.protonvpn.android.telemetry.StatsBody
import com.protonvpn.android.telemetry.StatsEvent
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.TimeoutOverride
import me.proton.core.network.domain.session.SessionId
import okhttp3.RequestBody
import retrofit2.Response
import java.net.URLEncoder
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface ProtonApiRetroFit {

    suspend fun getAppConfig(sessionId: SessionId?, netzone: String?): ApiResult<AppConfigResponse>

    suspend fun getDynamicReportConfig(sessionId: SessionId?): ApiResult<DynamicReportModel>

    suspend fun getLocation(): ApiResult<UserLocation>

    suspend fun postBugReport(params: RequestBody): ApiResult<GenericResponse>

    suspend fun getServerCities(languageTag: String): ApiResult<CityTranslationsResponse>

    suspend fun getServerListV1(
        netzone: String?,
        protocols: List<String>,
        lastModified: Long,
        enableTruncation: Boolean,
        mustHaveIDs: Set<String>?,
    ): ApiResult<Response<ServerListV1>>

    suspend fun getServerList(
        netzone: String?,
        protocols: List<String>,
        lastModified: Long,
        enableTruncation: Boolean,
        mustHaveIDs: Set<String>?,
    ): ApiResult<Response<LogicalsResponse>>

    suspend fun getServerByName(nameQuery: String): ApiResult<ServerSearchResponse>

    suspend fun getLoads(netzone: String?): ApiResult<LoadsResponse>

    suspend fun getBinaryStatus(statusId: String): ApiResult<ByteArray>

    suspend fun getStreamingServices(): ApiResult<StreamingServicesResponse>

    suspend fun getServerCountryCount(): ApiResult<ServersCountResponse>

    suspend fun getSessionForkSelector(): ApiResult<SessionForkSelectorResponse>

    suspend fun getForkedSession(selector: String): ApiResult<ForkedSessionResponse>

    suspend fun postSessionFork(
        childClientId: String,
        payload: String,
        isIndependent: Boolean,
        userCode: String? = null,
    ): ApiResult<SessionForkResponse>

    suspend fun getConnectingDomain(domainId: String): ApiResult<ConnectingDomainResponse>

    suspend fun getVPNInfo(sessionId: SessionId? = null): ApiResult<VpnInfoResponse>

    suspend fun getApiNotifications(
        supportedFormats: List<String>,
        fullScreenImageWidthPx: Int,
        fullScreenImageHeightPx: Int,
    ): ApiResult<ApiNotificationsResponse>

    suspend fun logout(): ApiResult<GenericResponse>

    suspend fun getSession(): ApiResult<SessionListResponse>

    suspend fun getAvailableDomains(): ApiResult<GenericResponse>

    suspend fun triggerHumanVerification(): ApiResult<GenericResponse>

    suspend fun getCertificate(sessionId: SessionId, clientPublicKey: String): ApiResult<CertificateResponse>

    suspend fun postPromoCode(code: String): ApiResult<GenericResponse>

    suspend fun dismissNps(): ApiResult<GenericResponse>

    suspend fun postNps(data: PostNps.NpsData): ApiResult<GenericResponse>

    suspend fun postStats(events: List<StatsEvent>): ApiResult<GenericResponse>

    suspend fun putTelemetryGlobalSetting(isEnabled: Boolean): ApiResult<GlobalSettingsResponse>
}

@Singleton
class ProtonApiRetroFitImpl @Inject constructor(
    private val manager: VpnApiManager,
    private val userCountryTelephonyBased: UserCountryTelephonyBased,
    private val userCountry: UserCountryPhysical,
    private val debugApiPrefs: DebugApiPrefs?,
) : ProtonApiRetroFit {

    override suspend fun getAppConfig(sessionId: SessionId?, netzone: String?): ApiResult<AppConfigResponse> =
        manager(sessionId) { getAppConfig(createNetZoneHeaders(netzone)) }

    override suspend fun getDynamicReportConfig(sessionId: SessionId?) =
        manager(sessionId) { getDynamicReportConfig() }

    override suspend fun getLocation() =
        manager { getLocation() }

    override suspend fun postBugReport(
        params: RequestBody,
    ) = manager { postBugReport(TimeoutOverride(writeTimeoutSeconds = 20), params) }

    override suspend fun getServerCities(languageTag: String) = manager {
        getServerCities(languageTag)
    }

    override suspend fun getServerListV1(
        netzone: String?,
        protocols: List<String>,
        lastModified: Long,
        enableTruncation: Boolean,
        mustHaveIDs: Set<String>?,
    ) = manager {
        getServersV1(
            timeoutOverride = TimeoutOverride(readTimeoutSeconds = 20),
            headers = createLogicalsHeaders(netzone, lastModified, enableTruncation),
            protocols = protocols.joinToString(","),
            withState = true,
            userTier = null,
            includeIDs = mustHaveIDs.takeIf { enableTruncation }?.encodeParamSet()
        )
    }

    override suspend fun getServerList(
        netzone: String?,
        protocols: List<String>,
        lastModified: Long,
        enableTruncation: Boolean,
        mustHaveIDs: Set<String>?,
    ) = manager {
        getServers(
            timeoutOverride = TimeoutOverride(readTimeoutSeconds = 20),
            headers = createLogicalsHeaders(netzone, lastModified, enableTruncation),
            protocols = protocols.joinToString(","),
            withState = true,
            includeIDs = mustHaveIDs.takeIf { enableTruncation }?.encodeParamSet()
        )
    }

    override suspend fun getServerByName(nameQuery: String) =
        manager { getServerByName(nameQuery) }

    override suspend fun getLoads(netzone: String?) =
        manager {
            getLoads(
                headers = createNetZoneHeaders(netzone),
                userTier = null
            )
        }

    override suspend fun getBinaryStatus(statusId: String) =
        manager { getBinaryStatus(statusId) }

    override suspend fun getStreamingServices() =
        manager { getStreamingServices() }

    override suspend fun getServerCountryCount() =
        manager { getServersCount() }

    override suspend fun getSessionForkSelector() =
        manager { getSessionForkSelector() }

    override suspend fun getForkedSession(selector: String) =
        manager { getForkedSession(selector) }

    override suspend fun postSessionFork(
        childClientId: String, payload: String, isIndependent: Boolean, userCode: String?
    ) = manager { postSessionFork(SessionForkBody(payload, childClientId, if (isIndependent) 1 else 0, userCode)) }

    override suspend fun getConnectingDomain(domainId: String) =
        manager { getServerDomain(domainId) }

    override suspend fun getVPNInfo(sessionId: SessionId?) =
        manager(sessionId) { getVPNInfo() }

    override suspend fun getApiNotifications(
        supportedFormats: List<String>,
        fullScreenImageWidthPx: Int,
        fullScreenImageHeightPx: Int
    ) = manager {
        getApiNotifications(supportedFormats.joinToString(","), fullScreenImageWidthPx, fullScreenImageHeightPx)
    }

    override suspend fun logout() =
        manager { postLogout() }

    override suspend fun getSession(): ApiResult<SessionListResponse> =
        manager { getSession() }

    override suspend fun getAvailableDomains(): ApiResult<GenericResponse> =
        manager { getAvailableDomains() }

    override suspend fun triggerHumanVerification(): ApiResult<GenericResponse> =
        manager { triggerHumanVerification() }

    override suspend fun getCertificate(sessionId: SessionId, clientPublicKey: String): ApiResult<CertificateResponse> =
        manager(sessionId) {
            getCertificate(CertificateRequestBody(
                clientPublicKey, "EC", Build.MODEL, "session", emptyList()))
        }

    override suspend fun postPromoCode(code: String): ApiResult<GenericResponse> =
        manager { postPromoCode(PromoCodesBody("VPN", listOf(code))) }

    override suspend fun dismissNps(): ApiResult<GenericResponse> =
        manager { postDismissNps(createNpsHeaders()) }

    override suspend fun postNps(data: PostNps.NpsData): ApiResult<GenericResponse> =
        manager { postNps(createNpsHeaders(), data) }

    override suspend fun postStats(events: List<StatsEvent>): ApiResult<GenericResponse> =
        manager { postStats(StatsBody(events)) }

    override suspend fun putTelemetryGlobalSetting(isEnabled: Boolean): ApiResult<GlobalSettingsResponse> =
        manager { putTelemetryGlobalSetting(UpdateGlobalTelemetry(isEnabled)) }

    private fun createNetZoneHeaders(netzone: String?) =
        mutableMapOf<String, String>().apply {
            val effectiveMCC = userCountryTelephonyBased()?.countryCode
            if (effectiveMCC != null)
                put(ProtonVPNRetrofit.HEADER_COUNTRY, effectiveMCC)
            val effectiveNetzone = debugApiPrefs?.netzone ?: netzone
            if (!effectiveNetzone.isNullOrEmpty())
                put(ProtonVPNRetrofit.HEADER_NETZONE, effectiveNetzone)
            ProtonLogger.logCustom(LogCategory.API, "netzone: $effectiveNetzone, mcc: $effectiveMCC")
        }

    private fun createNpsHeaders() =
        HashMap<String, String>().apply {
            val userCountryCode = userCountry()?.countryCode
            if (userCountryCode != null) {
                put(ProtonVPNRetrofit.HEADER_COUNTRY, userCountryCode)
            }
        }

    private fun createLogicalsHeaders(netzone: String?, lastModified: Long, enableTruncation: Boolean,) =
        createNetZoneHeaders(netzone) + buildMap {
            put("If-Modified-Since", httpHeaderDateFormatter.format(Instant.ofEpochMilli(lastModified)))
            if (enableTruncation)
                put("x-pm-response-truncation-permitted", "true")
        }

    private fun Set<String>.encodeParamSet(): Set<String> =
        map { URLEncoder.encode(it, "UTF-8") }.toSet()
}
