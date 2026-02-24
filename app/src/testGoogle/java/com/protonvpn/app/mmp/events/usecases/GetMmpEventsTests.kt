/*
 * Copyright (c) 2026 Proton AG
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

package com.protonvpn.app.mmp.events.usecases

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.AppDatabase.Companion.buildDatabase
import com.protonvpn.android.mmp.FakeIsMmpFeatureFlagEnabled
import com.protonvpn.android.mmp.events.data.MmpEventsDao
import com.protonvpn.android.mmp.events.usecases.GetMmpEvents
import com.protonvpn.app.mmp.events.TestMmpEvent
import com.protonvpn.app.mmp.events.data.TestMmpEventEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GetMmpEventsTests {

    private lateinit var appDatabase: AppDatabase

    private lateinit var isMmpEnabled: FakeIsMmpFeatureFlagEnabled

    private lateinit var mmpEventsDao: MmpEventsDao

    private lateinit var testScope: TestScope

    private lateinit var getMmpEvents: GetMmpEvents

    @Before
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher()

        appDatabase = Room.inMemoryDatabaseBuilder(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            klass = AppDatabase::class.java,
        )
            .setQueryExecutor(executor = testDispatcher.asExecutor())
            .setTransactionExecutor(executor = testDispatcher.asExecutor())
            .allowMainThreadQueries()
            .buildDatabase()

        isMmpEnabled = FakeIsMmpFeatureFlagEnabled(enabled = true)

        mmpEventsDao = appDatabase.mmpEventsDao()

        testScope = TestScope(context = testDispatcher)

        getMmpEvents = GetMmpEvents(
            isMmpEnabled = isMmpEnabled,
            mmpEventsDao = mmpEventsDao,
        )
    }

    @After
    fun tearDown() {
        appDatabase.close()
    }

    @Test
    fun `GIVEN feature flag is enabled AND locally stored events WHEN observing events THEN events are emitted sorted by timestamp`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = true)
        val mmpEventEntity1 = TestMmpEventEntity.create(id = 1, timestamp = 2)
        val mmpEventEntity2 = TestMmpEventEntity.create(id = 2, timestamp = 1)
        val expectedMmpEvents = listOf(
            TestMmpEvent.create(entity = mmpEventEntity2),
            TestMmpEvent.create(entity = mmpEventEntity1),
        )
        mmpEventsDao.insert(entity = mmpEventEntity1)
        mmpEventsDao.insert(entity = mmpEventEntity2)

        assertEquals(expected = expectedMmpEvents, actual = getMmpEvents())
    }

    @Test
    fun `GIVEN feature flag is disabled AND locally stored events WHEN observing events THEN empty events are emitted`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = false)
        val mmpEventEntity = TestMmpEventEntity.create()
        mmpEventsDao.insert(entity = mmpEventEntity)

        assertTrue(actual = getMmpEvents().isEmpty())
    }

    @Test
    fun `GIVEN feature flag is enabled AND no locally stored events WHEN observing events THEN empty events are emitted`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = true)

        assertTrue(actual = getMmpEvents().isEmpty())
    }

    @Test
    fun `GIVEN feature flag is disabled AND no locally stored events WHEN observing events THEN empty events are emitted`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = false)

        assertTrue(actual = getMmpEvents().isEmpty())
    }

}
