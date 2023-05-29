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

package com.protonvpn.tests.redesign.recents

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.AppDatabase.Companion.buildDatabase
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.data.RecentsDao
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecentsDaoTests {

    private lateinit var recentsDao: RecentsDao

    @Before
    fun setup() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .buildDatabase()
        recentsDao = db.recentsDao()
    }

    @Test
    fun newConnectionsAddedOnTopOfRecents() = runTest {
        val connection1 = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        val connection2 = ConnectIntent.FastestInCountry(CountryId.fastest, emptySet())
        val connection3 = ConnectIntent.FastestInCountry(CountryId.fastest, setOf(ServerFeature.P2P))
        val recents = with(recentsDao) {
            insertOrUpdateForConnection(connection1, 1)
            insertOrUpdateForConnection(connection2, 2)
            insertOrUpdateForConnection(connection3, 3)
            getRecentsList().first()
        }
        assertEquals(listOf(connection3, connection2, connection1), recents.map { it.connectIntent })
    }

    @Test
    fun connectionToExistingRecentMovesItToTop() = runTest {
        val connection1 = ConnectIntent.FastestInCountry(CountryId.fastest, emptySet())
        val connection2 = ConnectIntent.FastestInCountry(CountryId.fastest, setOf(ServerFeature.P2P))
        val connection3 = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        val recents = with(recentsDao) {
            insertOrUpdateForConnection(connection1, 1)
            insertOrUpdateForConnection(connection2, 2)
            insertOrUpdateForConnection(connection3, 3)
            insertOrUpdateForConnection(connection1, 4)
            getRecentsList().first()
        }
        assertEquals(listOf(connection1, connection3, connection2), recents.map { it.connectIntent })
    }

    @Test
    fun pinnedItemsAreReturnedOnTop() = runTest {
        val recent1 = ConnectIntent.FastestInCountry(CountryId.sweden, emptySet())
        val recent2 = ConnectIntent.FastestInCountry(CountryId.fastest, emptySet())
        val pinned = ConnectIntent.Server("server1", emptySet())
        val recents = with(recentsDao) {
            insertOrUpdateForConnection(pinned, 1)
            val pinnedId = getMostRecentConnection().first()!!.id
            pin(pinnedId, 1)
            insertOrUpdateForConnection(recent1, 2)
            insertOrUpdateForConnection(recent2, 3)
            getRecentsList().first()
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
            insertOrUpdateForConnection(pinned, 100)
            val pinnedId = getMostRecentConnection().first()!!.id
            pin(pinnedId, 1)
            insertOrUpdateForConnection(recent, 200)
            insertOrUpdateForConnection(mostRecent, 300)

            unpin(pinnedId)
            getRecentsList().first()
        }
        assertEquals(listOf(mostRecent, pinned, recent), recents.map { it.connectIntent })
        assertEquals(listOf(false, false, false), recents.map { it.isPinned })
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
            recentsDao.insertOrUpdateForConnection(intent, timestamp = index.toLong())
        }
        val recents = recentsDao.getRecentsList().first()
        val recentIntents = recents.map { it.connectIntent }
        assertEquals(connectIntents.reversed(), recentIntents)
    }
}
