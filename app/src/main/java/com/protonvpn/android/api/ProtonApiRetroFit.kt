/*
 * Copyright (c) 2020 Proton Technologies AG
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
import com.protonvpn.android.appconfig.AppConfigResponse
import com.protonvpn.android.components.LoaderUI
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.models.login.LoginBody
import com.protonvpn.android.models.login.LoginInfoBody
import com.protonvpn.android.models.login.SessionListResponse
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.models.vpn.CertificateRequestBody
import com.protonvpn.android.models.vpn.CertificateResponse
import com.protonvpn.android.utils.NetUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.proton.core.network.data.protonApi.RefreshTokenRequest
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.session.SessionId
import okhttp3.RequestBody

//TODO: remove dependencies on activity/network loaders, refactor callbacks to suspending functions
open class ProtonApiRetroFit(val scope: CoroutineScope, private val manager: VpnApiManager) {

    open suspend fun getAppConfig(): ApiResult<AppConfigResponse> =
        manager { getAppConfig() }

    open suspend fun getDynamicReportConfig() =
        manager { getDynamicReportConfig() }

    open suspend fun getLocation() =
        manager { getLocation() }

    suspend fun refreshToken(request: RefreshTokenRequest) =
        manager { refreshToken(request) }

    open suspend fun postBugReport(
        params: RequestBody,
    ) = manager { postBugReport(params) }

    open suspend fun getServerList(loader: LoaderUI?, ip: String?, lang: String) =
        makeCall(loader) { it.getServers(createNetZoneHeaders(ip), lang) }

    open suspend fun getLoads(ip: String?) =
        manager { getLoads(createNetZoneHeaders(ip)) }

    open suspend fun getStreamingServices() =
        manager { getStreamingServices() }

    suspend fun postLogin(body: LoginBody) =
        manager { postLogin(body) }

    open suspend fun getSessionForkSelector() =
        manager { getSessionForkSelector() }

    open suspend fun getForkedSession(selector: String) =
        manager { getForkedSession(selector) }

    open suspend fun getConnectingDomain(domainId: String) =
        manager { getServerDomain(domainId) }

    suspend fun postLoginInfo(email: String) =
        manager { postLoginInfo(LoginInfoBody(email)) }

    suspend fun getFeature(feature: String) =
        manager { getFeature(feature) }

    open suspend fun getVPNInfo(sessionId: SessionId? = null) =
        manager(sessionId) { getVPNInfo() }

    open fun getVPNInfo(callback: NetworkResultCallback<VpnInfoResponse>) =
        makeCall(callback) { it.getVPNInfo() }

    open suspend fun getApiNotifications() =
        manager { getApiNotifications() }

    open suspend fun logout() =
        manager { postLogout() }

    open suspend fun getSession(): ApiResult<SessionListResponse> =
        manager { getSession() }

    open suspend fun getAvailableDomains(): ApiResult<GenericResponse> =
        manager { getAvailableDomains() }

    open suspend fun triggerHumanVerification(): ApiResult<GenericResponse> =
        manager { triggerHumanVerification() }

    open suspend fun getCertificate(clientPublicKey: String): ApiResult<CertificateResponse> =
        manager {
            getCertificate(CertificateRequestBody(
                clientPublicKey, "EC", Build.MODEL, "session", emptyList()))
        }

    private suspend fun <T> makeCall(
        loader: LoaderUI?,
        callFun: suspend (ProtonVPNRetrofit) -> T
    ): ApiResult<T> {
        loader?.switchToLoading()
        val result = manager(block = callFun)
        when (result) {
            is ApiResult.Success -> {
                loader?.switchToEmpty()
            }
            is ApiResult.Error -> {
                loader?.switchToRetry(result)
            }
        }
        return result
    }

    private fun <T> makeCall(
        callback: NetworkResultCallback<T>,
        loader: LoaderUI? = null,
        callFun: suspend (ProtonVPNRetrofit) -> T
    ) = scope.launch {
        loader?.switchToLoading()
        when (val result = manager(block = callFun)) {
            is ApiResult.Success -> {
                loader?.switchToEmpty()
                callback.onSuccess(result.value)
            }
            is ApiResult.Error -> {
                loader?.switchToRetry(result)
                callback.onFailure()
            }
        }
    }

    // Used in routes that provide server information including a score of how good a server is
    // for the particular user to connect to.
    // To provide relevant scores even when connected to VPN, we send a truncated version of
    // the user's public IP address. In keeping with our no-logs policy, this partial IP address
    // is not stored on the server and is only used to fulfill this one-off API request.
    private fun createNetZoneHeaders(ip: String?) =
        if (!ip.isNullOrEmpty()) {
            val netzone = NetUtils.stripIP(ip)
            mapOf(ProtonVPNRetrofit.HEADER_NETZONE to netzone)
        } else {
            emptyMap()
        }
}
