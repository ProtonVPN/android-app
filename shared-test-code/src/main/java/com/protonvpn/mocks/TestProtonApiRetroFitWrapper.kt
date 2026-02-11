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

package com.protonvpn.mocks

import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfigResponse
import com.protonvpn.android.appconfig.ForkedSessionResponse
import com.protonvpn.android.appconfig.SessionForkSelectorResponse
import com.protonvpn.android.appconfig.globalsettings.GlobalSettingsResponse
import com.protonvpn.android.models.config.bugreport.DynamicReportModel
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.models.login.SessionForkResponse
import com.protonvpn.android.models.login.SessionListResponse
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.models.vpn.CertificateResponse
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
import com.protonvpn.android.telemetry.StatsEvent
import java.util.concurrent.atomic.AtomicReference
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.session.SessionId
import okhttp3.RequestBody
import retrofit2.Response

class TestProtonApiRetroFitWrapper(private val apiHolder: AtomicReference<ProtonApiRetroFit>) :
    ProtonApiRetroFit {

    constructor(api: ProtonApiRetroFit) : this(AtomicReference(api))

    var api: ProtonApiRetroFit
        get() = apiHolder.get()
        set(value) = apiHolder.set(value)

    override suspend fun getAppConfig(
        sessionId: SessionId?,
        netzone: String?,
    ): ApiResult<AppConfigResponse> = api.getAppConfig(sessionId, netzone)

    override suspend fun getDynamicReportConfig(
        sessionId: SessionId?
    ): ApiResult<DynamicReportModel> = api.getDynamicReportConfig(sessionId)

    override suspend fun getLocation(): ApiResult<UserLocation> = api.getLocation()

    override suspend fun postBugReport(params: RequestBody): ApiResult<GenericResponse> =
        api.postBugReport(params)

    override suspend fun getServerCities(languageTag: String): ApiResult<CityTranslationsResponse> =
        api.getServerCities(languageTag)

    override suspend fun getServerListV1(
        netzone: String?,
        protocols: List<String>,
        lastModified: Long,
        enableTruncation: Boolean,
        mustHaveIDs: Set<String>?,
    ): ApiResult<Response<ServerListV1>> =
        api.getServerListV1(netzone, protocols, lastModified, enableTruncation, mustHaveIDs)

    override suspend fun getServerList(
        netzone: String?,
        protocols: List<String>,
        lastModified: Long,
        enableTruncation: Boolean,
        mustHaveIDs: Set<String>?,
    ): ApiResult<Response<LogicalsResponse>> =
        api.getServerList(netzone, protocols, lastModified, enableTruncation, mustHaveIDs)

    override suspend fun getServerByName(nameQuery: String): ApiResult<ServerSearchResponse> =
        api.getServerByName(nameQuery)

    override suspend fun getLoads(netzone: String?): ApiResult<LoadsResponse> =
        api.getLoads(netzone)

    override suspend fun getBinaryStatus(statusId: String): ApiResult<ByteArray> =
        api.getBinaryStatus(statusId)

    override suspend fun getStreamingServices(): ApiResult<StreamingServicesResponse> =
        api.getStreamingServices()

    override suspend fun getServerCountryCount(): ApiResult<ServersCountResponse> =
        api.getServerCountryCount()

    override suspend fun getSessionForkSelector(): ApiResult<SessionForkSelectorResponse> =
        api.getSessionForkSelector()

    override suspend fun getForkedSession(selector: String): ApiResult<ForkedSessionResponse> =
        api.getForkedSession(selector)

    override suspend fun postSessionFork(
        childClientId: String,
        payload: String,
        isIndependent: Boolean,
        userCode: String?,
    ): ApiResult<SessionForkResponse> =
        api.postSessionFork(childClientId, payload, isIndependent, userCode)

    override suspend fun getConnectingDomain(
        domainId: String
    ): ApiResult<ConnectingDomainResponse> = api.getConnectingDomain(domainId)

    override suspend fun getVPNInfo(sessionId: SessionId?): ApiResult<VpnInfoResponse> =
        api.getVPNInfo(sessionId)

    override suspend fun getApiNotifications(
        supportedFormats: List<String>,
        fullScreenImageWidthPx: Int,
        fullScreenImageHeightPx: Int,
    ): ApiResult<ApiNotificationsResponse> =
        api.getApiNotifications(supportedFormats, fullScreenImageWidthPx, fullScreenImageHeightPx)

    override suspend fun logout(): ApiResult<GenericResponse> = api.logout()

    override suspend fun getSession(): ApiResult<SessionListResponse> = api.getSession()

    override suspend fun getAvailableDomains(): ApiResult<GenericResponse> =
        api.getAvailableDomains()

    override suspend fun triggerHumanVerification(): ApiResult<GenericResponse> =
        api.triggerHumanVerification()

    override suspend fun getCertificate(
        sessionId: SessionId,
        clientPublicKey: String,
    ): ApiResult<CertificateResponse> = api.getCertificate(sessionId, clientPublicKey)

    override suspend fun postPromoCode(code: String): ApiResult<GenericResponse> =
        api.postPromoCode(code)

    override suspend fun dismissNps(): ApiResult<GenericResponse> = api.dismissNps()

    override suspend fun postNps(data: PostNps.NpsData): ApiResult<GenericResponse> =
        api.postNps(data)

    override suspend fun postStats(events: List<StatsEvent>): ApiResult<GenericResponse> =
        api.postStats(events)

    override suspend fun putTelemetryGlobalSetting(
        isEnabled: Boolean
    ): ApiResult<GlobalSettingsResponse> = api.putTelemetryGlobalSetting(isEnabled)
}
