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

package com.protonvpn.app.redesign.recents

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.AppDatabase.Companion.buildDatabase
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.data.ConnectionType
import com.protonvpn.android.redesign.recents.data.DefaultConnectionDao
import com.protonvpn.android.redesign.recents.data.DefaultConnectionEntity
import com.protonvpn.android.redesign.recents.data.RecentConnectionEntity
import com.protonvpn.android.redesign.recents.data.RecentsDao
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.testsHelper.AccountTestHelper
import com.protonvpn.testsHelper.AccountTestHelper.Companion.TestAccount1
import com.protonvpn.testsHelper.AccountTestHelper.Companion.TestAccount2
import com.protonvpn.testsHelper.AccountTestHelper.Companion.TestSession1
import com.protonvpn.testsHelper.AccountTestHelper.Companion.TestSession2
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.proton.core.domain.entity.UserId
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecentsDaoTests {

    private lateinit var recentsDao: RecentsDao
    private lateinit var defaultConnectionDao: DefaultConnectionDao

    private val intentFastest = ConnectIntent.FastestInCountry(CountryId.fastest, emptySet())
    private val intentSweden = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
    private val intentIceland = ConnectIntent.FastestInCountry(CountryId.iceland, emptySet())

    private val userId1 = TestAccount1.userId
    private val userId2 = TestAccount2.userId

    @Before
    fun setup() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .buildDatabase()

        val accountHelper = AccountTestHelper()
        accountHelper.withAccountManager(db) { accountManager ->
            accountManager.addAccount(TestAccount1, TestSession1)
            accountManager.addAccount(TestAccount2, TestSession2)
        }

        recentsDao = db.recentsDao()
        defaultConnectionDao = db.defaultConnectionDao()
    }

    @Test
    fun defaultConnectionIsDeletedAlongsideRecent() = runTest {
        val connection = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        recentsDao.insert(listOf(RecentConnectionEntity(2, userId1, false, 0,0, connection.toData())))
        defaultConnectionDao.insert(DefaultConnectionEntity(userId = userId1.id, connectionType = ConnectionType.RECENT, recentId = 2))
        recentsDao.delete(2)
        assertEquals(null, defaultConnectionDao.getDefaultConnectionFlow(userId1).first())
    }

    @Test
    fun newConnectionsAddedOnTopOfRecents() = runTest {
        val connection1 = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        val connection2 = ConnectIntent.FastestInCountry(CountryId.fastest, emptySet())
        val connection3 = ConnectIntent.FastestInCountry(CountryId.fastest, setOf(ServerFeature.P2P))
        insertIntents(userId1, connection1 to 1, connection2 to 2, connection3 to 3)

        val recents = recentsDao.getRecentsList(userId1).first()
        assertEquals(listOf(connection3, connection2, connection1), recents.map { it.connectIntent })
    }

    @Test
    fun connectionToExistingRecentMovesItToTop() = runTest {
        val connection1 = ConnectIntent.FastestInCountry(CountryId.fastest, emptySet())
        val connection2 = ConnectIntent.FastestInCountry(CountryId.fastest, setOf(ServerFeature.P2P))
        val connection3 = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        insertIntents(userId1, connection1 to 1, connection2 to 2, connection3 to 3)
        recentsDao.insertOrUpdateForConnection(userId1, connection1, 4)

        val recents = recentsDao.getRecentsList(userId1).first()
        assertEquals(listOf(connection1, connection3, connection2), recents.map { it.connectIntent })
    }

    @Test
    fun pinnedItemsAreReturnedOnTop() = runTest {
        val recent1 = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        val recent2 = ConnectIntent.FastestInCountry(CountryId.fastest, emptySet())
        val pinned = ConnectIntent.Server("server1", emptySet())
        val recents = with(recentsDao) {
            insertOrUpdateForConnection(userId1, pinned, 1)
            val pinnedId = getMostRecentConnection(userId1).first()!!.id
            pin(pinnedId, 1)
            insertOrUpdateForConnection(userId1, recent1, 2)
            insertOrUpdateForConnection(userId1, recent2, 3)
            getRecentsList(userId1).first()
        }
        assertEquals(listOf(pinned, recent2, recent1), recents.map { it.connectIntent })
        assertEquals(listOf(true, false, false), recents.map { it.isPinned })
    }

    @Test
    fun unpinningMovesItemToSecondMostRecent() = runTest {
        val recent = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        val mostRecent = ConnectIntent.FastestInCountry(CountryId.fastest, emptySet())
        val pinned = ConnectIntent.Server("server1", emptySet())
        val recents = with(recentsDao) {
            insertOrUpdateForConnection(userId1, pinned, 100)
            val pinnedId = getMostRecentConnection(userId1).first()!!.id
            pin(pinnedId, 1)
            insertOrUpdateForConnection(userId1, recent, 200)
            insertOrUpdateForConnection(userId1, mostRecent, 300)

            unpin(pinnedId)
            getRecentsList(userId1).first()
        }
        assertEquals(listOf(mostRecent, pinned, recent), recents.map { it.connectIntent })
        assertEquals(listOf(false, false, false), recents.map { it.isPinned })
    }

    @Test
    fun unpinningRecentlyConnectedRecentKeepsItFirst() = runTest{
        val recentlyConnected = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        val other = ConnectIntent.FastestInCountry(CountryId.fastest, emptySet())
        val recents = with(recentsDao) {
            // Setup
            insertOrUpdateForConnection(userId1, other, 100)
            val otherId = getMostRecentConnection(userId1).first()!!.id
            insertOrUpdateForConnection(userId1, recentlyConnected, 200)
            val recentlyConnectedId = getMostRecentConnection(userId1).first()!!.id
            pin(otherId, 1000)
            pin(recentlyConnectedId, 2000)

            // Simulate connection
            insertOrUpdateForConnection(userId1, recentlyConnected, 3000)
            // Unpin them to see if it breaks the ordering
            unpin(recentlyConnectedId)
            unpin(otherId)

            getRecentsList(userId1).first()
        }
        assertEquals(listOf(recentlyConnected, other), recents.map { it.connectIntent })
    }

    @Test
    fun connectionHistoryUpdatedSeparatelyForEachUser() = runTest {
        insertIntents(userId1, intentFastest to 100, intentSweden to 200, intentIceland to 300)
        insertIntents(userId2, intentSweden to 100, intentIceland to 200, intentFastest to 300)

        assertEquals(
            listOf(intentIceland, intentSweden, intentFastest),
            recentsDao.getRecentsList(userId1).first().map { it.connectIntent }
        )
        assertEquals(
            listOf(intentFastest, intentIceland, intentSweden),
            recentsDao.getRecentsList(userId2).first().map { it.connectIntent }
        )

        // Update affects only user 1's recents
        recentsDao.insertOrUpdateForConnection(userId1, intentSweden, 1000)
        assertEquals(
            listOf(intentSweden, intentIceland, intentFastest),
            recentsDao.getRecentsList(userId1).first().map { it.connectIntent }
        )
        assertEquals(
            listOf(intentFastest, intentIceland, intentSweden),
            recentsDao.getRecentsList(userId2).first().map { it.connectIntent }
        )
    }

    @Test
    fun recentsLimitEnforcedForEachUserSeparately() = runTest {
        insertIntents(userId1, intentFastest to 100, intentSweden to 200, intentIceland to 300)
        insertIntents(userId2, intentSweden to 100, intentIceland to 200, intentFastest to 300)

        recentsDao.deleteExcessUnpinnedRecents(userId1, 2)
        val user1Recents = recentsDao.getRecentsList(userId1).first()
        val user2Recents = recentsDao.getRecentsList(userId2).first()
        assertEquals(2, user1Recents.size)
        assertEquals(3, user2Recents.size)
    }

    @Test
    fun allConnectIntentTypesAreCorrectlySavedAndLoaded() = runTest {
        val connectIntents = listOf(
            ConnectIntent.FastestInCountry(CountryId.fastest, emptySet()),
            ConnectIntent.FastestInCountry(CountryId("de"), emptySet()),
            ConnectIntent.FastestInCountry(CountryId("de"), setOf(ServerFeature.P2P)),
            ConnectIntent.FastestInCity(CountryId("pl"), "Warsaw", emptySet()),
            ConnectIntent.SecureCore(CountryId.fastest, CountryId.fastest),
            ConnectIntent.SecureCore(CountryId("lt"), CountryId.fastest),
            ConnectIntent.SecureCore(CountryId("lt"), CountryId.sweden),
            ConnectIntent.Server("server1", emptySet()),
            ConnectIntent.Server("server1", setOf(ServerFeature.Tor))
        )
        connectIntents.forEachIndexed { index, intent ->
            recentsDao.insertOrUpdateForConnection(userId1, intent, timestamp = index.toLong())
        }
        val recents = recentsDao.getRecentsList(userId1).first()
        val recentIntents = recents.map { it.connectIntent }
        assertEquals(connectIntents.reversed(), recentIntents)
    }

    private suspend fun insertIntents(userId: UserId, vararg intentsWithTime: Pair<ConnectIntent, Long>) {
        intentsWithTime.forEach {
            recentsDao.insertOrUpdateForConnection(userId, it.first, it.second)
        }
    }
}
