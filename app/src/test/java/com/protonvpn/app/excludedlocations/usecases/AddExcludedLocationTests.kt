/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.app.excludedlocations.usecases

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.AppDatabase.Companion.buildDatabase
import com.protonvpn.android.models.login.toVpnUserEntity
import com.protonvpn.android.excludedlocations.ExcludedLocations
import com.protonvpn.android.excludedlocations.usecases.AddExcludedLocation
import com.protonvpn.android.excludedlocations.usecases.ObserveExcludedLocations
import com.protonvpn.android.redesign.settings.FakeIsAutomaticConnectionPreferencesFeatureFlagEnabled
import com.protonvpn.app.excludedlocations.TestExcludedLocation
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createAccountUser
import com.protonvpn.testsHelper.AccountTestHelper
import com.protonvpn.testsHelper.AccountTestHelper.Companion.TestAccount1
import com.protonvpn.testsHelper.AccountTestHelper.Companion.TestSession1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
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
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AddExcludedLocationTests {

    private lateinit var testUserProvider: TestCurrentUserProvider

    private lateinit var observeExcludedLocations: ObserveExcludedLocations

    private lateinit var addExcludedLocation: AddExcludedLocation

    private lateinit var testScope: TestScope

    @Before
    fun setUp() {
        val testDispatcher = UnconfinedTestDispatcher()

        Dispatchers.setMain(dispatcher = testDispatcher)

        testScope = TestScope(context = testDispatcher)

        val db = Room.inMemoryDatabaseBuilder(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            klass = AppDatabase::class.java,
        )
            .setQueryExecutor(executor = testDispatcher.asExecutor())
            .setTransactionExecutor(executor = testDispatcher.asExecutor())
            .allowMainThreadQueries()
            .buildDatabase()

        AccountTestHelper().withAccountManager(db) { accountManager ->
            accountManager.addAccount(
                account = TestAccount1,
                session = TestSession1,
            )
        }

        testUserProvider = TestCurrentUserProvider(
            vpnUser = null,
            user = createAccountUser(),
        )

        val currentUser = CurrentUser(provider = testUserProvider)

        val excludedLocationsDao = db.excludedLocationsDao()

        observeExcludedLocations = ObserveExcludedLocations(
            mainScope = testScope.backgroundScope,
            currentUser = currentUser,
            excludedLocationsDao = excludedLocationsDao,
            isAutomaticConnectionEnabled = FakeIsAutomaticConnectionPreferencesFeatureFlagEnabled(enabled = true),
        )

        addExcludedLocation = AddExcludedLocation(
            currentUser = currentUser,
            excludedLocationsDao = excludedLocationsDao,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `GIVEN there is no vpn user WHEN adding excluded location THEN no excluded location is added`() = testScope.runTest {
        testUserProvider.vpnUser = null
        val expectedExcludedLocations = ExcludedLocations(allLocations = emptyList())

        addExcludedLocation(excludedLocation = TestExcludedLocation.create())

        observeExcludedLocations().test {
            val excludedLocations = awaitItem()

            assertEquals(expectedExcludedLocations, excludedLocations)
        }
    }

    @Test
    fun `GIVEN free vpn user WHEN adding excluded location THEN no excluded location is added`() = testScope.runTest {
        testUserProvider.vpnUser = TestUser.freeUser.vpnUser
        val expectedExcludedLocations = ExcludedLocations(allLocations = emptyList())

        addExcludedLocation(excludedLocation = TestExcludedLocation.create())

        observeExcludedLocations().test {
            val excludedLocations = awaitItem()

            assertEquals(expectedExcludedLocations, excludedLocations)
        }
    }

    @Test
    fun `GIVEN paid vpn user WHEN adding excluded location THEN excluded location is added`() = testScope.runTest {
        testUserProvider.vpnUser = TestUser.plusUser
            .vpnInfoResponse
            .toVpnUserEntity(
                userId = TestAccount1.userId,
                sessionId = TestSession1.sessionId,
                timestamp = 0L,
                autoLoginId = null,
            )
        val excludedLocation = TestExcludedLocation.create()
        val expectedExcludedLocations = ExcludedLocations(
            allLocations = listOf(
                TestExcludedLocation.create(id = 1L),
            )
        )

        addExcludedLocation(excludedLocation = excludedLocation)

        observeExcludedLocations().test {
            val excludedLocations = awaitItem()

            assertEquals(expectedExcludedLocations, excludedLocations)
        }
    }

}
