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
import com.protonvpn.android.excludedlocations.data.ExcludedLocationsDao
import com.protonvpn.android.excludedlocations.data.toEntity
import com.protonvpn.android.excludedlocations.usecases.ObserveExcludedLocations
import com.protonvpn.android.excludedlocations.usecases.RemoveExcludedLocation
import com.protonvpn.app.excludedlocations.TestExcludedLocation
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createAccountUser
import com.protonvpn.testsHelper.AccountTestHelper
import com.protonvpn.testsHelper.AccountTestHelper.Companion.TestAccount1
import com.protonvpn.testsHelper.AccountTestHelper.Companion.TestSession1
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class RemoveExcludedLocationTests {

    private lateinit var testUserProvider: TestCurrentUserProvider

    private lateinit var excludedLocationsDao: ExcludedLocationsDao

    private lateinit var observeExcludedLocations: ObserveExcludedLocations

    private lateinit var removeExcludedLocation: RemoveExcludedLocation

    @Before
    fun setUp() {
        val db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).buildDatabase()

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

        excludedLocationsDao = db.excludedLocationsDao()

        observeExcludedLocations = ObserveExcludedLocations(
            currentUser = currentUser,
            excludedLocationsDao = excludedLocationsDao,
        )

        removeExcludedLocation = RemoveExcludedLocation(
            currentUser = currentUser,
            excludedLocationsDao = excludedLocationsDao,
        )
    }

    @Test
    fun `GIVEN there is no vpn user WHEN removing excluded location THEN no excluded location is removed`() = runTest {
        testUserProvider.vpnUser = null
        val excludedLocation = TestExcludedLocation.create()
        val expectedExcludedLocations = ExcludedLocations(allLocations = emptyList())
        excludedLocationsDao.insert(entity = excludedLocation.toEntity(userId = TestAccount1.userId))

        removeExcludedLocation(excludedLocation = excludedLocation)

        observeExcludedLocations().test {
            val excludedLocations = awaitItem()

            assertEquals(expectedExcludedLocations, excludedLocations)
        }
    }

    @Test
    fun `GIVEN free vpn user WHEN removing excluded location THEN no excluded location is removed`() = runTest {
        val freeVpnUser = TestUser.freeUser.vpnUser
        testUserProvider.vpnUser = freeVpnUser
        val excludedLocation = TestExcludedLocation.create()
        val expectedExcludedLocations = ExcludedLocations(allLocations = emptyList())
        excludedLocationsDao.insert(entity = excludedLocation.toEntity(userId = TestAccount1.userId))

        removeExcludedLocation(excludedLocation = TestExcludedLocation.create())

        observeExcludedLocations().test {
            val excludedLocations = awaitItem()

            assertEquals(expectedExcludedLocations, excludedLocations)
        }
    }

    @Test
    fun `GIVEN paid vpn user WHEN removing excluded location THEN excluded location is removed`() = runTest {
        val plusVpnUser = TestUser.plusUser
            .vpnInfoResponse
            .toVpnUserEntity(
                userId = TestAccount1.userId,
                sessionId = TestSession1.sessionId,
                timestamp = 0L,
                autoLoginId = null,
            )
        testUserProvider.vpnUser = plusVpnUser
        val excludedLocation1 = TestExcludedLocation.create(id = 1L)
        val excludedLocation2 = TestExcludedLocation.create(id = 2L)
        val expectedExcludedLocations = ExcludedLocations(allLocations = listOf(excludedLocation2))
        excludedLocationsDao.insert(entity = excludedLocation1.toEntity(userId = plusVpnUser.userId))
        excludedLocationsDao.insert(entity = excludedLocation2.toEntity(userId = plusVpnUser.userId))

        removeExcludedLocation(excludedLocation = excludedLocation1)

        observeExcludedLocations().test {
            val excludedLocations = awaitItem()

            assertEquals(expectedExcludedLocations, excludedLocations)
        }
    }

}
