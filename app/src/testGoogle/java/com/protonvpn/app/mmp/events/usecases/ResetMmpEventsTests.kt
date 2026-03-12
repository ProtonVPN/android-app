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
import com.protonvpn.android.mmp.events.usecases.ResetMmpEvents
import com.protonvpn.android.mmp.referrer.data.MmpReferrerStorage
import com.protonvpn.app.mmp.events.data.TestMmpEventEntity
import com.protonvpn.app.mmp.referrer.TestMmpReferrer
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ResetMmpEventsTests {

    private lateinit var appDatabase: AppDatabase

    private lateinit var isMmpEnabled: FakeIsMmpFeatureFlagEnabled

    private lateinit var mmpEventsDao: MmpEventsDao

    private lateinit var mmpReferrerStorage: MmpReferrerStorage

    private lateinit var testScope: TestScope

    private lateinit var resetMmpEvents: ResetMmpEvents

    @Before
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher()

        testScope = TestScope(context = testDispatcher)

        appDatabase = Room.inMemoryDatabaseBuilder(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            klass = AppDatabase::class.java,
        ).buildDatabase()

        mmpEventsDao = appDatabase.mmpEventsDao()

        mmpReferrerStorage = MmpReferrerStorage(
            mainScope = testScope.backgroundScope,
            localDataStoreFactory = InMemoryDataStoreFactory(),
        )

        isMmpEnabled = FakeIsMmpFeatureFlagEnabled(enabled = true)

        resetMmpEvents = ResetMmpEvents(
            mmpEventsDao = mmpEventsDao,
            mmpReferrerStorage = mmpReferrerStorage,
        )
    }

    @After
    fun tearDown() {
        appDatabase.close()
    }

    @Test
    fun `GIVEN locally stored events WHEN resetting events THEN events are removed`() = testScope.runTest {
        mmpEventsDao.insert(entity = TestMmpEventEntity.create())

        resetMmpEvents()

        assertTrue(actual = mmpEventsDao.getAll().isEmpty())
    }

    @Test
    fun `GIVEN referrer WHEN resetting events THEN referrer session is restarted`() = testScope.runTest {
        val mmpReferrer = TestMmpReferrer.create(sessionStartTimestamp = 179927328711)
        mmpReferrerStorage.updateMmpReferrer { mmpReferrer }

        resetMmpEvents()

        assertNull(actual = mmpReferrerStorage.getMmpReferrer()?.sessionStartTimestamp)
    }

}
