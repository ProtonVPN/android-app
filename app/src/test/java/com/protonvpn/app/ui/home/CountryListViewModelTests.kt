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

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.R
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.ApiNotification
import com.protonvpn.android.appconfig.ChangeServerConfig
import com.protonvpn.android.appconfig.Restrictions
import com.protonvpn.android.appconfig.RestrictionsConfig
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.PartnersResponse
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.partnerships.PartnershipsRepository
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.ui.home.countries.CollapsibleServerGroupModel
import com.protonvpn.android.ui.home.countries.CountryListViewModel
import com.protonvpn.android.ui.home.countries.FastestConnectionModel
import com.protonvpn.android.ui.home.countries.FreeUpsellBannerModel
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.app.userstorage.createDummyProfilesManager
import com.protonvpn.test.shared.InMemoryDataStoreFactory
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.proton.core.network.domain.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CountryListViewModelTests {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private lateinit var scope: TestScope
    private lateinit var serverManager: ServerManager
    private lateinit var vpnUserFlow:  MutableStateFlow<VpnUser>
    private lateinit var restrictionsFlow: MutableStateFlow<Restrictions>
    private lateinit var countryListViewModel: CountryListViewModel

    @MockK
    private lateinit var mockApi: ProtonApiRetroFit

    @MockK
    private lateinit var mockCurrentUser: CurrentUser

    @MockK
    private lateinit var mockServerListUpdater: ServerListUpdater

    @RelaxedMockK
    private lateinit var mockVpnStateMonitor: VpnStateMonitor

    @RelaxedMockK
    private lateinit var context: Context
    private lateinit var userSettingsManager: CurrentUserLocalSettingsManager
    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        scope = TestScope(UnconfinedTestDispatcher())
        Storage.setPreferences(MockSharedPreference())
        ProtonApplication.setAppContextForTest(context)

        mockCurrentUser.mockVpnUser { vpnUserFlow.value }
        vpnUserFlow = MutableStateFlow(TestUser.plusUser.vpnUser)
        every { mockCurrentUser.vpnUserFlow } returns vpnUserFlow

        restrictionsFlow = MutableStateFlow(Restrictions(false, ChangeServerConfig(1, 2, 10)))

        val effectiveSettingsFlow = MutableStateFlow(LocalUserSettings.Default)

        val supportsProtocol = SupportsProtocol(createGetSmartProtocols())
        val profileManager = createDummyProfilesManager()
        serverManager = ServerManager(
            scope.backgroundScope,
            EffectiveCurrentUserSettingsCached(effectiveSettingsFlow),
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
            createServer(serverName = "PL#4", score = 4.0, tier = 0),
            createServer(serverName = "DE#1", exitCountry = "DE", score = 4.0, tier = 2),
            createServer(serverName = "DE#2", exitCountry = "DE", score = 4.0, tier = 2)
        )
        runBlocking {
            serverManager.setServers(servers, null)
        }

        userSettingsManager = CurrentUserLocalSettingsManager(
            LocalUserSettingsStoreProvider(InMemoryDataStoreFactory())
        )

        countryListViewModel = CountryListViewModel(
            scope,
            serverManager,
            PartnershipsRepository(mockApi),
            mockServerListUpdater,
            VpnStatusProviderUI(scope, mockVpnStateMonitor),
            vpnConnectionManager = mockk(),
            EffectiveCurrentUserSettings(scope.backgroundScope, effectiveSettingsFlow),
            userSettingsManager,
            mockCurrentUser,
            mockk(),
            RestrictionsConfig(scope.backgroundScope, restrictionsFlow),
        )
    }

    @Test
    fun `plus user server list order`() = scope.runTest {
        vpnUserFlow.value = TestUser.plusUser.vpnUser
        val state = countryListViewModel.state.first()

        val plItems =
            (state.sections.first().items.last() as CollapsibleServerGroupModel).sections

        // We have fastest, plus and free groups for PL
        assertEquals(
            listOf(R.string.listFastestServer, R.string.listPlusServers, R.string.listFreeServers),
            plItems.map { it.groupTitle?.titleRes }
        )
        val tiers = plItems.map { it.servers.map { it.tier } }
        // sections: premium fastest server, 2 plus and 2 free
        assertEquals(listOf(1, 2, 2), tiers.map { it.size })
        assertEquals(listOf(2, 2, 0), tiers.map { it.first() })
    }

    @Test
    fun `free user list order`() = scope.runTest {
        vpnUserFlow.value = TestUser.freeUser.vpnUser
        restrictionsFlow.value = restrictionsFlow.value.copy(serverList = true)

        val state = countryListViewModel.state.first()
        assertEquals(
            listOf(
                listOf(FastestConnectionModel::class.java),
                listOf(
                    FreeUpsellBannerModel::class.java,
                    CollapsibleServerGroupModel::class.java,
                    CollapsibleServerGroupModel::class.java
                )
            ),
            state.sections.map { it.items.map { it.javaClass } }
        )
    }

    @Test
    fun `plus user list doesn't get restricted`() = scope.runTest {
        restrictionsFlow.value = restrictionsFlow.value.copy(serverList = false)
        checkPlusUserList()

        restrictionsFlow.value = restrictionsFlow.value.copy(serverList = true)
        checkPlusUserList()
    }

    private suspend fun checkPlusUserList() {
        vpnUserFlow.value = TestUser.plusUser.vpnUser
        val state = countryListViewModel.state.first()
        assertEquals(
            listOf(listOf("DE", "PL")),
            state.sections.map { it.items.filterIsInstance<CollapsibleServerGroupModel>().map { it.countryFlag } }
        )
    }
}
