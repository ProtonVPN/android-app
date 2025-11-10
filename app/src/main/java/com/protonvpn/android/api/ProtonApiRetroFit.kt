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
import com.protonvpn.android.appconfig.UserCountryTelephonyBased
import com.protonvpn.android.appconfig.globalsettings.GlobalSettingsResponse
import com.protonvpn.android.appconfig.globalsettings.UpdateGlobalTelemetry
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.models.login.SessionForkBody
import com.protonvpn.android.models.login.SessionListResponse
import com.protonvpn.android.models.vpn.CertificateRequestBody
import com.protonvpn.android.models.vpn.CertificateResponse
import com.protonvpn.android.models.vpn.PromoCodesBody
import com.protonvpn.android.telemetry.StatsBody
import com.protonvpn.android.telemetry.StatsEvent
import com.protonvpn.android.ui.promooffers.usecase.PostNps
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.TimeoutOverride
import me.proton.core.network.domain.session.SessionId
import okhttp3.RequestBody
import java.net.URLEncoder
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class ProtonApiRetroFit @Inject constructor(
    private val manager: VpnApiManager,
    private val userCountryTelephonyBased: UserCountryTelephonyBased,
    private val debugApiPrefs: DebugApiPrefs?,
) {
    open suspend fun getAppConfig(sessionId: SessionId?, netzone: String?): ApiResult<AppConfigResponse> =
        manager(sessionId) { getAppConfig(createNetZoneHeaders(netzone)) }

    open suspend fun getDynamicReportConfig(sessionId: SessionId?) =
        manager(sessionId) { getDynamicReportConfig() }

    open suspend fun getLocation() =
        manager { getLocation() }

    open suspend fun postBugReport(
        params: RequestBody,
    ) = manager { postBugReport(TimeoutOverride(writeTimeoutSeconds = 20), params) }

    open suspend fun getServerListV1(
        netzone: String?,
        lang: String,
        protocols: List<String>,
        freeOnly: Boolean,
        lastModified: Long,
        enableTruncation: Boolean,
        mustHaveIDs: Set<String>?,
    ) = manager {
        getServersV1(
            timeoutOverride = TimeoutOverride(readTimeoutSeconds = 20),
            headers = createLogicalsHeaders(netzone, lastModified, enableTruncation),
            language = lang,
            protocols = protocols.joinToString(","),
            withState = true,
            userTier = if (freeOnly) VpnUser.FREE_TIER else null,
            includeIDs = mustHaveIDs.takeIf { enableTruncation }?.encodeParamSet()
        )
    }

    suspend fun getServerList(
        netzone: String?,
        lang: String,
        protocols: List<String>,
        lastModified: Long,
        enableTruncation: Boolean,
        mustHaveIDs: Set<String>?,
    ) = manager {
        getServers(
            timeoutOverride = TimeoutOverride(readTimeoutSeconds = 20),
            headers = createLogicalsHeaders(netzone, lastModified, enableTruncation),
            language = lang,
            protocols = protocols.joinToString(","),
            withState = true,
            includeIDs = mustHaveIDs.takeIf { enableTruncation }?.encodeParamSet()
        )
    }

    open suspend fun getServerByName(nameQuery: String) =
        manager { getServerByName(nameQuery) }

    open suspend fun getLoads(netzone: String?, freeOnly: Boolean) =
        manager {
            getLoads(
                createNetZoneHeaders(netzone),
                if (freeOnly) VpnUser.FREE_TIER else null
            )
        }

    open suspend fun getBinaryStatus(statusId: String) =
        manager { getBinaryStatus(statusId) }

    open suspend fun getStreamingServices() =
        manager { getStreamingServices() }

    open suspend fun getServerCountryCount() =
        manager { getServersCount() }

    open suspend fun getSessionForkSelector() =
        manager { getSessionForkSelector() }

    open suspend fun getForkedSession(selector: String) =
        manager { getForkedSession(selector) }

    open suspend fun postSessionFork(
        childClientId: String, payload: String, isIndependent: Boolean, userCode: String? = null
    ) = manager { postSessionFork(SessionForkBody(payload, childClientId, if (isIndependent) 1 else 0, userCode)) }

    open suspend fun getConnectingDomain(domainId: String) =
        manager { getServerDomain(domainId) }

    open suspend fun getVPNInfo(sessionId: SessionId? = null) =
        manager(sessionId) { getVPNInfo() }

    open suspend fun getApiNotifications(
        supportedFormats: List<String>,
        fullScreenImageWidthPx: Int,
        fullScreenImageHeightPx: Int
    ) = manager {
        getApiNotifications(supportedFormats.joinToString(","), fullScreenImageWidthPx, fullScreenImageHeightPx)
    }

    open suspend fun logout() =
        manager { postLogout() }

    open suspend fun getSession(): ApiResult<SessionListResponse> =
        manager { getSession() }

    open suspend fun getAvailableDomains(): ApiResult<GenericResponse> =
        manager { getAvailableDomains() }

    open suspend fun triggerHumanVerification(): ApiResult<GenericResponse> =
        manager { triggerHumanVerification() }

    open suspend fun getCertificate(sessionId: SessionId, clientPublicKey: String): ApiResult<CertificateResponse> =
        manager(sessionId) {
            getCertificate(CertificateRequestBody(
                clientPublicKey, "EC", Build.MODEL, "session", emptyList()))
        }

    open suspend fun postPromoCode(code: String): ApiResult<GenericResponse> =
        manager { postPromoCode(PromoCodesBody("VPN", listOf(code))) }

    suspend fun dismissNps(): ApiResult<GenericResponse> =
        manager { postDismissNps() }

    suspend fun postNps(data: PostNps.NpsData): ApiResult<GenericResponse> =
        manager { postNps(data) }

    suspend fun postStats(events: List<StatsEvent>): ApiResult<GenericResponse> =
        manager { postStats(StatsBody(events)) }

    suspend fun putTelemetryGlobalSetting(isEnabled: Boolean): ApiResult<GlobalSettingsResponse> =
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

    private fun createLogicalsHeaders(netzone: String?, lastModified: Long, enableTruncation: Boolean,) =
        createNetZoneHeaders(netzone) + buildMap {
            put("If-Modified-Since", httpHeaderDateFormatter.format(Instant.ofEpochMilli(lastModified)))
            if (enableTruncation)
                put("x-pm-response-truncation-permitted", "true")
        }

    private fun Set<String>.encodeParamSet(): Set<String> =
        map { URLEncoder.encode(it, "UTF-8") }.toSet()
}
