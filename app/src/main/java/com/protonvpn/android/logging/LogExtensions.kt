/*
 * Copyright (c) 2021. Proton AG
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

package com.protonvpn.android.logging

import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper

fun Profile.toLog(userData: UserData): String {
    val type = when {
        isPreBakedFastest -> "Fastest"
        isPreBakedProfile -> "Random"
        name.isNotBlank() -> "\"$name\""
        else -> "None"
    }
    val protocol = getProtocol(userData)
    val serverInfo = if (wrapper.type == ServerWrapper.ProfileType.DIRECT) {
        server?.serverName ?: "server is not available"
    } else {
        if (wrapper.country.isNotBlank()) "${wrapper.type} ${wrapper.country}"
        else wrapper.type
    }
    return "Profile: $type, protocol: $protocol, server: $serverInfo"
}

fun VpnUser.toLog() =
    "plan: $planName, maxTier: $maxTier, maxConnect: $maxConnect, status: $status, subscribed: $subscribed, " +
        "services: $services, delinquent: $delinquent"
