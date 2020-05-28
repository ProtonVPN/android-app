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

import android.content.Context
import com.protonvpn.android.models.config.UserData
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Response
import java.util.concurrent.TimeUnit

abstract class AlternativeApiManager(
    private val primaryDomain: String,
    private val userData: UserData,
    private val now: () -> Long
) {

    interface DnsOverHttpsProvider {
        suspend fun getAlternativeBaseUrls(domain: String): List<String>?
    }

    companion object {
        val ALTERNATIVE_API_ACTIVE_PERIOD = TimeUnit.DAYS.toMillis(1)
        private const val DOH_PROVIDER_TIMEOUT = 10_000L
    }

    private var activeBackend: ApiBackendRetrofit<ProtonVPNRetrofit>? = null
    private var alternativeBaseUrls: List<String>? = userData.alternativeApiBaseUrls
    private val dohProviders by lazy { getDnsOverHttpsProviders() }

    fun getActiveBackend(): ApiBackendRetrofit<ProtonVPNRetrofit>? {
        val apiFailedTimestamp = userData.lastPrimaryApiFail
        if (now() - apiFailedTimestamp >= ALTERNATIVE_API_ACTIVE_PERIOD) {
            clearActiveBackend()
        } else if (activeBackend == null) {
            val baseUrl = userData.activeAlternativeApiBaseUrl
            if (baseUrl != null)
                activeBackend = createAltBackend(baseUrl)
        }
        return activeBackend
    }

    private fun setActiveBackend(altBackend: ApiBackendRetrofit<ProtonVPNRetrofit>) {
        activeBackend = altBackend
        userData.activeAlternativeApiBaseUrl = altBackend.baseUrl
    }

    fun clearActiveBackend() {
        userData.activeAlternativeApiBaseUrl = null
        activeBackend = null
    }

    fun setPrimaryFailedTimestamp() {
        userData.lastPrimaryApiFail = now()
    }

    suspend fun refreshDomains() {
        for (provider in dohProviders) {
            val success = withTimeoutOrNull(DOH_PROVIDER_TIMEOUT) {
                val result = provider.getAlternativeBaseUrls(primaryDomain)
                if (result != null) {
                    alternativeBaseUrls = result
                    userData.alternativeApiBaseUrls = alternativeBaseUrls
                }
                result != null
            }
            if (success == true)
                break
        }
    }

    suspend fun <T> callWithAlternatives(
        context: Context,
        callFun: suspend (ProtonVPNRetrofit) -> Response<T>
    ): ApiResult<T>? {
        // Make a copy to avoid concurrent modification
        val alternatives = alternativeBaseUrls?.shuffled()?.toTypedArray()
        alternatives?.forEach { baseUrl ->
            val backend = createAltBackend(baseUrl)
            val result = backend.call(callFun)
            if (!result.isPotentialBlocking(context)) {
                setActiveBackend(backend)
                return result
            }
        }
        return null
    }

    abstract fun createAltBackend(baseUrl: String): ApiBackendRetrofit<ProtonVPNRetrofit>
    abstract fun getDnsOverHttpsProviders(): Array<out DnsOverHttpsProvider>
}
