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

package com.protonvpn.app.ui.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.R
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.Location
import com.protonvpn.android.models.vpn.PartnersResponse
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.partnerships.PartnershipsRepository
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.ui.home.countries.CountryListViewModel
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestUser
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import me.proton.core.network.domain.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CountryListViewModelTests {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private lateinit var serverManager: ServerManager
    private lateinit var userData: UserData

    @MockK
    private lateinit var mockApi: ProtonApiRetroFit
    @MockK
    private lateinit var mockCurrentUser: CurrentUser
    @MockK
    private lateinit var mockServerListUpdater: ServerListUpdater
    @RelaxedMockK
    private lateinit var mockVpnStateMonitor: VpnStateMonitor
    @MockK
    private lateinit var mockAppConfig: AppConfig

    private lateinit var countryListViewModel: CountryListViewModel

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val scope = TestScope()
        Storage.setPreferences(MockSharedPreference())
        userData = UserData.create(null)
        val appFeaturesPrefs = AppFeaturesPrefs(MockSharedPreferencesProvider())
        every { mockAppConfig.getSmartProtocols() } returns ProtocolSelection.REAL_PROTOCOLS
        val supportsProtocol = SupportsProtocol(mockAppConfig)
        serverManager = ServerManager(userData, mockCurrentUser, { 0 }, supportsProtocol, appFeaturesPrefs)
        coEvery { mockApi.getPartnerships() } returns ApiResult.Success(PartnersResponse(emptyList()))

        val servers = listOf(
            createServer("PL#1", score = 1f, tier = 2),
            createServer("PL#2", score = 5f, tier = 2),
            createServer("PL#3", score = 2f, tier = 0),
            createServer("PL#4", score = 4f, tier = 0)
        )
        serverManager.setServers(servers, null)

        countryListViewModel = CountryListViewModel(
            serverManager,
            PartnershipsRepository(mockApi),
            mockServerListUpdater,
            VpnStatusProviderUI(scope, mockVpnStateMonitor),
            userData,
            mockCurrentUser
        )
    }

    @Test
    fun `free user server list order`() {
        every { mockCurrentUser.vpnUserCached() } returns TestUser.freeUser.vpnUser

        val country = serverManager.getVpnExitCountry("PL", false)!!
        val serverList = countryListViewModel.getMappedServersForCountry(country)
        val groupTitles = serverList.map { it.groupTitle?.titleRes }
        assertEquals(
            listOf(R.string.listFastestServer, R.string.listFreeServers, R.string.listPlusServers),
            groupTitles
        )
        val fastestServer = serverList.first().servers.first()
        assertEquals(0, fastestServer.tier)
    }

    @Test
    fun `plus user server list order`() {
        every { mockCurrentUser.vpnUserCached() } returns TestUser.plusUser.vpnUser

        val country = serverManager.getVpnExitCountry("PL", false)!!
        val serverList = countryListViewModel.getMappedServersForCountry(country)
        val groupTitles = serverList.map { it.groupTitle?.titleRes }
        assertEquals(
            listOf(R.string.listFastestServer, R.string.listPlusServers, R.string.listFreeServers),
            groupTitles
        )
        val fastestServer = serverList.first().servers.first()
        assertEquals(2, fastestServer.tier)
    }

    private fun createServer(serverName: String, score: Float, tier: Int) = Server(
        serverId = "",
        entryCountry = "PL",
        exitCountry = "PL",
        serverName = serverName,
        connectingDomains = listOf(dummyConnectingDomain),
        hostCountry = null,
        domain = "dummy.protonvpn.net",
        load = 50f,
        tier = tier,
        region = null,
        city = null,
        features = 0,
        location = Location("", ""),
        null,
        score = score,
        isOnline = true
    )

    companion object {
        private val dummyConnectingDomain =
            ConnectingDomain("1.2.34", null, "dummy.protonvpn.net", "1.2.3.5", null, null, true, "dummy")
    }
}
