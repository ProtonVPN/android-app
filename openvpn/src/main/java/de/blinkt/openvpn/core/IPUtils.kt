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

import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressSeqRange
import inet.ipaddr.IPAddressString
import inet.ipaddr.ipv4.IPv4Address
import inet.ipaddr.ipv4.IPv4AddressSeqRange
import inet.ipaddr.ipv6.IPv6Address
import inet.ipaddr.ipv6.IPv6AddressSeqRange

private val LOCAL_RANGES = listOf(
    IPAddressString("10.0.0.0/8").address,
    IPAddressString("172.16.0.0/12").address,
    IPAddressString("192.168.0.0/16").address,
    IPAddressString("169.254.0.0/16").address,
    IPAddressString("224.0.0.0/4").address, // multicast v4
    IPAddressString("fc00::/7").address, // unique local v6
    IPAddressString("fe80::/10").address, // link-local v6
    IPAddressString("ff00::/8").address, // multicast v6
)

val FULL_RANGE_IP_V4 = IPv4AddressSeqRange(
    IPv4Address(ByteArray(4)),
    IPv4Address(ByteArray(4) { 0xff.toByte() }),
)

val FULL_RANGE_IP_V6 = IPv6AddressSeqRange(
    IPv6Address(ByteArray(16)),
    IPv6Address(ByteArray(16) { 0xff.toByte() }),
)

fun removeIpFromRanges(
    currentRanges: List<IPAddressSeqRange>,
    ip: IPAddress
): List<IPAddressSeqRange> {
    val toRemove = ip.toPrefixBlock().toSequentialRange()
    return currentRanges.flatMap { range ->
        if (range.overlaps(toRemove))
            range.subtract(toRemove).toList()
        else
            listOf(range)
    }
}

fun removeIPsFromRange(
    range: IPAddressSeqRange,
    ips: List<IPAddress>
): List<IPAddressSeqRange> =
    ips.fold(listOf(range)) { result, address ->
        removeIpFromRanges(result, address)
    }

fun List<IPAddress>.includesAddress(address: IPAddress): Boolean =
    fold(listOf(address.toSequentialRange())) { result, range ->
        removeIpFromRanges(result, range)
    }.isEmpty()

fun isPrivateOnlyAddress(address: String): Boolean =
    LOCAL_RANGES.includesAddress(IPAddressString(address).address)