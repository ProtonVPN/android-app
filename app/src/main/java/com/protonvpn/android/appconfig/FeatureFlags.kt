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
package com.protonvpn.android.appconfig

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import me.proton.core.network.data.protonApi.IntToBoolSerializer

@Serializable
data class FeatureFlags(
    @Serializable(with = IntToBoolSerializer::class)
    @SerialName(value = "ServerRefresh") val maintenanceTrackerEnabled: Boolean = true,
    @Serializable(with = IntToBoolSerializer::class)
    @SerialName(value = "NetShield") val netShieldEnabled: Boolean = false,
    @Serializable(with = IntToBoolSerializer::class)
    @SerialName(value = "GuestHoles") val guestHoleEnabled: Boolean = false,
    @Serializable(with = IntToBoolSerializer::class)
    @SerialName(value = "PollNotificationAPI") val pollApiNotifications: Boolean = false,
    @Serializable(with = IntToBoolSerializer::class)
    @SerialName(value = "PromoCode") val promoCodeEnabled: Boolean = false,
    @Serializable(with = IntToBoolSerializer::class)
    @SerialName(value = "StreamingServicesLogos") val streamingServicesLogos: Boolean = false,
    @Serializable(with = IntToBoolSerializer::class)
    @SerialName(value = "VpnAccelerator") val vpnAccelerator: Boolean = false,
    @Serializable(with = IntToBoolSerializer::class)
    @SerialName(value = "WireGuardTls") val wireguardTlsEnabled: Boolean = true,
    @Serializable(with = IntToBoolSerializer::class)
    @SerialName(value = "SafeMode") val safeMode: Boolean = false,
) : java.io.Serializable
