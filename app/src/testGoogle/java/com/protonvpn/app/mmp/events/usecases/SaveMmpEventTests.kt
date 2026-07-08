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
import com.protonvpn.android.mmp.referrer.usecases.FetchMmpReferrer
import com.protonvpn.android.mmp.referrer.usecases.GetMmpReferrer
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.app.mmp.events.TestMmpEvent
import com.protonvpn.app.mmp.referrer.TestMmpReferrer
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SaveMmpEventTests {

    @MockK
    private lateinit var mockFetchMmpReferrer: FetchMmpReferrer

    private lateinit var appDatabase: AppDatabase

    private lateinit var mmpEventsDao: MmpEventsDao

    private lateinit var mmpReferrerStorage: MmpReferrerStorage

    private lateinit var testScope: TestScope

    private lateinit var localUserSettingsFlow: MutableStateFlow<LocalUserSettings>

    private lateinit var saveMmpEvent: SaveMmpEvent

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        val testDispatcher = UnconfinedTestDispatcher()

        appDatabase = Room.inMemoryDatabaseBuilder(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            klass = AppDatabase::class.java,
        ).buildDatabase()

        mmpEventsDao = appDatabase.mmpEventsDao()

        testScope = TestScope(context = testDispatcher)

        mmpReferrerStorage = MmpReferrerStorage(
            mainScope = testScope.backgroundScope,
            localDataStoreFactory = InMemoryDataStoreFactory(),
        )

        localUserSettingsFlow = MutableStateFlow(value = LocalUserSettings.Default)

        val getMmpReferrer = GetMmpReferrer(
            isMmpEnabled = FakeIsMmpFeatureFlagEnabled(enabled = true),
            mmpReferrerStorage = mmpReferrerStorage,
            fetchMmpReferrer = mockFetchMmpReferrer,
        )

        val userSettings = EffectiveCurrentUserSettings(
            mainScope = testScope.backgroundScope,
            effectiveCurrentUserSettingsFlow = localUserSettingsFlow,
        )

        saveMmpEvent = SaveMmpEvent(
            userSettings = userSettings,
            mmpEventsDao = mmpEventsDao,
            getMmpReferrer = getMmpReferrer,
            mmpReferrerStorage = mmpReferrerStorage,
            now = testScope::currentTime,
        )
    }

    @After
    fun tearDown() {
        appDatabase.close()
    }

    @Test
    fun `GIVEN telemetry is disabled WHEN saving event THEN event is not saved`() = testScope.runTest {
        localUserSettingsFlow.value = LocalUserSettings.Default.copy(telemetry = false)

        saveMmpEvent(eventType = MmpEventType.Install)

        assertTrue(actual = mmpEventsDao.getAll().isEmpty())
    }

    @Test
    fun `GIVEN no referrer WHEN saving event THEN event is saved`() = testScope.runTest {
        val eventType = MmpEventType.Install
        val expectedMmpEvents = listOf(
            TestMmpEvent.create(
                id = 1,
                type = eventType,
                timestamp = currentTime,
                sessionStartTimestamp = null,
            )
        )
        coEvery { mockFetchMmpReferrer.getMmpReferrer() } returns null

        saveMmpEvent(eventType = eventType)

        assertEquals(expected = expectedMmpEvents, actual = mmpEventsDao.getAll().toDomain())
    }

    @Test
    fun `GIVEN referrer WHEN saving event THEN event is saved`() = testScope.runTest {
        val sessionStartTimestamp = 1776180474817
        val mmpReferrer = TestMmpReferrer.create(sessionStartTimestamp = sessionStartTimestamp)
        val eventType = MmpEventType.Install
        advanceTimeBy(delayTimeMillis = sessionStartTimestamp)
        val expectedTruncatedTimestamp = 1776124800000
        val expectedMmpEvents = listOf(
            TestMmpEvent.create(
                id = 1,
                type = eventType,
                timestamp = expectedTruncatedTimestamp,
                sessionStartTimestamp = expectedTruncatedTimestamp,
            )
        )
        mmpReferrerStorage.updateMmpReferrer { mmpReferrer }

        saveMmpEvent(eventType = eventType)

        assertEquals(expected = expectedMmpEvents, actual = mmpEventsDao.getAll().toDomain())
    }

    @Test
    fun `GIVEN referrer WHEN saving event THEN referrer session is not restarted`() = testScope.runTest {
        val sessionStartTimestamp = 162827328732
        val mmpReferrer = TestMmpReferrer.create(sessionStartTimestamp = sessionStartTimestamp)
        val eventType = MmpEventType.Install
        mmpReferrerStorage.updateMmpReferrer { mmpReferrer }

        saveMmpEvent(eventType = eventType)

        assertEquals(
            actual = mmpReferrerStorage.getMmpReferrer()?.sessionStartTimestamp,
            expected = sessionStartTimestamp,
        )
    }

    @Test
    fun `GIVEN referrer AND session restart required WHEN saving event THEN event is saved`() = testScope.runTest {
        val mmpReferrer = TestMmpReferrer.create(sessionStartTimestamp = 179927328711)
        val eventType = MmpEventType.Install
        val expectedMmpEvents = listOf(
            TestMmpEvent.create(
                id = 1,
                type = eventType,
                timestamp = currentTime,
                sessionStartTimestamp = null,
            )
        )
        mmpReferrerStorage.updateMmpReferrer { mmpReferrer }

        saveMmpEvent(eventType = eventType, isSessionRestartRequired = true)

        assertEquals(expected = expectedMmpEvents, actual = mmpEventsDao.getAll().toDomain())
    }

    @Test
    fun `GIVEN referrer AND session restart required WHEN saving event THEN referrer session is restarted`() = testScope.runTest {
        val mmpReferrer = TestMmpReferrer.create(sessionStartTimestamp = 179927328711)
        val eventType = MmpEventType.Install
        mmpReferrerStorage.updateMmpReferrer { mmpReferrer }

        saveMmpEvent(eventType = eventType, isSessionRestartRequired = true)

        assertNull(actual = mmpReferrerStorage.getMmpReferrer()?.sessionStartTimestamp)
    }

}
