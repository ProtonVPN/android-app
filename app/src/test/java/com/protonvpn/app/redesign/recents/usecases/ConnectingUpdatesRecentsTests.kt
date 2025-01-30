/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.app.redesign.recents.usecases

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.data.RecentsDao
import com.protonvpn.android.redesign.recents.usecases.ConnectingUpdatesRecents
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createServer
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ConnectingUpdatesRecentsTests {

    @MockK
    private lateinit var mockRecentsDao: RecentsDao

    private lateinit var currentUser: CurrentUser
    private lateinit var testScope: TestScope
    private lateinit var testUserProvider: TestCurrentUserProvider
    private lateinit var vpnStateMonitor: VpnStateMonitor

    private lateinit var connectingUpdatesRecents: ConnectingUpdatesRecents

    private val freeUser = TestUser.freeUser.vpnUser
    private val plusUser = TestUser.plusUser.vpnUser
    private val server = createServer("server1")
    private val connectIntent = ConnectIntent.FastestInCountry(CountryId.switzerland, emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        MockKAnnotations.init(this)

        testScope = TestScope(UnconfinedTestDispatcher())
        testUserProvider = TestCurrentUserProvider(vpnUser = plusUser)
        currentUser = CurrentUser(testUserProvider)
        vpnStateMonitor = VpnStateMonitor()
        val vpnStatusProviderUI = VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor)

        coEvery { mockRecentsDao.insertOrUpdateForConnection(any(), any(), any()) } just runs

        connectingUpdatesRecents = ConnectingUpdatesRecents(
            testScope.backgroundScope,
            vpnStatusProviderUI,
            mockRecentsDao,
            currentUser,
            testScope::currentTime
        )
    }

    @Test
    fun `when plus user connects then connection is added to recents`() = testScope.runTest {
        val connectionParams = ConnectionParams(connectIntent, server, null, null)
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))

        coVerify { mockRecentsDao.insertOrUpdateForConnection(plusUser.userId, connectIntent, any()) }
    }

    @Test
    fun `when recent is disconnected and connected again recent is reinserted`() = testScope.runTest {
        val connectionParams = ConnectionParams(connectIntent, server, null, null)
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Disabled, connectionParams))
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        coVerify(exactly = 2) { mockRecentsDao.insertOrUpdateForConnection(plusUser.userId, connectIntent, any()) }
    }

    @Test
    fun `when plus user attempts to connect and fails then connection is added to recents`() = testScope.runTest {
        val connectionParams = ConnectionParams(connectIntent, server, null, null)
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connecting, connectionParams))
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Disabled, connectionParams))

        coVerify { mockRecentsDao.insertOrUpdateForConnection(plusUser.userId, connectIntent, any()) }
    }

    @Test
    fun `when free user connects then no connection is added to recents`() = testScope.runTest {
        testUserProvider.vpnUser = freeUser
        val connectionParams = ConnectionParams(connectIntent, server, null, null)
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connecting, connectionParams))
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, connectionParams))

        coVerify { mockRecentsDao wasNot Called }
    }

    @Test
    fun `when Guest Hole connection is made then no connection is added to recents`() = testScope.runTest {
        val ghIntent = AnyConnectIntent.GuestHole(server.serverId)
        val connectionParams = ConnectionParams(ghIntent, server, null, null)
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connecting, connectionParams))
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Disabled, connectionParams))

        coVerify { mockRecentsDao wasNot Called }
    }
}
