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
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.ApiNotificationOfferButton
import com.protonvpn.android.appconfig.ApiNotificationTypes
import com.protonvpn.android.appconfig.ChangeServerConfig
import com.protonvpn.android.appconfig.Restrictions
import com.protonvpn.android.appconfig.RestrictionsConfig
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.PartnersResponse
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.partnerships.PartnershipsRepository
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.ui.home.countries.CollapsibleServerGroupModel
import com.protonvpn.android.ui.home.countries.CountryListViewModel
import com.protonvpn.android.ui.home.countries.FreeUpsellBannerModel
import com.protonvpn.android.ui.home.countries.PromoOfferBannerModel
import com.protonvpn.android.ui.home.countries.RecommendedConnectionModel
import com.protonvpn.android.ui.promooffers.PromoOffersPrefs
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.app.userstorage.createDummyProfilesManager
import com.protonvpn.test.shared.ApiNotificationTestHelper.mockFullScreenImagePanel
import com.protonvpn.test.shared.ApiNotificationTestHelper.mockOffer
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.MockSharedPreferencesProvider
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    private lateinit var promoNotificationsFlow: MutableStateFlow<List<ApiNotification>>
    private lateinit var restrictionsFlow: MutableStateFlow<Restrictions>
    private lateinit var countryListViewModel: CountryListViewModel
    private lateinit var promoOfferPrefs: PromoOffersPrefs

    @MockK
    private lateinit var mockApi: ProtonApiRetroFit

    @MockK
    private lateinit var mockNotificationsManager: ApiNotificationManager

    @MockK
    private lateinit var mockCurrentUser: CurrentUser

    @MockK
    private lateinit var mockServerListUpdater: ServerListUpdater

    @RelaxedMockK
    private lateinit var mockVpnStateMonitor: VpnStateMonitor

    @RelaxedMockK
    private lateinit var context: Context

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
        serverManager.setServers(servers, null)

        promoNotificationsFlow = MutableStateFlow(emptyList())
        every { mockNotificationsManager.activeListFlow } returns promoNotificationsFlow
        promoOfferPrefs = PromoOffersPrefs(MockSharedPreferencesProvider())

        countryListViewModel = CountryListViewModel(
            serverManager,
            PartnershipsRepository(mockApi),
            mockServerListUpdater,
            VpnStatusProviderUI(scope, mockVpnStateMonitor),
            EffectiveCurrentUserSettings(scope.backgroundScope, effectiveSettingsFlow),
            mockCurrentUser,
            RestrictionsConfig(scope.backgroundScope, restrictionsFlow),
            mockNotificationsManager,
            promoOfferPrefs
        )
    }

    @Test
    fun `free user server list order`() = scope.runTest {
        vpnUserFlow.value = TestUser.freeUser.vpnUser
        val state = countryListViewModel.state.first()

        // Fist country from first section (free countries) - PL
        val plItems =
            (state.sections.first().items.first() as CollapsibleServerGroupModel).sections

        // We have fastest, free and plus groups for PL
        assertEquals(
            listOf(R.string.listFastestServer, R.string.listFreeServers, R.string.listPlusServers),
            plItems.map { it.groupTitle?.titleRes }
        )
        val tiers = plItems.map { it.servers.map { it.tier } }
        // sections: premium fastest server, 2 plus and 2 free
        assertEquals(listOf(1, 2, 2), tiers.map { it.size })
        assertEquals(listOf(0, 0, 2), tiers.map { it.first() })
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
    fun `free user list when not restricted`() = scope.runTest {
        vpnUserFlow.value = TestUser.freeUser.vpnUser

        val state = countryListViewModel.state.first()
        assertEquals(
            listOf(
                listOf(CollapsibleServerGroupModel::class),
                listOf(FreeUpsellBannerModel::class, CollapsibleServerGroupModel::class)
            ),
            state.sections.map { it.items.map { it::class } }
        )
        // 2 country sections: with free PL and plus DE
        assertEquals(
            listOf(listOf("PL"), listOf("DE")),
            state.sections.map { it.items.filterIsInstance<CollapsibleServerGroupModel>().map { it.countryFlag } }
        )

        // We have fastest, free and plus groups for free country
        val firstCountryItems =
            state.sections.first().items.first() as CollapsibleServerGroupModel
        assertEquals(
            listOf(R.string.listFastestServer, R.string.listFreeServers, R.string.listPlusServers),
            firstCountryItems.sections.map { it.groupTitle?.titleRes }
        )
        val sectionTiers = firstCountryItems.sections.map { it.servers.map { it.tier } }
        // sections: free fastest server, 2 free, 2 plus
        assertEquals(listOf(1, 2, 2), sectionTiers.map { it.size })
        assertEquals(listOf(0, 0, 2), sectionTiers.map { it.first() })
    }

    @Test
    fun `free user list when restricted`() = scope.runTest {
        vpnUserFlow.value = TestUser.freeUser.vpnUser
        restrictionsFlow.value = restrictionsFlow.value.copy(serverList = true)

        val state = countryListViewModel.state.first()
        assertEquals(
            listOf(
                listOf(RecommendedConnectionModel::class.java),
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

    @Test
    fun `promo offer banner shown to free and paid users`() = scope.runTest {
        val action = ApiNotificationOfferButton(url = "https://proton.me", actionBehaviors = listOf("autologin"))
        val notification = mockOffer(
            id = "banner",
            type = ApiNotificationTypes.TYPE_COUNTRY_LIST_BANNER,
            end = 10,
            panel = mockFullScreenImagePanel(
                "https://proton.me/banner.png",
                "Sale!",
                button = action,
                showCountdown = true,
            ),
            reference = "offer-A"
        )
        promoNotificationsFlow.value = listOf(notification)
        vpnUserFlow.value = TestUser.freeUser.vpnUser

        val expectedItem =
            PromoOfferBannerModel("https://proton.me/banner.png", "Sale!", action, true, 10_000L, "banner", "offer-A")

        val sectionsNoRestrictions = countryListViewModel.state.first().sections
        assertEquals(expectedItem, sectionsNoRestrictions[1].items.find { it is PromoOfferBannerModel })

        // With restrictions
        restrictionsFlow.value = Restrictions(true, ChangeServerConfig(1, 2, 10))
        val sectionsWithRestrictions = countryListViewModel.state.first().sections
        assertEquals(expectedItem, sectionsWithRestrictions[1].items.find { it is PromoOfferBannerModel })

        // Plus user
        vpnUserFlow.value = TestUser.plusUser.vpnUser
        val plusUserSections = countryListViewModel.state.first().sections
        assertEquals(expectedItem, plusUserSections[0].items.find { it is PromoOfferBannerModel })

        // Dismissed
        promoOfferPrefs.addVisitedOffer(notification.id)
        val plusUserSectionsWithDismissedBanner = countryListViewModel.state.first().sections
        assertEquals(null, plusUserSectionsWithDismissedBanner[0].items.find { it is PromoOfferBannerModel })
    }

    private suspend fun checkPlusUserList() {
        vpnUserFlow.value = TestUser.plusUser.vpnUser
        val state = countryListViewModel.state.first()
        assertEquals(
            listOf(listOf("DE", "PL")),
            state.sections.map { it.items.map { (it as CollapsibleServerGroupModel).countryFlag } }
        )
    }
}
