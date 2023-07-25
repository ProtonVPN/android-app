/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.android.appconfig

import com.protonvpn.android.utils.Constants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

val DEFAULT_SERVER_LIST_REFRESH_FOREGROUND_MINUTES = TimeUnit.HOURS.toMinutes(3)
val DEFAULT_SERVER_LIST_REFRESH_BACKGROUND_MINUTES = TimeUnit.DAYS.toMinutes(2)

@Serializable
data class AppConfigResponse(
    @SerialName(value = "ServerRefreshInterval")
    val underMaintenanceDetectionDelay: Long = Constants.DEFAULT_MAINTENANCE_CHECK_MINUTES,
    @SerialName(value = "LogicalsRefreshIntervalForegroundMinutes")
    val logicalsRefreshForegroundDelayMinutesInternal: Long? = DEFAULT_SERVER_LIST_REFRESH_FOREGROUND_MINUTES,
    @SerialName(value = "LogicalsRefreshIntervalBackgroundMinutes")
    val logicalsRefreshBackgroundDelayMinutesInternal: Long? = DEFAULT_SERVER_LIST_REFRESH_BACKGROUND_MINUTES,
    @SerialName(value = "DefaultPorts") val defaultPortsConfig: DefaultPortsConfig?,
    @SerialName(value = "FeatureFlags") val featureFlags: FeatureFlags,
    @SerialName(value = "SmartProtocol") val smartProtocolConfig: SmartProtocolConfig?,
    @SerialName(value = "RatingSettings") val ratingConfig: RatingConfig?
) {
    // Workaround for GSON problem with deserializing fields with default values
    val logicalsRefreshForegroundDelayMinutes get() =
        logicalsRefreshForegroundDelayMinutesInternal ?: DEFAULT_SERVER_LIST_REFRESH_FOREGROUND_MINUTES
    val logicalsRefreshBackgroundDelayMinutes get() =
        logicalsRefreshBackgroundDelayMinutesInternal ?: DEFAULT_SERVER_LIST_REFRESH_BACKGROUND_MINUTES
}
