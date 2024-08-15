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
package com.protonvpn.android.models.profiles

import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.vpn.ProtocolSelection
import java.io.Serializable
import java.util.Locale
import java.util.UUID

data class Profile @JvmOverloads constructor(
    val name: String,
    private val color: String?,
    val wrapper: ServerWrapper,
    private val colorId: Int?,
    val isSecureCore: Boolean?,
    private var protocol: String? = null,
    private var transmissionProtocol: String? = null,
    val id: UUID? = UUID.randomUUID(),
    var isGuestHoleProfile: Boolean = false
) : Serializable {

    val isPreBakedProfile: Boolean
        get() = wrapper.isPreBakedProfile
    val isPreBakedFastest: Boolean
        get() = wrapper.isPreBakedFastest

    val country: String get() = wrapper.country

    fun getProtocol(settings: LocalUserSettings) = protocol?.let { protocol ->
        val vpnProtocol = VpnProtocol.entries.firstOrNull { it.name == protocol } ?: VpnProtocol.Smart
        ProtocolSelection(vpnProtocol, transmissionProtocol?.let(TransmissionProtocol::valueOf))
    } ?: settings.protocol

    fun setProtocol(protocol: ProtocolSelection) {
        this.protocol = protocol.vpn.toString()
        this.transmissionProtocol = protocol.transmission?.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Profile

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    companion object {
        @JvmStatic
        fun getTempProfile(server: Server) = getTempProfile(server, null)
        fun getTempProfile(server: Server, isSecureCore: Boolean?) =
            getTempProfile(ServerWrapper.makeWithServer(server), isSecureCore)
        fun getTempProfile(serverWrapper: ServerWrapper, isSecureCore: Boolean? = null) =
            Profile("", null, serverWrapper, null, isSecureCore)
    }
}
