/*
 * Copyright (c) 2024. Proton AG
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

import com.protonvpn.android.profiles.data.ProfileAutoOpen
import com.protonvpn.android.profiles.ui.ProfileType
import com.protonvpn.android.profiles.usecases.PrivateBrowsingAvailability
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.vpn.ProtocolSelection

fun Boolean.toTelemetry() = if (this) "true" else "false"

fun ProtocolSelection.toTelemetry(): String {
    val vpnPrefix = vpn.name.lowercase()
    val transmissionSuffix = transmission?.name?.lowercase()?.let { "_$it" }.orEmpty()
    return "$vpnPrefix$transmissionSuffix"
}

fun NatType.toTelemetry() = when(this) {
    NatType.Strict -> "type3_strict"
    NatType.Moderate -> "type2_moderate"
}

fun ProfileAutoOpen.toTelemetry() = when(this) {
    is ProfileAutoOpen.None -> "off"
    is ProfileAutoOpen.App -> "app"
    is ProfileAutoOpen.Url -> if (openInPrivateMode) "url_private" else "url"
}

fun PrivateBrowsingAvailability.toTelemetry() = when(this) {
    PrivateBrowsingAvailability.AvailableWithDefault -> "available_with_default"
    PrivateBrowsingAvailability.AvailableWithOther -> "available_with_other"
    PrivateBrowsingAvailability.NotAvailable -> "not_available"
}

fun ProfileType.toTelemetry() = when(this) {
    ProfileType.Standard -> "standard"
    ProfileType.SecureCore -> "secure_core"
    ProfileType.P2P -> "p2p"
    ProfileType.Gateway -> "gateway"
}
