/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.tests.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.DatabaseMigrations
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue


@RunWith(AndroidJUnit4::class)
class MigrationTests {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun allMigrationsWithoutData() {
        helper.createDatabase(dbName, 1).apply {
            close()
        }
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            dbName
        ).addMigrations(*AppDatabase.migrations.toTypedArray()).build().apply {
            openHelper.writableDatabase.close()
        }
    }

    @Test
    fun migrateRecentData35to37() = runTest {
        // Insert recent to 35
        val db = helper.createDatabase(dbName, 35)
        db.execSQL("""INSERT INTO recents (id, userId, isPinned, lastConnectionAttemptTimestamp, lastPinnedTimestamp, connectIntentType, exitCountry, entryCountry, city,            region,       gatewayName, serverId, features)
                                    VALUES (1,  'id1',  1,        10,                             11,                  'FASTEST',         'US',        'CH',         'San Francisco', 'California', 'Gateway',   '12',     'P2P')""")

        // Migrate
        helper.runMigrationsAndValidate(
            dbName, 37, true, DatabaseMigrations.MIGRATION_35_36, DatabaseMigrations.MIGRATION_36_37)

        db.query("SELECT * FROM recents").apply {
            moveToFirst()
            assertEquals(1, getInt(getColumnIndex("id")))
            assertEquals("id1", getString(getColumnIndex("userId")))
            assertEquals(1, getInt(getColumnIndex("isPinned")))
            assertEquals(10, getLong(getColumnIndex("lastConnectionAttemptTimestamp")))
            assertEquals(11, getLong(getColumnIndex("lastPinnedTimestamp")))
            assertTrue(isLast)
            close()
        }

        db.query("SELECT * FROM unnamedRecentsIntents").apply {
            moveToFirst()
            assertEquals("FASTEST", getString(getColumnIndex("connectIntentType")))
            assertEquals("US", getString(getColumnIndex("exitCountry")))
            assertEquals("CH", getString(getColumnIndex("entryCountry")))
            assertEquals("San Francisco", getString(getColumnIndex("city")))
            assertEquals("California", getString(getColumnIndex("region")))
            assertEquals("Gateway", getString(getColumnIndex("gatewayName")))
            assertEquals("12", getString(getColumnIndex("serverId")))
            assertEquals("P2P", getString(getColumnIndex("features")))
            assertTrue(isLast)
            close()
        }

        db.close()
    }
}