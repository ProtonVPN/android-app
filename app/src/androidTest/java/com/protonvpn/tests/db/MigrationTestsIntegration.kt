/*
 *
 *  * Copyright (c) 2023. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.tests.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.DatabaseMigrations
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.profiles.data.Profile
import com.protonvpn.android.profiles.data.ProfileAutoOpen
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.data.ProfileInfo
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.data.ProtocolSelectionData
import com.protonvpn.android.redesign.recents.data.RecentConnection
import com.protonvpn.android.redesign.recents.data.SettingsOverrides
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.settings.data.CustomDnsSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import me.proton.core.domain.entity.UserId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.EnumSet
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MigrationTestsIntegration {

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
    fun migrateRecentsAndProfile() {
        val unnamedRecentInserts = listOf(
            """
                INSERT INTO recents (id, userId, isPinned, lastConnectionAttemptTimestamp, lastPinnedTimestamp, profileId)
                             VALUES (1,   'id1',        1,                             10,                  11,      NULL)
            """.trimMargin(),
            """
                INSERT INTO unnamedRecentsIntents (recentId, connectIntentType, exitCountry, entryCountry, city, region, gatewayName, serverId, features, profileId)
                                           VALUES (1,                'FASTEST',        'CH',         NULL, NULL,   NULL,        NULL,     NULL,     'P2P',     NULL)
            """.trimIndent()
        )
        val unnamedRecentExpected = RecentConnection.UnnamedRecent(
            id = 1L,
            isPinned = true,
            connectIntent = ConnectIntent.FastestInCountry(
                country = CountryId("CH"),
                features = EnumSet.of(ServerFeature.P2P),
                profileId = null,
                settingsOverrides = null,
            )
        )

        val profileRecentsInserts = listOf(
            """
                INSERT INTO profiles (profileId, userId,       name,    color,    icon, createdAt, connectIntentType, exitCountry, entryCountry,     city, region, gatewayName, serverId, features,  netShield, randomizedNat, lanConnections,         vpn, transmission)
                              VALUES (        1,  'id1', 'profile1', 'Color2', 'Icon3',      1000, 'FASTEST_IN_CITY',        'CH',         NULL, 'Zurich',   NULL,        NULL,     NULL,       '', 'DISABLED',             0,              1, 'WireGuard',        'UDP')
            """.trimMargin(),
            """
                INSERT INTO recents (id, userId, isPinned, lastConnectionAttemptTimestamp, lastPinnedTimestamp, profileId)
                             VALUES (10,  'id1',        0,                             20,                   0,         1)
            """.trimIndent()
        )
        val profileExpected = Profile(
            userId = UserId("id1"),
            info = ProfileInfo(
                id = 1,
                name = "profile1",
                color = ProfileColor.Color2,
                icon = ProfileIcon.Icon3,
                createdAt = 1000L,
                isUserCreated = true,
                lastConnectedAt = null,
            ),
            autoOpen = ProfileAutoOpen.None,
            connectIntent = ConnectIntent.FastestInCity(
                country = CountryId("CH"),
                cityEn = "Zurich",
                features = emptySet(),
                profileId = 1,
                settingsOverrides = SettingsOverrides(
                    protocolData = ProtocolSelectionData(VpnProtocol.WireGuard, TransmissionProtocol.UDP),
                    netShield = NetShieldProtocol.DISABLED,
                    randomizedNat = false,
                    lanConnections = true,
                    lanConnectionsAllowDirect = false,
                    customDns = CustomDnsSettings(toggleEnabled = false, rawDnsList = emptyList()),
                )
            )
        )
        val profileRecentExpected = RecentConnection.ProfileRecent(id = 10, isPinned = false, profile = profileExpected)

        // Unnamed recent is pinned, so it's first even though the profile recent has larger
        // lastConnectionAttemptTimestamp.
        val expectedRecents = listOf(unnamedRecentExpected, profileRecentExpected)


        helper.createDatabase(dbName, 37).use { db ->
            val allInserts: List<String> = unnamedRecentInserts + profileRecentsInserts
            allInserts.forEach { db.execSQL(it) }
        }
        val roomDb: AppDatabase = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            dbName
        )
            .addMigrations(*AppDatabase.migrations.toTypedArray())
            .build()
        try {
            val recentsDao = roomDb.recentsDao()
            runBlocking {
                val recents = recentsDao.getRecentsList(UserId("id1")).first()

                assertEquals(expectedRecents, recents)
            }
        } finally {
            // Doesn't implement Closeable so we can't use `use`.
            roomDb.close()
        }
    }

    @Test
    fun migrateRecentData35to37() = runTest {
        // Insert recent to 35
        val db = helper.createDatabase(dbName, 35)
        db.execSQL("""
            INSERT INTO recents (id, userId, isPinned, lastConnectionAttemptTimestamp, lastPinnedTimestamp, connectIntentType, exitCountry, entryCountry, city,            region,       gatewayName, serverId, features)
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
