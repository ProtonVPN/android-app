/*
 * Copyright (c) 2022. Proton AG
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

import com.protonvpn.android.BuildConfig
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.UserData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Telemetry @Inject constructor(
    private val mainScope: CoroutineScope,
    private val apiRetroFit: ProtonApiRetroFit,
    private val appConfig: AppConfig,
    private val userData: UserData
) {
    private val isEnabled: Boolean get() = appConfig.getFeatureFlags().telemetry && userData.telemetryEnabled

    fun event(
        measurementGroup: String,
        event: String,
        values: Map<String, Long>,
        dimensions: Map<String, String>
    ) {
        if (isEnabled) {
            log("$measurementGroup $event: $values $dimensions")
            mainScope.launch {
                apiRetroFit.postStats(measurementGroup, event, values, dimensions)
            }
        }
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) {
            ProtonLogger.logCustom(LogLevel.DEBUG, LogCategory.TELEMETRY, message)
        }
    }
}
