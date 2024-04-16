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
package com.protonvpn.android.models.vpn

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StreamingServicesResponse(
    @SerialName(value = "ResourceBaseURL") val resourceBaseURL: String,
    @SerialName(value = "StreamingServices") val countryToServices: Map<String, Map<String, List<StreamingService>>>
)

@Serializable
data class StreamingService(
    @SerialName(value = "Name") val name: String,
    @SerialName(value = "Icon") val iconName: String,
    @SerialName(value = "ColoredIcon") val coloredIconName: String
)
