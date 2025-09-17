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

import android.net.Uri
import android.os.Parcelable
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.redesign.recents.data.ProtocolSelectionData
import com.protonvpn.android.redesign.recents.data.SettingsOverrides
import com.protonvpn.android.redesign.recents.data.toConnectIntent
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.settings.data.CustomDnsSettings
import com.protonvpn.android.vpn.ProtocolSelection
import kotlinx.parcelize.Parcelize
import me.proton.core.domain.entity.UserId

data class ProfileInfo(
    val id: Long,
    val name: String,
    val color: ProfileColor,
    val icon: ProfileIcon,
    val createdAt: Long,
    val isUserCreated: Boolean,
    val lastConnectedAt: Long? = null,
)

sealed class ProfileAutoOpen : Parcelable {
    @Parcelize data object None : ProfileAutoOpen()
    @Parcelize data class App(val packageName: String) : ProfileAutoOpen()
    @Parcelize data class Url(val url: Uri, val openInPrivateMode: Boolean) : ProfileAutoOpen()

    companion object {
        fun from(text: String, enabled: Boolean, openInPrivateMode: Boolean): ProfileAutoOpen = when {
            !enabled -> None
            text.startsWith("app:") -> App(text.removePrefix("app:"))
            else -> Url(Uri.parse(text), openInPrivateMode)
        }
    }
}

data class Profile(
    val info: ProfileInfo,
    val autoOpen: ProfileAutoOpen,
    val connectIntent: ConnectIntent,
    val userId: UserId,
)

fun Profile.toProfileEntity() = ProfileEntity(
    name = info.name,
    color = info.color,
    connectIntentData = connectIntent.toData(),
    createdAt = info.createdAt,
    lastConnectedAt = info.lastConnectedAt,
    icon = info.icon,
    isUserCreated = info.isUserCreated,
    userId = userId,
    autoOpenUrlPrivately = autoOpen is ProfileAutoOpen.Url && autoOpen.openInPrivateMode,
    autoOpenEnabled = autoOpen !is ProfileAutoOpen.None,
    autoOpenText = when (autoOpen) {
        is ProfileAutoOpen.None -> ""
        is ProfileAutoOpen.App -> "app:${autoOpen.packageName}"
        is ProfileAutoOpen.Url -> autoOpen.url.toString()
    },
)

fun ProfileEntity.toProfile() = Profile(
    ProfileInfo(
        id = requireNotNull(connectIntentData.profileId),
        name = name,
        color = color,
        icon = icon,
        createdAt = createdAt,
        isUserCreated = isUserCreated,
        lastConnectedAt = lastConnectedAt,
    ),
    autoOpen = ProfileAutoOpen.from(autoOpenText, autoOpenEnabled, autoOpenUrlPrivately),
    connectIntent = connectIntentData.toConnectIntent(),
    userId = userId,
)

fun profileSettingsOverrides(
    protocolData: ProtocolSelectionData = ProtocolSelection.SMART.toData(),
    netShield: NetShieldProtocol = NetShieldProtocol.ENABLED_EXTENDED,
    randomizedNat: Boolean = true,
    lanConnections: Boolean = true,
    lanConnectionsAllowDirect: Boolean = false,
    customDnsSettings: CustomDnsSettings = CustomDnsSettings(false),
) = SettingsOverrides(
    protocolData = protocolData,
    netShield = netShield,
    randomizedNat = randomizedNat,
    customDns = customDnsSettings,
    lanConnections = lanConnections,
    lanConnectionsAllowDirect = lanConnectionsAllowDirect,
)
