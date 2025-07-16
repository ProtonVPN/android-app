/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.appconfig.periodicupdates

import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.HttpResponseCodes
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val MIN_RETRY_AFTER = 15.minutes

class PeriodicApiCallResult<R>(
    apiResult: ApiResult<R>,
    nextCallDelayOverride: Long? = apiResult.retryAfterIfApplicable(MIN_RETRY_AFTER)?.inWholeMilliseconds
) : PeriodicActionResult<ApiResult<R>>(apiResult, apiResult is ApiResult.Success, nextCallDelayOverride)

// TODO: these two following functions are not necessary after all.
fun <T> ApiResult<T>.toPeriodicActionResult(
    nextCallDelayOverride: Long? = this.retryAfterIfApplicable(MIN_RETRY_AFTER)?.inWholeMilliseconds
) = PeriodicApiCallResult(this, nextCallDelayOverride)

fun ApiResult<*>.toPeriodicActionResultWithNoData(
    nextCallDelayOverride: Long? = this.retryAfterIfApplicable(MIN_RETRY_AFTER)?.inWholeMilliseconds
) = PeriodicActionResult(Unit, this is ApiResult.Success, nextCallDelayOverride)

private fun <T> ApiResult<T>.retryAfterIfApplicable(minimalDuration: Duration): Duration? =
    (this as? ApiResult.Error.Http)?.retryAfter?.takeIf {
        httpCode in arrayOf(HttpResponseCodes.HTTP_TOO_MANY_REQUESTS, HttpResponseCodes.HTTP_SERVICE_UNAVAILABLE)
    }?.coerceAtLeast(minimalDuration)
