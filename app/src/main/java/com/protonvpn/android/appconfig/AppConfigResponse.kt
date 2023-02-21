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

@Serializable
data class AppConfigResponse(
    @SerialName(value = "ServerRefreshInterval")
    val underMaintenanceDetectionDelay: Long = Constants.DEFAULT_MAINTENANCE_CHECK_MINUTES,
    @SerialName(value = "DefaultPorts") val defaultPortsConfig: DefaultPortsConfig?,
    @SerialName(value = "FeatureFlags") val featureFlags: FeatureFlags,
    @SerialName(value = "SmartProtocol") val smartProtocolConfig: SmartProtocolConfig?,
    @SerialName(value = "RatingSettings") val ratingConfig: RatingConfig?
)
