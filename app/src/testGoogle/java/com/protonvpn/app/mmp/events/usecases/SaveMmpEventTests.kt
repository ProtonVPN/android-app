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
import com.protonvpn.android.mmp.events.MmpEventType
import com.protonvpn.android.mmp.events.data.MmpEventsDao
import com.protonvpn.android.mmp.events.data.toDomain
import com.protonvpn.android.mmp.events.usecases.SaveMmpEvent
import com.protonvpn.android.mmp.referrer.data.MmpReferrerStorage
import com.protonvpn.app.mmp.events.TestMmpEvent
import com.protonvpn.app.mmp.referrer.TestMmpReferrer
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import me.proton.core.network.domain.server.ServerClock
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SaveMmpEventTests {

    @MockK
    private lateinit var mockServerClock: ServerClock

    private lateinit var appDatabase: AppDatabase

    private lateinit var isMmpEnabled: FakeIsMmpFeatureFlagEnabled

    private lateinit var mmpEventsDao: MmpEventsDao

    private lateinit var mmpReferrerStorage: MmpReferrerStorage

    private lateinit var testScope: TestScope

    private lateinit var saveMmpEvent: SaveMmpEvent

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        val testDispatcher = UnconfinedTestDispatcher()

        appDatabase = Room.inMemoryDatabaseBuilder(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            klass = AppDatabase::class.java,
        ).buildDatabase()

        isMmpEnabled = FakeIsMmpFeatureFlagEnabled(enabled = true)

        mmpEventsDao = appDatabase.mmpEventsDao()

        testScope = TestScope(context = testDispatcher)

        mmpReferrerStorage = MmpReferrerStorage(
            mainScope = testScope.backgroundScope,
            localDataStoreFactory = InMemoryDataStoreFactory(),
        )

        saveMmpEvent = SaveMmpEvent(
            isMmpEnabled = isMmpEnabled,
            mmpEventsDao = mmpEventsDao,
            mmpReferrerStorage = mmpReferrerStorage,
            serverClock = mockServerClock,
        )
    }

    @After
    fun tearDown() {
        appDatabase.close()
    }

    @Test
    fun `GIVEN feature flag is disabled WHEN saving event THEN event is not saved`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = false)

        saveMmpEvent(eventType = MmpEventType.Install)

        assertTrue(actual = mmpEventsDao.getAll().isEmpty())
    }

    @Test
    fun `GIVEN feature flag is enabled AND no referrer WHEN saving event THEN event is saved`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = true)
        val eventType = MmpEventType.Install
        val expectedMmpEvents = listOf(
            TestMmpEvent.create(
                type = eventType,
                timestamp = currentTime,
                sessionStartTimestamp = null,
            )
        )
        every { mockServerClock.getCurrentTime() } returns Instant.ofEpochMilli(currentTime)

        saveMmpEvent(eventType = eventType)

        assertEquals(expected = expectedMmpEvents, actual = mmpEventsDao.getAll().toDomain())
    }

    @Test
    fun `GIVEN feature flag is enabled AND referrer WHEN saving event THEN event is saved`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = true)
        val sessionStartTimestamp = 162827328732
        val mmpReferrer = TestMmpReferrer.create(sessionStartTimestamp = sessionStartTimestamp)
        val eventType = MmpEventType.Install
        val expectedMmpEvents = listOf(
            TestMmpEvent.create(
                type = eventType,
                timestamp = currentTime,
                sessionStartTimestamp = sessionStartTimestamp,
            )
        )
        mmpReferrerStorage.setMmpReferrer(mmpReferrer = mmpReferrer)
        every { mockServerClock.getCurrentTime() } returns Instant.ofEpochMilli(currentTime)

        saveMmpEvent(eventType = eventType)

        assertEquals(expected = expectedMmpEvents, actual = mmpEventsDao.getAll().toDomain())
    }

    @Test
    fun `GIVEN feature flag is enabled AND referrer WHEN saving event THEN referrer session is not restarted`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = true)
        val sessionStartTimestamp = 162827328732
        val mmpReferrer = TestMmpReferrer.create(sessionStartTimestamp = sessionStartTimestamp)
        val eventType = MmpEventType.Install
        mmpReferrerStorage.setMmpReferrer(mmpReferrer = mmpReferrer)
        every { mockServerClock.getCurrentTime() } returns Instant.ofEpochMilli(currentTime)

        saveMmpEvent(eventType = eventType)

        assertEquals(
            actual = mmpReferrerStorage.getMmpReferrer()?.sessionStartTimestamp,
            expected = sessionStartTimestamp,
        )
    }

    @Test
    fun `GIVEN feature flag is enabled AND referrer AND session restart required WHEN saving event THEN event is saved`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = true)
        val mmpReferrer = TestMmpReferrer.create(sessionStartTimestamp = 179927328711)
        val eventType = MmpEventType.Install
        val expectedMmpEvents = listOf(
            TestMmpEvent.create(
                type = eventType,
                timestamp = currentTime,
                sessionStartTimestamp = null,
            )
        )
        mmpReferrerStorage.setMmpReferrer(mmpReferrer = mmpReferrer)
        every { mockServerClock.getCurrentTime() } returns Instant.ofEpochMilli(currentTime)

        saveMmpEvent(eventType = eventType, isSessionRestartRequired = true)

        assertEquals(expected = expectedMmpEvents, actual = mmpEventsDao.getAll().toDomain())
    }

    @Test
    fun `GIVEN feature flag is enabled AND referrer AND session restart required WHEN saving event THEN referrer session is restarted`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = true)
        val mmpReferrer = TestMmpReferrer.create(sessionStartTimestamp = 179927328711)
        val eventType = MmpEventType.Install
        mmpReferrerStorage.setMmpReferrer(mmpReferrer = mmpReferrer)
        every { mockServerClock.getCurrentTime() } returns Instant.ofEpochMilli(currentTime)

        saveMmpEvent(eventType = eventType, isSessionRestartRequired = true)

        assertNull(actual = mmpReferrerStorage.getMmpReferrer()?.sessionStartTimestamp)
    }

}
