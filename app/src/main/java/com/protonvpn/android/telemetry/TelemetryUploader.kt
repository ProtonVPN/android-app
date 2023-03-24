/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.telemetry

import com.protonvpn.android.api.ProtonApiRetroFit
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.isRetryable
import me.proton.core.network.domain.retryAfter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelemetryUploader @Inject constructor(
    private val api: ProtonApiRetroFit
) {

    suspend fun uploadEvents(events: List<TelemetryEvent>): Telemetry.UploadResult {
        val statsEvents = events.map { StatsEvent(it.measurementGroup, it.event, it.values, it.dimensions) }
        val result = api.postStats(statsEvents)
        return if (result is ApiResult.Success) {
            Telemetry.UploadResult.Success(false)
        } else {
            Telemetry.UploadResult.Failure(result.isRetryable(), result.retryAfter())
        }
    }
}
