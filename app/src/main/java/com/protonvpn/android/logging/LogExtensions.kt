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
import com.protonvpn.android.models.config.Setting
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.vpn.ProtocolSelection

fun Profile.toLog(userData: UserData): String {
    val type = when {
        isPreBakedFastest -> "Fastest"
        isPreBakedProfile -> "Random"
        name.isNotBlank() -> "Custom" // Logs are sent to Sentry, let's not send profile names.
        else -> "None"
    }
    val protocol = getProtocol(userData)
    val serverInfo = arrayOf(wrapper.type.toString(), wrapper.country, wrapper.serverId)
        .filterNot { it.isNullOrBlank() }
        .joinToString(" ")
    return "Profile: $type, protocol: $protocol, server: $serverInfo"
}

fun VpnUser.toLog() =
    "plan: $planName, maxTier: $maxTier, maxConnect: $maxConnect, status: $status, subscribed: $subscribed, " +
        "services: $services, delinquent: $delinquent"

fun ProtonLogger.logUiSettingChange(setting: Setting, where: String) {
    log(UiSetting, "Changing \"${setting.logName}\" in $where.")
}

fun ProtocolSelection.toLog() = "$vpn ${transmission ?: ""}"
