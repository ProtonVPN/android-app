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
import com.protonvpn.android.redesign.recents.data.ConnectIntentData
import com.protonvpn.android.redesign.recents.data.ConnectIntentType
import com.protonvpn.android.redesign.recents.data.RecentConnectionEntity
import com.protonvpn.android.redesign.recents.data.RecentConnectionWithIntent
import com.protonvpn.android.redesign.recents.data.UnnamedRecentIntentEntity
import com.protonvpn.android.redesign.vpn.ServerFeature
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.proton.core.domain.entity.UserId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals


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
    fun migrateRecentData35to36() = runTest {
        // Insert recent to 35
        helper.createDatabase(dbName, 35).apply {
            execSQL("""INSERT INTO recents (id, userId, isPinned, lastConnectionAttemptTimestamp, lastPinnedTimestamp, connectIntentType, exitCountry, entryCountry, city,            region,       gatewayName, serverId, features)
                                    VALUES (1,  'id1',  1,        10,                             11,                  'FASTEST',         'US',        'CH',         'San Francisco', 'California', 'Gateway',   '12',     'P2P')""")
            close()
        }

        // Migrate
        helper.runMigrationsAndValidate(
            dbName, 36, true, DatabaseMigrations.MIGRATION_35_36)

        // Check recent is in migrated db
        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            dbName
        ).build()

        val result = db.recentsDao().getRecentsEntityList(UserId("id1")).first()
        assertEquals(
            listOf(
                RecentConnectionWithIntent(
                    recent = RecentConnectionEntity(
                        id = 1,
                        userId = UserId("id1"),
                        isPinned = true,
                        lastConnectionAttemptTimestamp = 10,
                        lastPinnedTimestamp = 11,
                    ),
                    profile = null,
                    unnamedRecent = UnnamedRecentIntentEntity(
                        recentId = 1,
                        connectIntentData = ConnectIntentData(
                            connectIntentType = ConnectIntentType.FASTEST,
                            exitCountry = "US",
                            entryCountry = "CH",
                            city = "San Francisco",
                            region = "California",
                            gatewayName = "Gateway",
                            serverId = "12",
                            features = setOf(ServerFeature.P2P),
                            profileId = null,
                            settingsOverrides = null,
                        )
                    )
                )
            ),
            result
        )
    }
}