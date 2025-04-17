/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.settings.data

import android.os.Parcelable
import androidx.room.ColumnInfo
import com.protonvpn.android.logging.itemCountToLog
import com.protonvpn.android.logging.toLog
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.userstorage.UUIDSerializer
import com.protonvpn.android.vpn.ProtocolSelection
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

enum class SplitTunnelingMode {
    INCLUDE_ONLY,
    EXCLUDE_ONLY
}

@Parcelize
@Serializable
data class SplitTunnelingSettings(
    val isEnabled: Boolean = false,
    val mode: SplitTunnelingMode = SplitTunnelingMode.INCLUDE_ONLY,
    val excludedIps: List<String> = emptyList(),
    val excludedApps: List<String> = emptyList(),
    val includedIps: List<String> = emptyList(),
    val includedApps: List<String> = emptyList(),
): Parcelable {
    fun isEffectivelySameAs(other: SplitTunnelingSettings): Boolean {
        return !isEnabled && !other.isEnabled
            || mode == other.mode && effectiveApps() == other.effectiveApps() && effectiveIps() == other.effectiveIps()
            // Both INCLUDE_ONLY and EXCLUDE_ONLY with empty values behave the same as split tunneling disabled:
            || isEmpty() && other.isEmpty()
    }

    fun currentModeApps() = when (mode) {
        SplitTunnelingMode.INCLUDE_ONLY -> includedApps
        SplitTunnelingMode.EXCLUDE_ONLY -> excludedApps
    }

    fun currentModeIps() = when (mode) {
        SplitTunnelingMode.INCLUDE_ONLY -> includedIps
        SplitTunnelingMode.EXCLUDE_ONLY -> excludedIps
    }

    fun isEmpty() = currentModeApps().isEmpty() && currentModeIps().isEmpty()

    private fun effectiveApps() = if (isEnabled) currentModeApps() else emptyList()
    private fun effectiveIps() = if (isEnabled) currentModeIps() else emptyList()
}

@Parcelize
@Serializable
data class CustomDnsSettings(
    @ColumnInfo(name = "customDnsEnabled")
    @SerialName("enabled") // For backwards compatibility.
    val toggleEnabled: Boolean = false,
    val rawDnsList: List<String> = emptyList()
): java.io.Serializable, Parcelable {
    val effectiveDnsList: List<String> get() = if (toggleEnabled && rawDnsList.isNotEmpty()) rawDnsList else emptyList()
    val effectiveEnabled get() = effectiveDnsList.isNotEmpty()
}

@Serializable
data class LocalUserSettings(
    // Version of the LocalUserSettings structure. Only increase when needed for migration.
    // Set it to 3 next time a migration is needed.
    // The current version is in fact 2 but because it's a newly added field it starts with value of 1 to always force a
    // migration of split tunneling settings.
    val version: Int = 1,
    val apiUseDoh: Boolean = true,
    @Serializable(with = UUIDSerializer::class)
    val defaultProfileId: UUID? = null,
    val lanConnections: Boolean = false,
    val mtuSize: Int = 1375,
    val netShield: NetShieldProtocol = NetShieldProtocol.ENABLED_EXTENDED,
    val protocol: ProtocolSelection = ProtocolSelection.SMART,
    val randomizedNat: Boolean = true,
    @Deprecated("used only for migration") val secureCore: Boolean = false, // Value needed only for migrated profiles.
    val splitTunneling: SplitTunnelingSettings = SplitTunnelingSettings(),
    val telemetry: Boolean = true,
    val vpnAccelerator: Boolean = true,
    val ipV6Enabled: Boolean = true,
    val customDns: CustomDnsSettings = CustomDnsSettings(false)
    // Whenever adding a new setting add it also in toLogList below.
) {
    companion object {
        val Default = LocalUserSettings()
    }
}

// Provide log strings for all settings.
fun LocalUserSettings.toLogList(): List<String> {
    val regularSettings = listOf(
        "LAN connections: ${lanConnections.toLog()}",
        "MTU size: $mtuSize bytes",
        "NetShield: $netShield",
        "Protocol: ${protocol.apiName}",
        "Restricted NAT: ${randomizedNat.toLog()}",
        with(splitTunneling) {
            "Split tunneling: ${isEnabled.toLog()}, mode: ${mode.toLog()}," +
                " excluded apps: ${excludedApps.itemCountToLog()}, excluded IPs: ${excludedIps.itemCountToLog()}" +
                " included apps: ${includedApps.itemCountToLog()}, included IPs: ${includedIps.itemCountToLog()}"
        },
        "Telemetry: ${telemetry.toLog()}",
        "Use DoH for API: ${apiUseDoh.toLog()}",
        "VPN Accelerator: ${vpnAccelerator.toLog()}",
        "IPv6 enabled: ${ipV6Enabled.toLog()}",
        "Custom DNS enabled: ${customDns.toggleEnabled.toLog()}",
        "Custom DNS list: ${customDns.rawDnsList.itemCountToLog()}",
    )
    return regularSettings
}
