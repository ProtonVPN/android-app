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
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Response
import java.util.Random
import java.util.concurrent.TimeoutException
import kotlin.math.pow

class ProtonApiManager(
    private val appContext: Context,
    private val userData: UserData,
    private val altApiManager: AlternativeApiManager,
    private val primaryBackend: ProtonApiBackend,
    private val random: Random
) {

    val primaryOkClient get() = primaryBackend.okClient

    private var vpnStateMonitor: VpnStateMonitor? = null

    //TODO: fix dependency cycle more elegantly e.g. extract VpnState from VpnStateMonitor
    fun initVpnState(monitor: VpnStateMonitor) {
        vpnStateMonitor = monitor
    }

    suspend fun <T> call(
        useBackoff: Boolean = false,
        callFun: suspend (ProtonVPNRetrofit) -> Response<T>
    ): ApiResult<T> {
        return when {
            userData.apiUseDoH && !isConnectedToVpn() -> {
                callWithTimeout(DOH_TIMEOUT) {
                    callWithDoH(callFun)
                }
            }
            useBackoff ->
                callWithBackoff(primaryBackend, callFun)
            else ->
                primaryBackend.call(callFun)
        }
    }

    private fun isConnectedToVpn() = vpnStateMonitor?.isConnected == true

    private suspend fun <T> callWithDoH(callFun: suspend (ProtonVPNRetrofit) -> Response<T>): ApiResult<T> {
        val activeBackend = altApiManager.getActiveBackend() ?: primaryBackend
        val result = activeBackend.call(callFun)
        return if (!result.isPotentialBlocking(appContext))
            result
        else coroutineScope {
            // Ping primary backend (to make sure failure wasn't a random network error rather than
            // an actual block) parallel with refreshing proxy list
            val ping = async {
                primaryBackend.ping()
            }
            val dohRefresh = async {
                withTimeoutOrNull(DOH_DOMAIN_REFRESH_TIMEOUT) {
                    altApiManager.refreshDomains()
                }
            }
            // If ping succeeded don't fallback to proxy
            val pingResult = ping.await()
            if (!pingResult.isPotentialBlocking(appContext)) {
                dohRefresh.cancel()
                altApiManager.clearActiveBackend()
                result
            } else {
                // Active api appears blocked, try proxies from DoH
                dohRefresh.await()

                if (activeBackend == primaryBackend)
                    altApiManager.setPrimaryFailedTimestamp()
                else
                    altApiManager.clearActiveBackend()

                altApiManager.callWithAlternatives(appContext, callFun) ?: result
            }
        }
    }

    private suspend fun <T> callWithBackoff(
        backend: ProtonApiBackend,
        callFun: suspend (ProtonVPNRetrofit) -> Response<T>
    ): ApiResult<T> {
        var retryCount = -1
        while (true) {
            val result = backend.call(callFun)
            retryCount++
            if (retryCount == BACKOFF_RETRY_COUNT ||
                    result is ApiResult.Success ||
                    result is ApiResult.ErrorBodyResponse) {
                return result
            }
            val delayCoefficient = sample(2.0.pow(retryCount.toDouble()), 2.0.pow(retryCount + 1.0))
            delay(BACKOFF_RETRY_DELAY_MS * delayCoefficient.toLong())
        }
    }

    private fun sample(min: Double, max: Double) =
            min + random.nextDouble() * (max - min)

    private suspend fun <T> callWithTimeout(timeoutMs: Long, block: suspend CoroutineScope.() -> ApiResult<T>) =
            withTimeoutOrNull(timeoutMs, block) ?: ApiResult.Failure(TimeoutException("API call timed out"))

    companion object {
        const val DOH_TIMEOUT = 60_000L
        const val BACKOFF_RETRY_COUNT = 2
        const val BACKOFF_RETRY_DELAY_MS = 500
        private const val DOH_DOMAIN_REFRESH_TIMEOUT = 30_000L
    }
}
