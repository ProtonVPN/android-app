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
package com.protonvpn.android.ui.home

import androidx.lifecycle.ViewModel
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.partnerships.PartnershipsRepository
import com.protonvpn.android.servers.GetStreamingServices
import com.protonvpn.android.servers.ServerManager2
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class InformationViewModel @Inject constructor(
    private val currentUser: CurrentUser,
    private val serverManager: ServerManager2,
    private val streamingServices: GetStreamingServices,
    private val partnershipsRepository: PartnershipsRepository
) : ViewModel() {

    suspend fun isPlusUser() = currentUser.vpnUser()?.isUserPlusOrAbove == true

    fun getPartnerTypes() = partnershipsRepository.getPartnerTypes()

    suspend fun getPartnersForServer(serverId: String) = serverManager.getServerById(serverId)?.let { server ->
        partnershipsRepository.getServerPartnerships(server)
    }

    fun getStreamingServices(countryCode: String) = streamingServices(countryCode)

    suspend fun getPartnersForCountry(countryCode: String, secureCore: Boolean) =
        serverManager.getVpnExitCountry(countryCode, secureCore)?.let {
            partnershipsRepository.getUniquePartnershipsForServers(it.serverList)
        }
}
