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
package com.protonvpn.android.models.vpn

import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.ProtocolSelection
import java.util.UUID

open class ConnectionParams(
    val profile: Profile,
    val server: Server,
    val connectingDomain: ConnectingDomain?,
    private val protocol: VpnProtocol?,
    val entryIp: String? = null,
    val port: Int? = null,
    protected val transmissionProtocol: TransmissionProtocol? = null,
    val uuid: UUID = UUID.randomUUID()
) : java.io.Serializable {

    open val info get() = "IP: ${connectingDomain?.entryDomain}/$entryIp Protocol: $protocol"

    val protocolSelection get() = protocol?.let { ProtocolSelection(it, transmissionProtocol) }

    val bouncing: String? get() = connectingDomain?.label?.takeIf(String::isNotBlank)

    override fun toString() = info

    fun hasSameProtocolParams(other: ConnectionParams) =
        this.javaClass == other.javaClass &&
            other.protocol == protocol &&
            other.transmissionProtocol == transmissionProtocol &&
            other.port == port

    companion object {

        fun store(params: ConnectionParams?) {
            ProtonLogger.logCustom(LogCategory.CONN, "storing connection params (${params?.connectingDomain?.entryDomain})")
            Storage.save(params, ConnectionParams::class.java)
        }

        fun deleteFromStore(msg: String) {
            ProtonLogger.logCustom(LogCategory.CONN, "removing connection params ($msg)")
            Storage.delete(ConnectionParams::class.java)
        }

        fun readFromStore(ignoreUnsupported: Boolean = true): ConnectionParams? {
            val value = Storage.load(ConnectionParams::class.java) ?: return null
            // Ignore stored connection params for unsupported protocol
            if (ignoreUnsupported && value.profile.isUnsupportedIKEv2()) {
                deleteFromStore("unsupported protocol")
                return null
            }
            return value
        }
    }
}
