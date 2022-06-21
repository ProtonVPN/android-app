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

package com.protonvpn.app.tv.main

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.R
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ProfileColor
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.tv.main.TvMainViewModel
import com.protonvpn.android.tv.models.ProfileCard
import com.protonvpn.android.tv.models.QuickConnectCard
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.RecentsManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestUser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TvMainViewModelTests {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockCurrentUser: CurrentUser
    @MockK
    private lateinit var mockContext: Context

    private lateinit var testDispatcher: TestCoroutineDispatcher
    private lateinit var testScope: TestCoroutineScope

    private lateinit var vpnUserFlow: MutableStateFlow<VpnUser?>
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var serverManager: ServerManager
    private lateinit var userData: UserData

    private lateinit var viewModel: TvMainViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        testDispatcher = TestCoroutineDispatcher()
        testScope = TestCoroutineScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)
        Storage.setPreferences(MockSharedPreference())
        userData = UserData.create()

        vpnStateMonitor = VpnStateMonitor()
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Disabled, null))

        serverManager = ServerManager(userData, mockCurrentUser) { 0 }.apply {
            setServers(MockedServers.serverList, "us")
        }
        vpnUserFlow = MutableStateFlow(TestUser.plusUser.vpnUser)
        every { mockCurrentUser.vpnUserFlow } returns vpnUserFlow
        every { mockCurrentUser.vpnUserCached() } answers { vpnUserFlow.value }

        setupStrings(mockContext)
        mockkObject(CountryTools)
        every { CountryTools.getLargeFlagResource(any(), any()) } returns 0
        every { CountryTools.getPreferredLocale() } returns Locale.US

        // Note: the amount of dependencies is crazy, we should refactor TvMainViewModel.
        viewModel = TvMainViewModel(
            appConfig = mockk(relaxed = true),
            serverManager = serverManager,
            mainScope = testScope,
            serverListUpdater = mockk(relaxed = true),
            vpnStateMonitor = vpnStateMonitor,
            vpnConnectionManager = mockk(relaxed = true),
            recentsManager = RecentsManager(testScope, vpnStateMonitor, mockk(relaxed = true)),
            userData = userData,
            currentUser = mockCurrentUser,
            logoutUseCase = mockk(relaxed = true),
            userPlanManager = mockk(relaxed = true),
            certificateRepository = mockk(relaxed = true),
            purchaseEnabled = mockk(relaxed = true)
        )
    }

    @Test
    fun `quick connect country shown in recents when there are no recent countries`() {
        val recents = viewModel.getRecentCardList(mockContext)
        assertEquals(1, recents.size)
        val recent = recents[0]
        assertIs<QuickConnectCard>(recent)
    }

    @Test
    fun `recently connected country shown after quick connect`() {
        val server = MockedServers.server
        val connectionParams = ConnectionParams(countryProfile(server.exitCountry), server, null, null)

        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Disabled, null))

        val recents = viewModel.getRecentCardList(mockContext)
        assertEquals(2, recents.size)
        assertIs<QuickConnectCard>(recents[0])
        assertEquals("Recommended", recents[0].bottomTitle?.text)
        assertIs<ProfileCard>(recents[1])
        assertEquals("Canada", recents[1].bottomTitle?.text)
    }

    @Test
    fun `favorite hides recommended profile`() {
        val server = MockedServers.server

        viewModel.setAsDefaultCountry(true, serverManager.getVpnExitCountry(server.exitCountry, false)!!)

        val recents = viewModel.getRecentCardList(mockContext)
        assertEquals(1, recents.size)
        assertIs<QuickConnectCard>(recents[0])
        assertEquals("Favorite", recents[0].bottomTitle?.text)
    }

    @Test
    fun `recent country same as default connection is hidden from recents`() {
        val server = MockedServers.server
        val connectionParams = ConnectionParams(countryProfile(server.exitCountry), server, null, null)

        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Disabled, null))

        val recentsBefore = viewModel.getRecentCardList(mockContext)
        assertEquals(2, recentsBefore.size)

        viewModel.setAsDefaultCountry(true, serverManager.getVpnExitCountry(server.exitCountry, false)!!)

        val recentsAfter = viewModel.getRecentCardList(mockContext)
        assertEquals(1, recentsAfter.size)
        assertIs<QuickConnectCard>(recentsAfter[0])
        assertEquals("Favorite", recentsAfter[0].bottomTitle?.text)
    }

    @Test
    fun `country being connected to is hidden from recents even if different server is used`() {
        val server1 = MockedServers.server
        val server2 = MockedServers.serverList[1]
        assertEquals("Both servers in this test need to be in the same country", server1.exitCountry, server2.exitCountry)
        val countryConnectionParams = ConnectionParams(countryProfile(server1.exitCountry), server1, null, null)
        val server2ConnectionParams = ConnectionParams(Profile.getTempProfile(server2), server2, null, null)
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, countryConnectionParams))

        val recentsBefore = viewModel.getRecentCardList(mockContext)
        assertEquals(2, recentsBefore.size)

        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, server2ConnectionParams))

        val recentsAfter = viewModel.getRecentCardList(mockContext)
        assertEquals(2, recentsAfter.size)
        assertIs<QuickConnectCard>(recentsAfter[0])
        assertEquals("Disconnect", recentsAfter[0].bottomTitle?.text)
        assertIs<ProfileCard>(recentsAfter[1])
        assertEquals("Recommended", recentsAfter[1].bottomTitle?.text)
    }

    @Test
    fun `country of fastest profile is added to recents and shown when fastest country changes`() {
        val server1 = MockedServers.server
        val server2 = MockedServers.serverList[2]
        assertNotEquals("Servers in this test need to be in different countries", server1.exitCountry, server2.exitCountry)
        serverManager.setServers(listOf(server1), null)

        val firstDefaultServer = serverManager.getServerForProfile(serverManager.defaultConnection, vpnUserFlow.value)!!
        val firstConnectionParams = ConnectionParams(serverManager.defaultConnection, firstDefaultServer, null, null)
        val firstCountry = firstDefaultServer.exitCountry

        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, firstConnectionParams))
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Disabled, null))

        val recentsBefore = viewModel.getRecentCardList(mockContext)
        assertEquals(1, recentsBefore.size)
        assertIs<QuickConnectCard>(recentsBefore[0])

        serverManager.setServers(listOf(server2), null)
        val secondDefaultServer = serverManager.getServerForProfile(serverManager.defaultConnection, vpnUserFlow.value)!!
        val secondConnectionParams = ConnectionParams(serverManager.defaultConnection, secondDefaultServer, null, null)

        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, secondConnectionParams))
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Disabled, null))

        val recentsAfter = viewModel.getRecentCardList(mockContext)
        assertEquals(2, recentsAfter.size)
        assertIs<QuickConnectCard>(recentsAfter[0])
        val secondCard = recentsAfter[1]
        assertIs<ProfileCard>(secondCard)
        assertEquals(firstCountry, secondCard.profile.country)
    }

    private fun countryProfile(countryCode: String) = Profile(
        countryCode, null, ServerWrapper.makeFastestForCountry(countryCode), ProfileColor.OLIVE.id, false
    )

    // TvMainViewModel needs to be refactored to not rely on Context - strings should be resolved in UI.
    private fun setupStrings(mockContext: Context) {
        every { mockContext.getString(R.string.tv_quick_connect_recommened) } returns "Recommended"
        every { mockContext.getString(R.string.tv_quick_connect_favourite) } returns "Favorite"
        every { mockContext.getString(R.string.disconnect) } returns "Disconnect"
        every { mockContext.getString(R.string.cancel) } returns "Cancel"
    }
}
