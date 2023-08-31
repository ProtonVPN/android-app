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
import com.protonvpn.android.appconfig.RestrictionsConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.PartnersResponse
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.partnerships.PartnershipsRepository
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.ui.home.countries.CountryListViewModel
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.app.userstorage.createDummyProfilesManager
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createInMemoryServersStore
import com.protonvpn.test.shared.createServer
import com.protonvpn.test.shared.mockVpnUser
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    @MockK
    private lateinit var mockApi: ProtonApiRetroFit

    @MockK
    private lateinit var mockCurrentUser: CurrentUser

    @MockK
    private lateinit var mockServerListUpdater: ServerListUpdater

    @RelaxedMockK
    private lateinit var mockVpnStateMonitor: VpnStateMonitor

    @RelaxedMockK
    private lateinit var restrictionsConfig: RestrictionsConfig

    @MockK
    private lateinit var countryListViewModel: CountryListViewModel
    private val vpnUserFlow = MutableStateFlow(TestUser.plusUser.vpnUser)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val scope = TestScope()
        Storage.setPreferences(MockSharedPreference())
        mockCurrentUser.mockVpnUser { vpnUserFlow.value }
        every { mockCurrentUser.vpnUserFlow } returns vpnUserFlow
        val userSettings = EffectiveCurrentUserSettingsCached(MutableStateFlow(LocalUserSettings.Default))
        val supportsProtocol = SupportsProtocol(createGetSmartProtocols())
        val profileManager = createDummyProfilesManager()
        serverManager = ServerManager(
            userSettings,
            mockCurrentUser,
            { 0 },
            supportsProtocol,
            createInMemoryServersStore(),
            profileManager,
        )
        coEvery { mockApi.getPartnerships() } returns ApiResult.Success(PartnersResponse(emptyList()))

        val servers = listOf(
            createServer(serverName = "PL#1", score = 1.0, tier = 2),
            createServer(serverName = "PL#2", score = 5.0, tier = 2),
            createServer(serverName = "PL#3", score = 2.0, tier = 0),
            createServer(serverName = "PL#4", score = 4.0, tier = 0)
        )
        serverManager.setServers(servers, null)

        countryListViewModel = CountryListViewModel(
            serverManager,
            PartnershipsRepository(mockApi),
            mockServerListUpdater,
            VpnStatusProviderUI(scope, mockVpnStateMonitor),
            userSettings,
            mockCurrentUser,
            restrictionsConfig
        )
    }

    @Test
    fun `free user server list order`() {
        vpnUserFlow.value = TestUser.freeUser.vpnUser

        val country = serverManager.getVpnExitCountry("PL", false)!!
        val serverList = countryListViewModel.getMappedServersForGroup(country)
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
        vpnUserFlow.value = TestUser.plusUser.vpnUser

        val country = serverManager.getVpnExitCountry("PL", false)!!
        val serverList = countryListViewModel.getMappedServersForGroup(country)
        val groupTitles = serverList.map { it.groupTitle?.titleRes }
        assertEquals(
            listOf(R.string.listFastestServer, R.string.listPlusServers, R.string.listFreeServers),
            groupTitles
        )
        val fastestServer = serverList.first().servers.first()
        assertEquals(2, fastestServer.tier)
    }
}
