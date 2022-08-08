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
import com.protonvpn.android.vpn.VpnStateMonitor
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
    private lateinit var mockStateMonitor: VpnStateMonitor
    @MockK
    private lateinit var mockConnectionParams: ConnectionParams

    private lateinit var homeViewModel: HomeViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        // Note: once more tests are added remove the mockk() calls here and use named dependencies.
        homeViewModel = HomeViewModel(
            mockk(),
            mockk(),
            mockStateMonitor,
            mockk(),
            mockk(relaxed = true),
            mockk(),
            mockk(relaxed = true),
            mockk(),
            mockk(relaxed = true),
            mockk()
        )
    }

    @Test
    fun `when switching Secure Core while connected to fastest profile use the same profile to reconnect`() {
        val wrapper = ServerWrapper.makePreBakedFastest()
        val fastestProfile = Profile("fastest", null, wrapper, ProfileColor.FERN.id, null)
        every { mockConnectionParams.profile } returns fastestProfile
        every { mockStateMonitor.connectionParams } returns mockConnectionParams
        every { mockStateMonitor.isConnected } returns true

        val profileToReconnectTo = homeViewModel.getReconnectProfileOnSecureCoreChange()
        assertEquals(fastestProfile, profileToReconnectTo)
    }

    @Test
    fun `when switching Secure Core while connected to server profile use fastest in country profile to reconnect`() {
        val wrapper =
            ServerWrapper.makeWithServer(MockedServers.serverList.find { it.serverName == "FR#1" }!!)
        val profile = Profile("FR#1", null, wrapper, ProfileColor.FERN.id, null)
        every { mockConnectionParams.profile } returns profile
        every { mockStateMonitor.connectionParams } returns mockConnectionParams
        every { mockStateMonitor.isConnected } returns true

        val profileToReconnectTo = homeViewModel.getReconnectProfileOnSecureCoreChange()
        assertEquals("FR", profileToReconnectTo.country)
        assertTrue(profileToReconnectTo.wrapper.isFastestInCountry)
    }

    @Test
    fun `when switching Secure Core while connected to profile enforcing SC use fastest in country profile to reconnect`() {
        val wrapper =
            ServerWrapper.makeWithServer(MockedServers.serverList.find { it.serverName == "FR#1" }!!)
        val profile = Profile("FR#1", null, wrapper, ProfileColor.FERN.id, false)
        every { mockConnectionParams.profile } returns profile
        every { mockStateMonitor.connectionParams } returns mockConnectionParams
        every { mockStateMonitor.isConnected } returns true

        val profileToReconnectTo = homeViewModel.getReconnectProfileOnSecureCoreChange()
        assertEquals("FR", profileToReconnectTo.country)
        assertTrue(profileToReconnectTo.wrapper.isFastestInCountry)
    }
}
