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

import kotlinx.coroutines.delay
import retrofit2.Response
import java.util.*
import kotlin.math.pow

class ProtonApiManager(
    private val primaryBackend: ProtonApiBackend,
    private val random: Random
) {

    val primaryOkClient get() = primaryBackend.okClient

    suspend fun <T> call(
        useBackoff: Boolean = false,
        callFun: suspend (ProtonVPNRetrofit) -> Response<T>
    ): ApiResult<T> {
        return if (useBackoff)
            callWithBackoff(primaryBackend, callFun)
        else
            primaryBackend.call(callFun)
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

    companion object {
        const val BACKOFF_RETRY_COUNT = 2
        const val BACKOFF_RETRY_DELAY_MS = 500
    }
}
