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

package com.protonvpn.android.db

import androidx.sqlite.db.SupportSQLiteDatabase
import me.proton.core.data.room.db.extension.recreateTable

object MigrateToNewProfiles {

    private val oldConnectIntentDataColumns = mapOf(
        "connectIntentType" to "TEXT NOT NULL",
        "exitCountry" to "TEXT",
        "entryCountry" to "TEXT",
        "city" to "TEXT",
        "region" to "TEXT",
        "gatewayName" to "TEXT",
        "serverId" to "TEXT",
        "features" to "TEXT NOT NULL",
    )

    private val connectIntentDataColumns = oldConnectIntentDataColumns + mapOf(
        "profileId" to "INTEGER",
        "netShield" to "TEXT",
        "randomizedNat" to "INTEGER",
        "lanConnections" to "INTEGER",
        "vpn" to "TEXT",
        "transmission" to "TEXT",
    )

    private val connectIntentDataColumnsSpec =
        connectIntentDataColumns.entries.joinToString(",\n") { (name, type) -> "`$name` $type" }

    fun migrate(db: SupportSQLiteDatabase) {
        db.createUnnamedRecentsIntentsTable()
        db.copyIntentDataToUnnamedRecentsIntents()
        db.createProfilesTable()
        db.recreateRecentsTable()
    }

    private fun SupportSQLiteDatabase.copyIntentDataToUnnamedRecentsIntents() {
        val oldIntentColumns = oldConnectIntentDataColumns.keys.joinToString(", ")
        execSQL("""
            INSERT INTO `unnamedRecentsIntents` (`recentId`, $oldIntentColumns)
            SELECT `id`, $oldIntentColumns
            FROM `recents`""".trimIndent()
        )
    }

    private fun SupportSQLiteDatabase.createUnnamedRecentsIntentsTable() {
        execSQL("""
            CREATE TABLE IF NOT EXISTS `unnamedRecentsIntents` (
                `recentId` INTEGER NOT NULL,
                $connectIntentDataColumnsSpec,
                PRIMARY KEY(`recentId`), FOREIGN KEY(`recentId`) REFERENCES `recents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent()
        )
    }

    private fun SupportSQLiteDatabase.createProfilesTable() {
        execSQL("""
            CREATE TABLE IF NOT EXISTS `profiles` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `userId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `color` TEXT NOT NULL,
                `icon` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                $connectIntentDataColumnsSpec,
                FOREIGN KEY(`userId`) REFERENCES `AccountEntity`(`userId`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent()
        )
        execSQL("CREATE INDEX IF NOT EXISTS `index_profiles_userId` ON `profiles` (`userId`)")
    }

    private fun SupportSQLiteDatabase.recreateRecentsTable() {
        recreateTable(
            table = "recents",
            createTable = {
                execSQL("""
                    CREATE TABLE IF NOT EXISTS `recents` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `isPinned` INTEGER NOT NULL,
                        `lastConnectionAttemptTimestamp` INTEGER NOT NULL,
                        `lastPinnedTimestamp` INTEGER NOT NULL,
                        `profileId` INTEGER,
                        FOREIGN KEY(`userId`) REFERENCES `AccountEntity`(`userId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent()
                )
            },
            createIndices = {
                execSQL("CREATE INDEX IF NOT EXISTS `index_recents_userId` ON `recents` (`userId`)")
                execSQL("CREATE INDEX IF NOT EXISTS `index_recents_isPinned` ON `recents` (`isPinned`)")
                execSQL("CREATE INDEX IF NOT EXISTS `index_recents_lastConnectionAttemptTimestamp` ON `recents` (`lastConnectionAttemptTimestamp`)")
                execSQL("CREATE INDEX IF NOT EXISTS `index_recents_lastPinnedTimestamp` ON `recents` (`lastPinnedTimestamp`)")
                execSQL("CREATE INDEX IF NOT EXISTS `index_recents_profileId` ON `recents` (`profileId`)")
            },
            oldColumns = listOf("`id`", "`userId`", "`isPinned`", "`lastConnectionAttemptTimestamp`", "`lastPinnedTimestamp`"),
            newColumns = listOf("`id`", "`userId`", "`isPinned`", "`lastConnectionAttemptTimestamp`", "`lastPinnedTimestamp`"),
        )
    }
}