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
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.vpn.ProtocolSelection

enum class Setting(val logName: String) {
    QUICK_CONNECT_PROFILE("Quick connect"),
    DEFAULT_PROTOCOL("Default protocol"),
    NETSHIELD_PROTOCOL("NetShield protocol"),
    SECURE_CORE("Secure Core"),
    LAN_CONNECTIONS("LAN connections"),
    SPLIT_TUNNEL_ENABLED("Split Tunneling enabled"),
    SPLIT_TUNNEL_APPS("Split Tunneling excluded apps"),
    SPLIT_TUNNEL_IPS("Split Tunneling excluded IPs"),
    DEFAULT_MTU("Default MTU"),
    SAFE_MODE("Safe Mode"),
    RESTRICTED_NAT("Restricted NAT"),
    VPN_ACCELERATOR_ENABLED("VPN Accelerator enabled"),
    API_DOH("Use DoH for API"),
    TELEMETRY("Telemetry")
}

fun Profile.toLog(settings: LocalUserSettings): String {
    val type = when {
        isPreBakedFastest -> "Fastest"
        isPreBakedProfile -> "Random"
        name.isNotBlank() -> "Custom" // Logs are sent to Sentry, let's not send profile names.
        else -> "None"
    }
    val protocol = getProtocol(settings)
    val serverInfo = arrayOf(wrapper.type.toString(), wrapper.country, wrapper.serverId)
        .filterNot { it.isNullOrBlank() }
        .joinToString(" ")
    return "Profile: $type, protocol: $protocol, server: $serverInfo"
}

fun AnyConnectIntent.toLog(): String {
    val description = when (this) {
        is ConnectIntent.FastestInCountry -> "Fastest in country: ${country.toLog()}"
        is ConnectIntent.FastestInCity -> "Fastest in city: ${cityEn} (${country.toLog()})"
        is ConnectIntent.FastestInState -> "Fastest in state: ${stateEn} (${country.toLog()})"
        is ConnectIntent.SecureCore -> "Secure Core: ${exitCountry.countryCode} via ${entryCountry.countryCode}"
        is ConnectIntent.Gateway ->
            "Gateway: $gatewayName, " + if (serverId == null) "fastest server" else "server $serverId"
        is ConnectIntent.Server -> "Direct server: $serverId"
        is AnyConnectIntent.GuestHole -> "Guest hole: $serverId"
    }
    return "ConnectIntent: $description; features: $features"
}

fun VpnUser.toLog() =
    "plan: $planName, maxTier: $maxTier, maxConnect: $maxConnect, status: $status, subscribed: $subscribed, " +
        "services: $services, delinquent: $delinquent"

fun ProtonLogger.logUiSettingChange(setting: Setting, where: String) {
    log(UiSetting, "Changing \"${setting.logName}\" in $where.")
}

fun ProtocolSelection.toLog() = "$vpn ${transmission ?: ""}"

fun Boolean?.toLog() = when(this) {
    true -> "enabled"
    false -> "disabled"
    null -> "unset"
}

fun List<*>.itemCountToLog() = if (isEmpty()) "None" else "$size items"

fun CountryId.toLog() = if (isFastest) "fastest" else countryCode
