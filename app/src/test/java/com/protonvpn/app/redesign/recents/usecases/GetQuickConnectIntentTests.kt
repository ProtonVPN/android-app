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
import com.protonvpn.android.redesign.recents.data.RecentConnection
import com.protonvpn.android.redesign.recents.usecases.GetQuickConnectIntent
import com.protonvpn.android.redesign.recents.usecases.RecentsManager
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetQuickConnectIntentTests {

    @MockK
    private lateinit var mockRecentsManager: RecentsManager

    private lateinit var mostRecentConnectionFlow: MutableStateFlow<RecentConnection?>
    private lateinit var currentUser: CurrentUser
    private lateinit var testScope: TestScope
    private lateinit var testUserProvider: TestCurrentUserProvider

    private lateinit var getQuickConnectIntent: GetQuickConnectIntent

    private val freeUser = TestUser.freeUser.vpnUser
    private val plusUser = TestUser.plusUser.vpnUser

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        testScope = TestScope()
        testUserProvider = TestCurrentUserProvider(vpnUser = null)
        currentUser = CurrentUser(testScope.backgroundScope, testUserProvider)

        mostRecentConnectionFlow = MutableStateFlow(null)
        every { mockRecentsManager.getMostRecentConnection() } returns mostRecentConnectionFlow

        getQuickConnectIntent = GetQuickConnectIntent(currentUser, mockRecentsManager)
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
        testUserProvider.vpnUser = plusUser
        mostRecentConnectionFlow.value = RecentConnection(0, false, connectIntent)

        assertEquals(connectIntent, getQuickConnectIntent())
    }

    @Test
    fun `when there is connection history return fastest for free user`() = testScope.runTest {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        testUserProvider.vpnUser = freeUser
        mostRecentConnectionFlow.value = RecentConnection(0, false, connectIntent)

        assertEquals(ConnectIntent.Fastest, getQuickConnectIntent())
    }
}
