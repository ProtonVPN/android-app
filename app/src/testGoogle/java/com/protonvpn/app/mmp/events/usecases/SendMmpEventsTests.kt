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
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.AppDatabase.Companion.buildDatabase
import com.protonvpn.android.mmp.FakeIsMmpFeatureFlagEnabled
import com.protonvpn.android.mmp.events.data.MmpEventsDao
import com.protonvpn.android.mmp.events.usecases.SendMmpEvents
import com.protonvpn.android.mmp.referrer.data.MmpReferrerStorage
import com.protonvpn.android.mmp.referrer.usecases.FetchMmpReferrer
import com.protonvpn.android.mmp.referrer.usecases.GetMmpReferrer
import com.protonvpn.app.mmp.events.TestMmpEvent
import com.protonvpn.app.mmp.events.data.TestMmpEventEntity
import com.protonvpn.app.mmp.events.data.TestMmpEventRequestBody
import com.protonvpn.app.mmp.events.data.TestMmpEventResponse
import com.protonvpn.app.mmp.referrer.TestMmpReferrer
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SendMmpEventsTests {

    @MockK
    private lateinit var mockApi: ProtonApiRetroFit

    @MockK
    private lateinit var mockFetchMmpReferrer: FetchMmpReferrer

    private lateinit var isMmpEnabled: FakeIsMmpFeatureFlagEnabled

    private lateinit var mmpEventsDao: MmpEventsDao

    private lateinit var mmpReferrerStorage: MmpReferrerStorage

    private lateinit var testScope: TestScope

    private lateinit var appDatabase: AppDatabase

    private lateinit var sendMmpEvents: SendMmpEvents

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        val context = InstrumentationRegistry.getInstrumentation().targetContext

        appDatabase = Room.inMemoryDatabaseBuilder(
            context = context,
            klass = AppDatabase::class.java,
        ).buildDatabase()

        mmpEventsDao = appDatabase.mmpEventsDao()

        isMmpEnabled = FakeIsMmpFeatureFlagEnabled(enabled = true)

        testScope = TestScope(context = UnconfinedTestDispatcher())

        mmpReferrerStorage = MmpReferrerStorage(
            mainScope = testScope.backgroundScope,
            localDataStoreFactory = InMemoryDataStoreFactory(),
        )

        val getMmpReferrer = GetMmpReferrer(
            isMmpEnabled = isMmpEnabled,
            mmpReferrerStorage = mmpReferrerStorage,
            fetchMmpReferrer = mockFetchMmpReferrer,
        )

        sendMmpEvents = SendMmpEvents(
            isMmpEnabled = isMmpEnabled,
            mmpEventsDao = mmpEventsDao,
            getMmpReferrer = getMmpReferrer,
            mmpReferrerStorage = mmpReferrerStorage,
            api = mockApi,
            context = context,
        )
    }

    @After
    fun tearDown() {
        appDatabase.close()
    }

    @Test
    fun `GIVEN feature flag disabled WHEN sending events THEN send events result succeeds`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = false)
        val mmpEvents = listOf(TestMmpEvent.create())

        val result = sendMmpEvents(mmpEvents = mmpEvents)

        assertTrue(actual = result.result.isSuccess)
    }

    @Test
    fun `GIVEN feature flag disabled WHEN sending events THEN events are not sent`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = false)
        val mmpEvents = listOf(TestMmpEvent.create())

        sendMmpEvents(mmpEvents = mmpEvents)

        coVerify(exactly = 0) { mockApi.postMmpEvents(any()) }
    }

    @Test
    fun `GIVEN feature flag enabled AND no referrer WHEN sending events THEN events are not sent`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = true)
        val mmpEvents = listOf(TestMmpEvent.create())
        coEvery { mockFetchMmpReferrer.getMmpReferrer() } returns null

        sendMmpEvents(mmpEvents = mmpEvents)

        coVerify(exactly = 0) { mockApi.postMmpEvents(any()) }
    }

    @Test
    fun `GIVEN feature flag enabled AND no referrer WHEN sending events THEN send events result succeeds`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = true)
        val mmpEvents = listOf(TestMmpEvent.create())
        coEvery { mockFetchMmpReferrer.getMmpReferrer() } returns null

        val result = sendMmpEvents(mmpEvents = mmpEvents)

        assertTrue(actual = result.result.isSuccess)
    }

    @Test
    fun `GIVEN feature flag enabled AND referrer WHEN sending events succeeds THEN send events result succeeds`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = true)
        val mmpReferrer = TestMmpReferrer.create()
        val mmpEvents = listOf(TestMmpEvent.create())
        val mmpEventRequestBody = TestMmpEventRequestBody.create(
            mmpReferrer = mmpReferrer,
            mmpEvents = mmpEvents,
        )
        mmpReferrerStorage.updateMmpReferrer { mmpReferrer }
        coEvery { mockApi.postMmpEvents(body = mmpEventRequestBody) } returns TestMmpEventResponse.Success.create()

        val result = sendMmpEvents(mmpEvents = mmpEvents)

        assertTrue(actual = result.result.isSuccess)
    }

    @Test
    fun `GIVEN feature flag enabled AND referrer WHEN sending events succeeds THEN events are locally removed`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = true)
        val mmpReferrer = TestMmpReferrer.create()
        val mmpEvents = listOf(
            TestMmpEvent.create(id = 1),
            TestMmpEvent.create(id = 2),
        )
        val mmpEventEntities = mmpEvents.map { mmpEvent -> TestMmpEventEntity.create(event = mmpEvent) }
        val mmpEventRequestBody = TestMmpEventRequestBody.create(
            mmpReferrer = mmpReferrer,
            mmpEvents = mmpEvents,
        )
        mmpReferrerStorage.updateMmpReferrer { mmpReferrer }
        mmpEventEntities.forEach { mmpEventEntity -> mmpEventsDao.insert(entity = mmpEventEntity) }
        coEvery { mockApi.postMmpEvents(body = mmpEventRequestBody) } returns TestMmpEventResponse.Success.create()

        sendMmpEvents(mmpEvents = mmpEvents)

        assertTrue(actual = mmpEventsDao.getAll().isEmpty())
    }

    @Test
    fun `GIVEN feature flag enabled AND referrer WHEN sending events succeeds THEN referrer session start is updated`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = true)
        val mmpReferrer = TestMmpReferrer.create()
        val mmpEvents = listOf(
            TestMmpEvent.create(id = 1),
            TestMmpEvent.create(id = 2),
        )
        val mmpEventEntities = mmpEvents.map { mmpEvent -> TestMmpEventEntity.create(event = mmpEvent) }
        val mmpEventRequestBody = TestMmpEventRequestBody.create(
            mmpReferrer = mmpReferrer,
            mmpEvents = mmpEvents,
        )
        val expectedSessionStartMs = 17585999033
        mmpReferrerStorage.updateMmpReferrer { mmpReferrer }
        mmpEventEntities.forEach { mmpEventEntity -> mmpEventsDao.insert(entity = mmpEventEntity) }
        coEvery {
            mockApi.postMmpEvents(body = mmpEventRequestBody)
        } returns TestMmpEventResponse.Success.create(sessionStartMs = expectedSessionStartMs)

        sendMmpEvents(mmpEvents = mmpEvents)

        assertEquals(
            expected = expectedSessionStartMs,
            actual = mmpReferrerStorage.getMmpReferrer()?.sessionStartTimestamp,
        )
    }

    @Test
    fun `GIVEN feature flag enabled AND referrer WHEN sending events fails THEN send events result fails`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = true)
        val mmpReferrer = TestMmpReferrer.create()
        val mmpEvents = listOf(TestMmpEvent.create())
        val mmpEventRequestBody = TestMmpEventRequestBody.create(
            mmpReferrer = mmpReferrer,
            mmpEvents = mmpEvents,
        )
        mmpReferrerStorage.updateMmpReferrer { mmpReferrer }
        coEvery { mockApi.postMmpEvents(body = mmpEventRequestBody) } returns TestMmpEventResponse.Error.create()

        val result = sendMmpEvents(mmpEvents = mmpEvents)

        assertFalse(actual = result.result.isSuccess)
    }

    @Test
    fun `GIVEN feature flag enabled AND referrer WHEN sending events fails THEN events are not locally removed`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = true)
        val mmpReferrer = TestMmpReferrer.create()
        val mmpEvents = listOf(
            TestMmpEvent.create(id = 1),
            TestMmpEvent.create(id = 2),
        )
        val mmpEventEntities = mmpEvents.map { mmpEvent -> TestMmpEventEntity.create(event = mmpEvent) }
        val mmpEventRequestBody = TestMmpEventRequestBody.create(
            mmpReferrer = mmpReferrer,
            mmpEvents = mmpEvents,
        )
        mmpReferrerStorage.updateMmpReferrer { mmpReferrer }
        mmpEventEntities.forEach { mmpEventEntity -> mmpEventsDao.insert(entity = mmpEventEntity) }
        coEvery { mockApi.postMmpEvents(body = mmpEventRequestBody) } returns TestMmpEventResponse.Error.create()

        sendMmpEvents(mmpEvents = mmpEvents)

        assertEquals(expected = mmpEventEntities, actual = mmpEventsDao.getAll())
    }

}
