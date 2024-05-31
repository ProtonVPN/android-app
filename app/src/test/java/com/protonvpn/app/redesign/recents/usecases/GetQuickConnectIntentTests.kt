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
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.data.DefaultConnection
import com.protonvpn.android.redesign.recents.data.RecentConnection
import com.protonvpn.android.redesign.recents.ui.RecentAvailability
import com.protonvpn.android.redesign.recents.usecases.GetIntentAvailability
import com.protonvpn.android.redesign.recents.usecases.GetQuickConnectIntent
import com.protonvpn.android.redesign.recents.usecases.RecentsManager
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.utils.Constants
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetQuickConnectIntentTests {

    @MockK
    private lateinit var mockRecentsManager: RecentsManager

    @MockK
    private lateinit var mockGetIntentAvailability: GetIntentAvailability

    private lateinit var mostRecentConnectionFlow: MutableStateFlow<RecentConnection?>
    private lateinit var currentUser: CurrentUser
    private lateinit var testScope: TestScope
    private lateinit var testUserProvider: TestCurrentUserProvider
    private lateinit var settingsFlow: MutableStateFlow<LocalUserSettings>
    private lateinit var getQuickConnectIntent: GetQuickConnectIntent

    private val freeUser = TestUser.freeUser.vpnUser
    private val plusUser = TestUser.plusUser.vpnUser

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        testScope = TestScope()
        testUserProvider = TestCurrentUserProvider(vpnUser = null)
        currentUser = CurrentUser(testScope.backgroundScope, testUserProvider)
        settingsFlow = MutableStateFlow(LocalUserSettings.Default)
        val effectiveUserSettings = EffectiveCurrentUserSettings(testScope.backgroundScope, settingsFlow)
        mostRecentConnectionFlow = MutableStateFlow(null)
        every { mockRecentsManager.getMostRecentConnection() } returns mostRecentConnectionFlow
        coEvery { mockGetIntentAvailability(any(), any(), any()) } returns RecentAvailability.ONLINE
        coEvery { mockRecentsManager.getDefaultConnectionFlow() } returns flowOf(Constants.DEFAULT_CONNECTION)
        getQuickConnectIntent = GetQuickConnectIntent(currentUser, mockRecentsManager, mockGetIntentAvailability, effectiveUserSettings)
    }

    @Test
    fun `when no connection history return fastest`() = testScope.runTest {
        testUserProvider.vpnUser = plusUser
        mostRecentConnectionFlow.value = null

        assertEquals(ConnectIntent.Fastest, getQuickConnectIntent())
    }

    @Test
    fun `when there is connection history return most recent`() = testScope.runTest {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        coEvery { mockRecentsManager.getDefaultConnectionFlow() } returns flowOf(DefaultConnection.LastConnection)
        testUserProvider.vpnUser = plusUser
        mostRecentConnectionFlow.value = RecentConnection(0, false, connectIntent)

        assertEquals(connectIntent, getQuickConnectIntent())
    }
    @Test
    fun `when recent is offline fastest connection is returned instead`() = testScope.runTest {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        coEvery { mockGetIntentAvailability(any(), any(), any()) } returns RecentAvailability.AVAILABLE_OFFLINE
        coEvery { mockRecentsManager.getDefaultConnectionFlow() } returns flowOf(DefaultConnection.LastConnection)
        testUserProvider.vpnUser = plusUser
        mostRecentConnectionFlow.value = RecentConnection(0, false, connectIntent)

        assertEquals(ConnectIntent.Fastest, getQuickConnectIntent())
    }
    @Test
    fun `when there is connection history return fastest for free user`() = testScope.runTest {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        testUserProvider.vpnUser = freeUser
        mostRecentConnectionFlow.value = RecentConnection(0, false, connectIntent)

        assertEquals(ConnectIntent.Fastest, getQuickConnectIntent())
    }
}
