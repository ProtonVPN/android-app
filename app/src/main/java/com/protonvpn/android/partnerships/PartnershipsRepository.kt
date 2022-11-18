/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.android.partnerships

import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.models.vpn.Partner
import com.protonvpn.android.models.vpn.PartnersResponse
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.utils.Storage
import javax.inject.Inject

class PartnershipsRepository @Inject constructor(
    private val api: ProtonApiRetroFit
) {

    private var partnersResponse: PartnersResponse? = Storage.load(PartnersResponse::class.java)
    private val allPartners: List<Partner>
        get() = partnersResponse?.partnerTypes?.flatMap { it.partners } ?: emptyList()

    suspend fun refresh() {
        api.getPartnerships().valueOrNull?.let {
            partnersResponse = it
            Storage.save(it)
        }
    }

    fun getPartnerTypes() = partnersResponse?.partnerTypes ?: emptyList()

    fun hasAnyPartnership(country: VpnCountry) =
        country.serverList.any { server ->
            allPartners.any { partner -> partner.hasServer(server) }
        }

    fun getServerPartnerships(serverId: String): List<Partner> =
        allPartners.filter { partner -> partner.hasServer(serverId) }

    fun getUniquePartnershipsForServers(serverList: List<Server>): List<Partner> = buildList {
        serverList.filter { it.isPartneshipServer }.forEach { server ->
            allPartners.forEach { partner ->
                if (partner.hasServer(server) && !this.contains(partner))
                    add(partner)
            }
        }
    }

    private fun Partner.hasServer(serverId: String) = logicalIDs.contains(serverId)
    private fun Partner.hasServer(server: Server) = logicalIDs.contains(server.serverId)
}
