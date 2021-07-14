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

package com.protonvpn.android.ui.drawer

import androidx.lifecycle.Transformations.map
import androidx.lifecycle.ViewModel
import com.protonvpn.android.models.config.UserData
import javax.inject.Inject

class SettingsExcludeIpsViewModel @Inject constructor(
    private val userData: UserData
): ViewModel() {

    val ipAddresses = map(userData.splitTunnelIpAddressesLiveData) { ips ->
        ips.map { ip -> LabeledItem(ip, ip) }
    }

    fun addAddress(newAddress: String): Boolean = userData.addIpToSplitTunnel(newAddress)
    fun removeAddress(item: LabeledItem) = userData.removeIpFromSplitTunnel(item.id)
}
