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

import com.protonvpn.android.logging.toLog
import com.protonvpn.android.vpn.ProtocolSelection
import kotlinx.serialization.Serializable

@Serializable
data class LocalUserSettings(
    val protocol: ProtocolSelection = ProtocolSelection.SMART,
    val safeMode: Boolean? = false
    // Whenever adding a new setting add it also in toLogList below.
) {
    companion object {
        val Default = LocalUserSettings()
    }
}

// Provide log strings for all settings.
fun LocalUserSettings.toLogList(): List<String> =
    listOf(
        "Protocol: ${protocol.apiName}",
        "Safe mode: ${safeMode.toLog()}"
    )
