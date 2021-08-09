/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.ui.settings

import androidx.lifecycle.Transformations.map
import androidx.lifecycle.ViewModel
import com.protonvpn.android.models.config.UserData
import javax.inject.Inject

private const val BITS_IN_BYTE = 8

@OptIn(ExperimentalUnsignedTypes::class)
class SettingsExcludeIpsViewModel @Inject constructor(
    private val userData: UserData
): ViewModel() {

    val ipAddresses = map(userData.splitTunnelIpAddressesLiveData) { ips ->
        ips.map { ipv4ToNumber(it) }
            .sorted()
            .map {
                val ip = it.toIpv4()
                LabeledItem(ip, ip)
            }
    }

    fun addAddress(newAddress: String): Boolean = userData.addIpToSplitTunnel(newAddress)
    fun removeAddress(item: LabeledItem) = userData.removeIpFromSplitTunnel(item.id)

    private fun ipv4ToNumber(s: String): UInt =
        s.split('.').fold(0u) { acc, str ->
            (acc shl BITS_IN_BYTE) + str.toUInt()
        }

    private fun UInt.toIpv4(): String =
        arrayOf(3, 2, 1, 0).map { index ->
            val shift = index * BITS_IN_BYTE
            this shr shift and 0xffu
        }.joinToString(".")
}
