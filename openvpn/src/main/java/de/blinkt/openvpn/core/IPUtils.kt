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

private val LOCAL_RANGES = listOf(
    IPAddressString("10.0.0.0/8").address,
    IPAddressString("172.16.0.0/12").address,
    IPAddressString("192.168.0.0/16").address,
    IPAddressString("169.254.0.0/16").address,
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

// NOTE: any IPv6 address will return true
fun List<String>.haveAddressesOusideOfIPv4Private(): Boolean {
    val ranges = map { (IPAddressString(it).address).toSequentialRange() }
    val public = LOCAL_RANGES.fold(ranges) { result, local -> removeIpFromRanges(result, local) }
    return public.isNotEmpty()
}