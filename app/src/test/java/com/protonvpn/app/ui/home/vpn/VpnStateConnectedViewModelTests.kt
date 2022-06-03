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

package com.protonvpn.app.ui.home.vpn

import com.protonvpn.android.R
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ProfileColor
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.ui.home.vpn.VpnStateConnectedViewModel
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.runWhileCollecting
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import me.proton.core.presentation.utils.SnackType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VpnStateConnectedViewModelTests {

    private val colorId = ProfileColor.CARROT.id

    @RelaxedMockK
    private lateinit var mockStateMonitor: VpnStateMonitor
    @RelaxedMockK
    private lateinit var mockServerManager: ServerManager
    @RelaxedMockK
    private lateinit var mockTrafficMonitor: TrafficMonitor
    @MockK
    private lateinit var mockConnectionParams: ConnectionParams

    private lateinit var viewModel: VpnStateConnectedViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { mockStateMonitor.connectionParams } returns mockConnectionParams
        viewModel = VpnStateConnectedViewModel(mockStateMonitor, mockServerManager, mockTrafficMonitor)
    }

    @Test
    fun `saving to profile saves the connected server`() {
        val server = MockedServers.server
        every { mockConnectionParams.server } returns server

        viewModel.saveToProfile()
        verify { mockServerManager.addToProfileList(server.serverName, any(), server) }
    }

    @Test
    fun `when a profile with connected server exists, no new profile is created`() = runBlockingTest {
        val server = MockedServers.server
        every { mockConnectionParams.server } returns server
        val existingProfiles = listOf(
            Profile("Fastest", null, ServerWrapper.makePreBakedFastest(), colorId, null),
            Profile("Server", null, ServerWrapper.makeWithServer(server), colorId, false)
        )
        every { mockServerManager.getSavedProfiles() } returns existingProfiles

        val notifications = runWhileCollecting(viewModel.eventNotification) {
            viewModel.saveToProfile()
        }
        verify(exactly = 0) { mockServerManager.addToProfileList(any(), any(), any()) }
        assertEquals(
            listOf(VpnStateConnectedViewModel.SnackbarNotification(R.string.saveProfileAlreadySaved, SnackType.Norm)),
            notifications
        )
    }
}
