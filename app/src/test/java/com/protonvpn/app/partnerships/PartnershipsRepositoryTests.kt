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

package com.protonvpn.app.partnerships

import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.models.vpn.Partner
import com.protonvpn.android.models.vpn.PartnerType
import com.protonvpn.android.models.vpn.PartnersResponse
import com.protonvpn.android.models.vpn.SERVER_FEATURE_PARTNER_SERVER
import com.protonvpn.android.partnerships.PartnershipsRepository
import com.protonvpn.android.utils.Storage
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.createServer
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import me.proton.core.network.domain.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PartnershipsRepositoryTests {

    @MockK
    private lateinit var mockApi: ProtonApiRetroFit

    private lateinit var partnershipsRepository: PartnershipsRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(MockSharedPreference())

        partnershipsRepository = PartnershipsRepository(mockApi)
    }

    @Test
    fun `test getPartnershipForServer`() = runTest {
        val server = createServer(serverId = "server 1", features = SERVER_FEATURE_PARTNER_SERVER)
        val partner = Partner("partner name", "partner description", "icon Url", listOf(server.serverId))
        setupPartnerships(partner)

        val partnersForServer = partnershipsRepository.getServerPartnerships(server)
        assertEquals(listOf(partner), partnersForServer)
    }

    @Test
    fun `when same partner is added to multiple servers it is listed once`() = runTest {
        val servers = listOf(
            createServer(serverId = "server 1", features = SERVER_FEATURE_PARTNER_SERVER),
            createServer(serverId = "server 2", features = SERVER_FEATURE_PARTNER_SERVER)
        )
        val partner =
            Partner("partner name", "partner description", "icon Url", servers.map { it.serverId })
        setupPartnerships(partner)

        val partnersForServers = partnershipsRepository.getUniquePartnershipsForServers(servers)
        assertEquals(listOf(partner), partnersForServers)
    }

    private suspend fun setupPartnerships(vararg partners: Partner) {
        val type = PartnerType("type", "type description", "iconUrl", partners.toList())
        coEvery { mockApi.getPartnerships() } returns ApiResult.Success(PartnersResponse(listOf(type)))
        partnershipsRepository.refresh()
    }
}
