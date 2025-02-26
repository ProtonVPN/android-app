/*
 * Copyright (c) 2023 Proton AG
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

package de.blinkt.openvpn.core

import inet.ipaddr.IPAddressString

private val LOCAL_RANGES_IP_V4 = listOf(
    IPAddressString("10.0.0.0/8").address,
    IPAddressString("172.16.0.0/12").address,
    IPAddressString("192.168.0.0/16").address,
    IPAddressString("169.254.0.0/16").address,
    IPAddressString("224.0.0.0/4").address, // multicast v4
)

private val LOCAL_RANGES_IP_V6 = listOf(
    IPAddressString("fc00::/7").address, // unique local v6
    IPAddressString("fe80::/10").address, // link-local v6
    IPAddressString("ff00::/8").address, // multicast v6
)

fun isPrivateOnlyAddress(addressStr: String): Boolean {
    val address = IPAddressString(addressStr).address
    val localRanges = if (address.isIPv4) LOCAL_RANGES_IP_V4 else LOCAL_RANGES_IP_V6
    return localRanges.any { it.intersect(address) == address }
}