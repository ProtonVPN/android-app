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
import com.protonvpn.android.redesign.recents.data.ConnectIntentData
import com.protonvpn.android.redesign.recents.data.toAnyConnectIntent
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.ProtocolSelection
import java.util.UUID

open class ConnectionParams(
    val connectIntentData: ConnectIntentData,
    val server: Server,
    val connectingDomain: ConnectingDomain?,
    private val protocol: VpnProtocol?,
    val entryIp: String? = null,
    val port: Int? = null,
    protected val transmissionProtocol: TransmissionProtocol? = null,
    val uuid: UUID = UUID.randomUUID(),
    val enableIPv6: Boolean = false
) : java.io.Serializable {

    private val profile: Profile? = null // Used for handling old serialized objects.

    open val info get() = "IP: ${connectingDomain?.entryDomain}/$entryIp Protocol: $protocol Server: ${server.serverName}"

    val connectIntent: AnyConnectIntent get() = connectIntentData.toAnyConnectIntent()
    val protocolSelection get() = protocol?.let { ProtocolSelection(it, transmissionProtocol) }

    val bouncing: String? get() = connectingDomain?.label?.takeIf(String::isNotBlank)

    constructor(
        connectIntent: AnyConnectIntent,
        server: Server,
        connectingDomain: ConnectingDomain?,
        protocol: VpnProtocol?,
        entryIp: String? = null,
        port: Int? = null,
        transmissionProtocol: TransmissionProtocol? = null,
        uuid: UUID = UUID.randomUUID(),
        ipv6SettingEnabled: Boolean = false,
    ) : this(
        connectIntent.toData(),
        server,
        connectingDomain,
        protocol,
        entryIp,
        port,
        transmissionProtocol,
        uuid,
        ipv6SettingEnabled && server.isIPv6Supported
    )

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

        fun readIntentFromStore(expectedUuid: UUID? = null): AnyConnectIntent? {
            val value = Storage.load(ConnectionParams::class.java)
                ?.takeIf { expectedUuid == null || it.uuid == expectedUuid }
                ?: return null
            if (value.profile != null) {
                // TODO: try to implement profile.toConnectIntent(). The problem is it needs ServerManager and UserData
                //  but maybe the conversion can be simplified?
                return null
            }
            return value.connectIntent
        }
    }
}
