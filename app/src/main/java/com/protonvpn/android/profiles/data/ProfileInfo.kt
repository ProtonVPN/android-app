/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.android.profiles.data

import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.redesign.recents.data.ProtocolSelectionData
import com.protonvpn.android.redesign.recents.data.SettingsOverrides
import com.protonvpn.android.redesign.recents.data.toConnectIntent
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.vpn.ProtocolSelection

data class ProfileInfo(
    val id: Long,
    val name: String,
    val color: ProfileColor,
    val icon: ProfileIcon,
    val isGateway: Boolean,
)

data class Profile(
    val info: ProfileInfo,
    val connectIntent: ConnectIntent,
)

fun ProfileEntity.toProfile() = Profile(
    ProfileInfo(
        id = requireNotNull(connectIntentData.profileId),
        name = name,
        color = color,
        icon = icon,
        isGateway = connectIntentData.gatewayName != null,
    ),
    connectIntentData.toConnectIntent(),
)

fun profileSettingsOverrides(
    protocolData: ProtocolSelectionData = ProtocolSelection.SMART.toData(),
    netShield: NetShieldProtocol = NetShieldProtocol.ENABLED_EXTENDED,
    randomizedNat: Boolean = true,
    lanConnections: Boolean = true,
) = SettingsOverrides(
    protocolData = protocolData,
    netShield = netShield,
    randomizedNat = randomizedNat,
    lanConnections = lanConnections,
)