/*
 * Copyright (c) 2019 Proton Technologies AG
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
class DefaultPorts(
    @SerialName(value = "UDP") private val udpPorts: List<Int>,
    @SerialName(value = "TCP") private val tcpPorts: List<Int>
) {
    fun getUdpPorts(): List<Int> =
        if (udpPorts.isEmpty()) DEFAULT_PORT_LIST else udpPorts

    fun getTcpPorts(): List<Int> =
        if (tcpPorts.isEmpty()) DEFAULT_PORT_LIST else tcpPorts

    companion object {
        private val DEFAULT_PORT_LIST = listOf(443)
        val defaults: DefaultPorts
            get() = DefaultPorts(DEFAULT_PORT_LIST, DEFAULT_PORT_LIST)
    }
}
