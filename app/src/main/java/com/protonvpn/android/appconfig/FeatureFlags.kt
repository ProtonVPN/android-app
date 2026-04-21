/*
 * Copyright (c) 2020 Proton AG
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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeatureFlags(
    @SerialName(value = "ServerRefresh") val maintenanceTrackerEnabled: Boolean = true,
    @SerialName(value = "PollNotificationAPI") val pollApiNotifications: Boolean = false,
    @SerialName(value = "WireGuardTls") val wireguardTlsEnabled: Boolean = true,
    // Deprecated:
//    @SerialName(value = "GuestHoles") val guestHoleEnabled: Boolean = true,
//    @Serializable(with = VpnIntToBoolSerializer::class)
//    @SerialName(value = "NetShield") val netShieldEnabled: Boolean = false,
//    @Serializable(with = VpnIntToBoolSerializer::class)
//    @SerialName(value = "NetShieldStats") val netShieldV2: Boolean = false,
//    @Serializable(with = VpnIntToBoolSerializer::class)
//    @SerialName(value = "PromoCode") val promoCodeEnabled: Boolean = false,
//    @Serializable(with = VpnIntToBoolSerializer::class)
//    @SerialName(value = "VpnAccelerator") val vpnAccelerator: Boolean = false,
//    @Serializable(with = VpnIntToBoolSerializer::class)
//    @SerialName(value = "SafeMode") val safeMode: Boolean = false,
//    @Serializable(with = VpnIntToBoolSerializer::class)
//    @SerialName(value = "ShowNewFreePlan") val showNewFreePlan: Boolean = false,
//    @SerialName(value = "StreamingServicesLogos") val streamingServicesLogos: Boolean = false,
//    @Serializable(with = VpnIntToBoolSerializer::class)
//    @SerialName(value = "Telemetry") val telemetry: Boolean = true,
) : java.io.Serializable

@Serializable
data class FeatureFlagsLegacyStorage(
    val maintenanceTrackerEnabled: Boolean = true,
    val guestHoleEnabled: Boolean = false,
    val pollApiNotifications: Boolean = true,
    val streamingServicesLogos: Boolean = false,
    val wireguardTlsEnabled: Boolean = true,
)

fun FeatureFlagsLegacyStorage.migrate() = FeatureFlags(
    maintenanceTrackerEnabled = maintenanceTrackerEnabled,
    pollApiNotifications = pollApiNotifications,
    wireguardTlsEnabled = wireguardTlsEnabled,
)