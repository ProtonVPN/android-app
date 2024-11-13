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
package com.protonvpn.app.vpn

import com.protonvpn.android.appconfig.ApiNotificationOfferButton
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.netshield.NetShieldStats
import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ui.ChangeServerViewState
import com.protonvpn.android.redesign.vpn.ui.StatusBanner
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewState
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewStateFlow
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.ui.promooffers.PromoOfferBannerState
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createServer
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class VpnStatusViewStateFlowTest {

    @MockK
    private lateinit var vpnStatusProviderUi: VpnStatusProviderUI

    @MockK
    private lateinit var vpnConnectionManager: VpnConnectionManager
    @MockK
    private lateinit var mockProfilesDao: ProfilesDao

    private lateinit var testUserProvider: TestCurrentUserProvider
    private lateinit var testScope: TestScope
    private lateinit var settingsFlow: MutableStateFlow<LocalUserSettings>
    private lateinit var serverListUpdaterPrefs: ServerListUpdaterPrefs
    private lateinit var vpnStatusViewStateFlow: VpnStatusViewStateFlow
    private val server: Server = createServer()
    private val connectionParams = ConnectionParams(ConnectIntent.Default, server, null, null)
    private lateinit var statusFlow: MutableStateFlow<VpnStatusProviderUI.Status>
    private lateinit var netShieldStatsFlow: MutableStateFlow<NetShieldStats>
    private lateinit var changeServerFlow: MutableStateFlow<ChangeServerViewState?>
    private lateinit var promoBannerFlow: MutableStateFlow<PromoOfferBannerState?>

    private val freeUser = TestUser.freeUser.vpnUser
    private val plusUser = TestUser.plusUser.vpnUser
    private val promoBanner = PromoOfferBannerState(
        "", "", ApiNotificationOfferButton(), false, null, "id", null
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val testCoroutineScheduler = TestCoroutineScheduler()
        val testDispatcher = UnconfinedTestDispatcher(testCoroutineScheduler)
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)

        serverListUpdaterPrefs = ServerListUpdaterPrefs(MockSharedPreferencesProvider())
        serverListUpdaterPrefs.ipAddress = "1.1.1.1"
        serverListUpdaterPrefs.lastKnownCountry = "US"

        statusFlow =
            MutableStateFlow(VpnStatusProviderUI.Status(VpnState.Connected, connectionParams))
        every { vpnStatusProviderUi.uiStatus } returns statusFlow
        netShieldStatsFlow = MutableStateFlow(NetShieldStats())
        every { vpnConnectionManager.netShieldStats } returns netShieldStatsFlow

        testUserProvider = TestCurrentUserProvider(plusUser)
        val currentUser = CurrentUser(testUserProvider)
        settingsFlow = MutableStateFlow(LocalUserSettings.Default)
        changeServerFlow = MutableStateFlow(null)
        promoBannerFlow = MutableStateFlow(null)
        val effectiveUserSettings =
            EffectiveCurrentUserSettings(testScope.backgroundScope, settingsFlow)
        val settingsForConnection = SettingsForConnection(effectiveUserSettings, mockProfilesDao, vpnStatusProviderUi)
        vpnStatusViewStateFlow = VpnStatusViewStateFlow(
            vpnStatusProviderUi,
            serverListUpdaterPrefs,
            vpnConnectionManager,
            settingsForConnection,
            currentUser,
            changeServerFlow,
            promoBannerFlow,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `change in vpnStatus changes StatusViewState flow`() = runTest {
        statusFlow.emit(VpnStatusProviderUI.Status(VpnState.Disabled, null))
        assert(vpnStatusViewStateFlow.first() is VpnStatusViewState.Disabled)
        statusFlow.emit(VpnStatusProviderUI.Status(VpnState.Connecting, connectionParams))
        assert(vpnStatusViewStateFlow.first() is VpnStatusViewState.Connecting)
        statusFlow.emit(VpnStatusProviderUI.Status(VpnState.Connected, connectionParams))
        assert(vpnStatusViewStateFlow.first() is VpnStatusViewState.Connected)
    }

    @Test
    fun `upon upgrade user will see netshield instead of upsell banner`() = runTest {
        statusFlow.emit(VpnStatusProviderUI.Status(VpnState.Connected, connectionParams))
        testUserProvider.vpnUser = freeUser
        assert((vpnStatusViewStateFlow.first() as VpnStatusViewState.Connected).banner is StatusBanner.UpgradePlus)
        testUserProvider.vpnUser = plusUser
        assert((vpnStatusViewStateFlow.first() as VpnStatusViewState.Connected).banner is StatusBanner.NetShieldBanner)
    }

    @Test
    fun `change server locked state changes upsell to not country wanted banner`() = runTest {
        statusFlow.emit(VpnStatusProviderUI.Status(VpnState.Connected, connectionParams))
        testUserProvider.vpnUser = freeUser
        assert((vpnStatusViewStateFlow.first() as VpnStatusViewState.Connected).banner is StatusBanner.UpgradePlus)
        changeServerFlow.emit(ChangeServerViewState.Locked(1, 1, true))
        assert((vpnStatusViewStateFlow.first() as VpnStatusViewState.Connected).banner is StatusBanner.UnwantedCountry)
    }

    @Test
    fun `user downgrade shows upsell banner`() = runTest {
        statusFlow.emit(VpnStatusProviderUI.Status(VpnState.Connected, connectionParams))
        assert(vpnStatusViewStateFlow.first() is VpnStatusViewState.Connected)
        testUserProvider.vpnUser = freeUser
        assert((vpnStatusViewStateFlow.first() as VpnStatusViewState.Connected).banner is StatusBanner.UpgradePlus)
    }

    @Test
    fun `vpn-essential plan shows no netshields stats nor any banner`() = runTest {
        statusFlow.emit(VpnStatusProviderUI.Status(VpnState.Connected, connectionParams))
        assert(vpnStatusViewStateFlow.first() is VpnStatusViewState.Connected)
        testUserProvider.vpnUser = TestUser.businessEssential.vpnUser
        assertEquals(VpnStatusViewState.Connected(false, null), vpnStatusViewStateFlow.first())
    }

    @Test
    fun `change in netShield stats are reflected in StatusViewState flow`() = runTest {
        statusFlow.emit(VpnStatusProviderUI.Status(VpnState.Connected, connectionParams))
        assert(vpnStatusViewStateFlow.first() is VpnStatusViewState.Connected)
        netShieldStatsFlow.emit(NetShieldStats(3, 3, 3000))
        val netShieldStats =
            ((vpnStatusViewStateFlow.first() as VpnStatusViewState.Connected).banner as StatusBanner.NetShieldBanner).netShieldState.netShieldStats
        assertEquals(NetShieldStats(3L, 3L, 3000L), netShieldStats)
    }

    @Test
    fun `when promo banner is present then no netshield upsell banner is shown for free users`() = runTest{
        statusFlow.value = VpnStatusProviderUI.Status(VpnState.Connected, connectionParams)
        testUserProvider.vpnUser = freeUser
        assertEquals(VpnStatusViewState.Connected(false, StatusBanner.UpgradePlus), vpnStatusViewStateFlow.first())
        promoBannerFlow.value = promoBanner
        assertEquals(VpnStatusViewState.Connected(false, null), vpnStatusViewStateFlow.first())
    }

    @Test
    fun `when promo banner is present then no change country banner is shown for free users`() = runTest{
        statusFlow.value = VpnStatusProviderUI.Status(VpnState.Connected, connectionParams)
        testUserProvider.vpnUser = freeUser
        changeServerFlow.value = ChangeServerViewState.Locked(10, 10, false)
        assertEquals(VpnStatusViewState.Connected(false, StatusBanner.UnwantedCountry), vpnStatusViewStateFlow.first())
        promoBannerFlow.value = promoBanner
        assertEquals(VpnStatusViewState.Connected(false, null), vpnStatusViewStateFlow.first())
    }

    @Test
    fun `when promo banner is present then NetShield state is shown for paid users`() = runTest{
        statusFlow.value = VpnStatusProviderUI.Status(VpnState.Connected, connectionParams)
        promoBannerFlow.value = promoBanner
        val vpnStatusViewState = vpnStatusViewStateFlow.first()
        assertIs<VpnStatusViewState.Connected>(vpnStatusViewState)
        assertIs<StatusBanner.NetShieldBanner>(vpnStatusViewState.banner)
    }
}
