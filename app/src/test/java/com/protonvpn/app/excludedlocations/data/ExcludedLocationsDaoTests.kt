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

package com.protonvpn.app.excludedlocations.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.AppDatabase.Companion.buildDatabase
import com.protonvpn.android.excludedlocations.data.ExcludedLocationsDao
import com.protonvpn.app.excludedlocations.TestExcludedLocationEntity
import com.protonvpn.testsHelper.AccountTestHelper
import com.protonvpn.testsHelper.AccountTestHelper.Companion.TestAccount1
import com.protonvpn.testsHelper.AccountTestHelper.Companion.TestSession1
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ExcludedLocationsDaoTests {

    private val userId = TestAccount1.userId.id

    private lateinit var accountTestHelper: AccountTestHelper

    private lateinit var appDatabase: AppDatabase

    private lateinit var excludedLocationsDao: ExcludedLocationsDao

    @Before
    fun setup() {
        appDatabase = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).buildDatabase()

        accountTestHelper = AccountTestHelper()

        accountTestHelper.withAccountManager(appDatabase) { accountManager ->
            accountManager.addAccount(
                account = TestAccount1,
                session = TestSession1,
            )
        }

        excludedLocationsDao = appDatabase.excludedLocationsDao()
    }

    @Test
    fun `WHEN inserting an excluded location THEN excluded location is saved`() = runTest {
        val excludedLocationEntity = TestExcludedLocationEntity.create(id = 1L, userId = userId)
        val expectedExcludedLocationEntities = listOf(excludedLocationEntity)

        excludedLocationsDao.insert(entity = excludedLocationEntity)

        val excludedLocationEntities = excludedLocationsDao
            .observeAll(userId = excludedLocationEntity.userId)
            .first()

        assertEquals(expectedExcludedLocationEntities, excludedLocationEntities)
    }

    @Test
    fun `WHEN deleting an excluded location THEN excluded location is removed`() = runTest {
        val excludedLocationEntity1 = TestExcludedLocationEntity.create(
            id = 1L,
            userId = userId,
            countryCode = "AU",
        )
        val excludedLocationEntity2 = TestExcludedLocationEntity.create(
            id = 2L,
            userId = userId,
            countryCode = "JP",
        )
        val expectedExcludedLocationEntities = listOf(excludedLocationEntity2)
        excludedLocationsDao.insert(entity = excludedLocationEntity1)
        excludedLocationsDao.insert(entity = excludedLocationEntity2)

        excludedLocationsDao.delete(entity = excludedLocationEntity1)

        val excludedLocationEntities = excludedLocationsDao.observeAll(userId = userId).first()
        assertEquals(expectedExcludedLocationEntities, excludedLocationEntities)
    }

    @Test
    fun `GIVEN excluded location already exists WHEN inserting the excluded location THEN conflict is resolved`() = runTest {
        val excludedLocationEntity = TestExcludedLocationEntity.create(id = 1L, userId = userId)
        val expectedExcludedLocationEntities = listOf(excludedLocationEntity)

        excludedLocationsDao.insert(entity = excludedLocationEntity)
        excludedLocationsDao.insert(entity = excludedLocationEntity)

        val excludedLocationEntities = excludedLocationsDao.observeAll(userId = userId).first()
        assertEquals(expectedExcludedLocationEntities, excludedLocationEntities)
    }

}
