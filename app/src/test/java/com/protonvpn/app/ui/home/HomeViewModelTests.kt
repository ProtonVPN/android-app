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

import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ProfileColor
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.ui.home.profiles.HomeViewModel
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.test.shared.MockedServers
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class HomeViewModelTests {

    @MockK
    private lateinit var mockVpnStatusProviderUI: VpnStatusProviderUI
    @MockK
    private lateinit var mockConnectionParams: ConnectionParams
    @MockK
    private lateinit var mockServerManager: ServerManager

    private lateinit var homeViewModel: HomeViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { mockServerManager.getServerForProfile(any(), any()) } returns MockedServers.server

        // Note: once more tests are added remove the mockk() calls here and use named dependencies.
        homeViewModel = HomeViewModel(
            mainScope = mockk(),
            userData = mockk(),
            vpnStatusProviderUI = mockVpnStatusProviderUI,
            serverManager = mockServerManager,
            userPlanManager = mockk(relaxed = true),
            certificateRepository = mockk(),
            currentUser = mockk(relaxed = true),
            logoutUseCase = mockk(),
            onSessionClosed = mockk(relaxed = true),
            purchaseEnabled = mockk(),
            appConfig = mockk(),
            appFeaturesPrefs = mockk(relaxed = true)
        )
    }

    @Test
    fun `when switching Secure Core while connected to fastest profile use the same profile to reconnect`() {
        val wrapper = ServerWrapper.makePreBakedFastest()
        val fastestProfile = Profile("fastest", null, wrapper, ProfileColor.FERN.id, null)
        setupMockConnection(fastestProfile)

        val profileToReconnectTo = homeViewModel.getReconnectProfileOnSecureCoreChange()
        assertEquals(fastestProfile, profileToReconnectTo)
    }

    @Test
    fun `when switching Secure Core while connected to server profile use fastest in country profile to reconnect`() {
        val wrapper =
            ServerWrapper.makeWithServer(MockedServers.serverList.find { it.serverName == "FR#1" }!!)
        val profile = Profile("FR#1", null, wrapper, ProfileColor.FERN.id, null)
        setupMockConnection(profile)

        val profileToReconnectTo = homeViewModel.getReconnectProfileOnSecureCoreChange()
        assertEquals("FR", profileToReconnectTo.country)
        assertTrue(profileToReconnectTo.wrapper.isFastestInCountry)
    }

    @Test
    fun `when switching Secure Core while connected to profile enforcing SC use fastest in country profile to reconnect`() {
        val wrapper =
            ServerWrapper.makeWithServer(MockedServers.serverList.find { it.serverName == "FR#1" }!!)
        val profile = Profile("FR#1", null, wrapper, ProfileColor.FERN.id, false)
        setupMockConnection(profile)

        val profileToReconnectTo = homeViewModel.getReconnectProfileOnSecureCoreChange()
        assertEquals("FR", profileToReconnectTo.country)
        assertTrue(profileToReconnectTo.wrapper.isFastestInCountry)
    }

    @Test
    fun `when switching Secure Core while connected and there's no server for the same country then connect to fallback profile`() {
        val fastestWrapper = ServerWrapper.makePreBakedFastest()
        val fallbackProfile = Profile("fastest", null, fastestWrapper, ProfileColor.FERN.id, null)

        val wrapper =
            ServerWrapper.makeWithServer(MockedServers.serverList.find { it.serverName == "FR#1" }!!)
        val profile = Profile("FR#1", null, wrapper, ProfileColor.FERN.id, false)
        setupMockConnection(profile)

        every { mockServerManager.getServerForProfile(any(), any()) } returns null
        every { mockServerManager.defaultFallbackConnection } returns fallbackProfile

        val profileToReconnectTo = homeViewModel.getReconnectProfileOnSecureCoreChange()
        assertEquals(fallbackProfile, profileToReconnectTo)
    }

    private fun setupMockConnection(profile: Profile) {
        every { mockConnectionParams.profile } returns profile
        every { mockVpnStatusProviderUI.connectionParams } returns mockConnectionParams
        every { mockVpnStatusProviderUI.isConnected } returns true
    }
}
