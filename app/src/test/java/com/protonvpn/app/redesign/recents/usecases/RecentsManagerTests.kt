/*
 * Copyright (c) 2023. Proton AG
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
import com.protonvpn.android.redesign.recents.usecases.RecentsManager
import com.protonvpn.android.redesign.recents.data.RecentConnection
import com.protonvpn.android.redesign.recents.data.RecentsDao
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecentsManagerTests {

    @RelaxedMockK
    private lateinit var mockRecentsDao: RecentsDao

    private lateinit var currentUserProvider: TestCurrentUserProvider
    private lateinit var testScope: TestScope
    private lateinit var recentsManager: RecentsManager

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val testDispatcher =  UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)

        currentUserProvider = TestCurrentUserProvider(TestUser.plusUser.vpnUser)
        val currentUser =
            CurrentUser(testScope.backgroundScope, currentUserProvider)
        recentsManager =
            RecentsManager(testScope.backgroundScope, mockRecentsDao, currentUser, { testScope.currentTime })
    }

    @Test
    fun `when user changes the new user's recents are returned`() = testScope.runTest {
        val user1 = TestUser.plusUser.vpnUser
        val user2 = TestUser.freeUser.vpnUser
        val user1Recents = listOf(
            RecentConnection(1, false, TestConnectIntent),
            RecentConnection(2, true, TestConnectIntent)
        )
        val user2Recents = listOf(
            RecentConnection(10, false, TestConnectIntent),
            RecentConnection(20, true, TestConnectIntent),
            RecentConnection(30, false, TestConnectIntent)
        )
        coEvery { mockRecentsDao.getRecentsList(user1.userId) } returns flowOf(user1Recents)
        coEvery { mockRecentsDao.getRecentsList(user2.userId) } returns flowOf(user2Recents)

        currentUserProvider.vpnUser = user1
        assertEquals(user1Recents, recentsManager.getRecentsList().first())
        currentUserProvider.vpnUser = user2
        assertEquals(user2Recents, recentsManager.getRecentsList().first())
    }

    @Test
    fun `when pinned item is removed it is deleted from DB`() = testScope.runTest {
        coEvery { mockRecentsDao.getMostRecentConnection(any()) } returns flowOf(null)
        recentsManager.remove(1)
        coVerify { mockRecentsDao.delete(1) }
    }

    @Test
    fun `when pinned connected item is removed it is unpinned`() = testScope.runTest {
        val id = 1L
        val recent = RecentConnection(id, true, TestConnectIntent)
        coEvery { mockRecentsDao.getMostRecentConnection(any()) } returns flowOf(recent)

        recentsManager.remove(id)
        coVerify { mockRecentsDao.unpin(id) }
    }

    companion object {
        private val TestConnectIntent = ConnectIntent.FastestInCountry(CountryId.fastest, emptySet())
    }
}
