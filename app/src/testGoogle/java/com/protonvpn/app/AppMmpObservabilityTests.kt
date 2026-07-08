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

package com.protonvpn.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.app.AppMmpObservability
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateSpec
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.AppDatabase.Companion.buildDatabase
import com.protonvpn.android.mmp.FakeIsMmpFeatureFlagEnabled
import com.protonvpn.android.mmp.events.data.MmpEventsDao
import com.protonvpn.android.mmp.events.usecases.GetMmpEvents
import com.protonvpn.android.mmp.events.usecases.ResetMmpEvents
import com.protonvpn.android.mmp.events.usecases.SendMmpEvents
import com.protonvpn.android.mmp.referrer.data.MmpReferrerStorage
import com.protonvpn.android.mmp.referrer.usecases.FetchMmpReferrer
import com.protonvpn.android.mmp.referrer.usecases.GetMmpReferrer
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.app.mmp.events.data.TestMmpEventEntity
import com.protonvpn.app.mmp.referrer.TestMmpReferrer
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppMmpObservabilityTests {

    @MockK
    private lateinit var mockApi: ProtonApiRetroFit

    @MockK
    private lateinit var mockFetchMmpReferrer: FetchMmpReferrer

    @RelaxedMockK
    private lateinit var mockPeriodicUpdateManager: PeriodicUpdateManager

    private lateinit var appDatabase: AppDatabase

    private lateinit var isMmpEnabled: FakeIsMmpFeatureFlagEnabled

    private lateinit var mmpEventsDao: MmpEventsDao

    private lateinit var mmpReferrerStorage: MmpReferrerStorage

    private lateinit var localUserSettingsFlow: MutableStateFlow<LocalUserSettings>

    private lateinit var testScope: TestScope

    private lateinit var appMmpObservability: AppMmpObservability

    private val expectedPeriodicUpdateSpec = PeriodicUpdateSpec(
        intervalMs = 86_400_000,
        conditions = emptySet(),
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val testDispatcher = UnconfinedTestDispatcher()

        val testExecutor = testDispatcher.asExecutor()

        Dispatchers.setMain(dispatcher = testDispatcher)

        appDatabase = Room.inMemoryDatabaseBuilder(
            context = context,
            klass = AppDatabase::class.java,
        )
            .setQueryExecutor(executor = testExecutor)
            .setTransactionExecutor(executor = testExecutor)
            .buildDatabase()

        isMmpEnabled = FakeIsMmpFeatureFlagEnabled(enabled = true)

        testScope = TestScope(context = testDispatcher)

        mmpEventsDao = appDatabase.mmpEventsDao()

        localUserSettingsFlow = MutableStateFlow(value = LocalUserSettings.Default)

        mmpReferrerStorage = MmpReferrerStorage(
            mainScope = testScope.backgroundScope,
            localDataStoreFactory = InMemoryDataStoreFactory(),
        )

        val resetMmpEvents = ResetMmpEvents(
            mmpEventsDao = mmpEventsDao,
            mmpReferrerStorage = mmpReferrerStorage,
        )

        val userSettings = EffectiveCurrentUserSettings(
            mainScope = testScope.backgroundScope,
            effectiveCurrentUserSettingsFlow = localUserSettingsFlow,
        )

        val getMmpReferrer = GetMmpReferrer(
            isMmpEnabled = isMmpEnabled,
            mmpReferrerStorage = mmpReferrerStorage,
            fetchMmpReferrer = mockFetchMmpReferrer,
        )

        val sendMmpEvents = SendMmpEvents(
            isMmpEnabled = isMmpEnabled,
            mmpEventsDao = mmpEventsDao,
            getMmpReferrer = getMmpReferrer,
            mmpReferrerStorage = mmpReferrerStorage,
            api = mockApi,
            context = context,
        )

        val getMmpEvents = GetMmpEvents(
            isMmpEnabled = isMmpEnabled,
            mmpEventsDao = mmpEventsDao,
        )

        appMmpObservability = AppMmpObservability(
            isMmpFeatureFlagEnabled = isMmpEnabled,
            userSettings = userSettings,
            periodicUpdateManager = { mockPeriodicUpdateManager },
            sendMmpEvents = { sendMmpEvents },
            getMmpEvents = { getMmpEvents },
            resetMmpEvents = { resetMmpEvents },
            mainScope = testScope.backgroundScope,
        )
    }

    @After
    fun tearDown() {
        appDatabase.close()

        Dispatchers.resetMain()
    }

    @Test
    fun `GIVEN feature flag is disabled WHEN observability starts THEN send events action is not registered`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = false)

        appMmpObservability.start()

        verify(exactly = 0) {
            mockPeriodicUpdateManager.registerUpdateAction<Unit, Unit>(
                action = match { it.id == "update_mmp_events" },
                updateSpec = arrayOf(expectedPeriodicUpdateSpec),
            )
        }
    }

    @Test
    fun `GIVEN feature flag is disabled WHEN observability starts THEN send events action is unregistered`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = false)

        appMmpObservability.start()

        verify(exactly = 1) {
            mockPeriodicUpdateManager.unregister(action = match { it.id == "update_mmp_events" })
        }
    }

    @Test
    fun `GIVEN feature flag is disabled AND pending events WHEN observability starts THEN pending events are deleted`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = false)
        val mmpEventEntity = TestMmpEventEntity.create()
        mmpEventsDao.insert(entity = mmpEventEntity)

        appMmpObservability.start()

        assertTrue(actual = mmpEventsDao.getAll().isEmpty())
    }

    @Test
    fun `GIVEN telemetry is disabled WHEN observability starts THEN send events action is not registered`() = testScope.runTest {
        localUserSettingsFlow.value = LocalUserSettings.Default.copy(telemetry = false)

        appMmpObservability.start()

        verify(exactly = 0) {
            mockPeriodicUpdateManager.registerUpdateAction<Unit, Unit>(
                action = match { it.id == "update_mmp_events" },
                updateSpec = arrayOf(expectedPeriodicUpdateSpec),
            )
        }
    }

    @Test
    fun `GIVEN telemetry is disabled WHEN observability starts THEN send events action is unregistered`() = testScope.runTest {
        localUserSettingsFlow.value = LocalUserSettings.Default.copy(telemetry = false)

        appMmpObservability.start()

        verify(exactly = 1) {
            mockPeriodicUpdateManager.unregister(action = match { it.id == "update_mmp_events" })
        }
    }

    @Test
    fun `GIVEN telemetry is disabled AND pending events WHEN observability starts THEN pending events are deleted`() = testScope.runTest {
        val mmpEventEntity = TestMmpEventEntity.create()
        localUserSettingsFlow.value = LocalUserSettings.Default.copy(telemetry = false)
        mmpEventsDao.insert(entity = mmpEventEntity)

        appMmpObservability.start()

        assertTrue(actual = mmpEventsDao.getAll().isEmpty())
    }

    @Test
    fun `GIVEN telemetry is disabled WHEN observability starts THEN referrer session is restarted`() = testScope.runTest {
        val mmpReferrer = TestMmpReferrer.create(sessionStartTimestamp = 123456789L)
        localUserSettingsFlow.value = LocalUserSettings.Default.copy(telemetry = false)
        mmpReferrerStorage.updateMmpReferrer { mmpReferrer }

        appMmpObservability.start()

        assertNull(actual = mmpReferrerStorage.getMmpReferrer()?.sessionStartTimestamp)
    }

    @Test
    fun `GIVEN feature flag AND telemetry are enabled WHEN observability starts THEN send events action is registered`() = testScope.runTest {
        isMmpEnabled.setEnabled(isEnabled = true)
        localUserSettingsFlow.value = LocalUserSettings.Default.copy(telemetry = true)

        appMmpObservability.start()

        verify(exactly = 1) {
            mockPeriodicUpdateManager.registerUpdateAction<Unit, Unit>(
                action = match { it.id == "update_mmp_events" },
                updateSpec = arrayOf(expectedPeriodicUpdateSpec),
            )
        }
    }

}
