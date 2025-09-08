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

import app.cash.turbine.test
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.redesign.recents.data.RecentConnection
import com.protonvpn.android.redesign.recents.data.RecentsDao
import com.protonvpn.android.redesign.recents.usecases.RecentsManager
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createConnectIntentFastestInCountry
import com.protonvpn.test.shared.createConnectIntentGateway
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

        testScope = TestScope(context = UnconfinedTestDispatcher())

        currentUserProvider = TestCurrentUserProvider(TestUser.plusUser.vpnUser)

        val currentUser = CurrentUser(currentUserProvider)

        recentsManager = RecentsManager(
            currentUser = currentUser,
            mainScope = testScope.backgroundScope,
            recentsDao = mockRecentsDao,
            clock = { testScope.currentTime },
        )
    }

    @Test
    fun `GIVEN user id WHEN observing recent connections THEN recent connections are emitted`() = testScope.runTest {
        val vpnUserId = TestUser.plusUser.vpnUser.userId
        val expectedRecentConnections = listOf(RecentConnection.UnnamedRecent(1, false, createConnectIntentFastestInCountry()))
        coEvery { mockRecentsDao.getRecentsList(vpnUserId) } returns flowOf(expectedRecentConnections)

        recentsManager.getRecentsList().test {
            val recentConnections = awaitItem()

            assertEquals(expectedRecentConnections, recentConnections)
        }
    }

    @Test
    fun `GIVEN user id WHEN observing most recent connection THEN most recent connection is emitted`() = testScope.runTest {
        val vpnUserId = TestUser.plusUser.vpnUser.userId
        val expectedMostRecentConnection = RecentConnection.UnnamedRecent(
            id = 1,
            isPinned = false,
            connectIntent = createConnectIntentGateway(),
        )
        coEvery { mockRecentsDao.getMostRecentConnection(vpnUserId) } returns flowOf(expectedMostRecentConnection)

        recentsManager.getMostRecentConnection().test {
            val mostRecentConnection = awaitItem()

            assertEquals(expectedMostRecentConnection, mostRecentConnection)
        }
    }

    @Test
    fun `GIVEN user changes WHEN observing recent connections THEN user associated connections are emitted`() =
        testScope.runTest {
            val user1 = TestUser.plusUser.vpnUser
            val user2 = TestUser.freeUser.vpnUser
            val user1Recents = listOf(
                RecentConnection.UnnamedRecent(1, false, createConnectIntentFastestInCountry()),
                RecentConnection.UnnamedRecent(2, true, createConnectIntentFastestInCountry()),
            )
            val user2Recents = listOf(
                RecentConnection.UnnamedRecent(10, false, createConnectIntentFastestInCountry()),
                RecentConnection.UnnamedRecent(20, true, createConnectIntentFastestInCountry()),
                RecentConnection.UnnamedRecent(30, false, createConnectIntentFastestInCountry()),
            )
            coEvery { mockRecentsDao.getRecentsList(user1.userId) } returns flowOf(user1Recents)
            coEvery { mockRecentsDao.getRecentsList(user2.userId) } returns flowOf(user2Recents)

            recentsManager.getRecentsList().test {
                currentUserProvider.vpnUser = user1
                currentUserProvider.vpnUser = user2

                assertEquals(user1Recents, awaitItem())
                assertEquals(user2Recents, awaitItem())
            }
        }

    @Test
    fun `GIVEN recent connection id WHEN getting recent connection THEN recent connection is get`() = testScope.runTest {
        val recentId = 1L

        recentsManager.getRecentById(id = recentId)

        coVerify(exactly = 1) { mockRecentsDao.getById(recentId) }
    }

    @Test
    fun `GIVEN recent connection id WHEN pinning recent connection THEN recent connection is pinned`() = testScope.runTest {
        val recentId = 1L

        recentsManager.pin(recentId)

        coVerify(exactly = 1) { mockRecentsDao.pin(recentId, testScope.currentTime) }
    }

    @Test
    fun `GIVEN recent connection id WHEN unpinning recent connection THEN recent connection is unpinned`() = testScope.runTest {
        val recentId = 1L

        recentsManager.unpin(recentId)

        coVerify(exactly = 1) { mockRecentsDao.unpin(recentId) }
    }

    @Test
    fun `GIVEN recent connection id WHEN removing recent connection THEN recent connection is deleted`() = testScope.runTest {
        val recentId = 1L

        recentsManager.remove(recentId)

        coVerify(exactly = 1) { mockRecentsDao.delete(recentId) }
    }

}
