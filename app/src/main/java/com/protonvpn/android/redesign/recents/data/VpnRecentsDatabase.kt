/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.redesign.recents.data

import androidx.sqlite.db.SupportSQLiteDatabase
import me.proton.core.data.room.db.migration.DatabaseMigration

interface VpnRecentsDatabase {
    fun recentsDao(): RecentsDao

    fun defaultConnectionDao(): DefaultConnectionDao

    companion object {
        val MIGRATION_0 = object : DatabaseMigration {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                        CREATE TABLE IF NOT EXISTS `defaultConnection` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `connectionType` TEXT NOT NULL,
                        `recentId` INTEGER,
                        FOREIGN KEY(`userId`) REFERENCES `AccountEntity`(`userId`) ON DELETE CASCADE,
                        FOREIGN KEY(`recentId`) REFERENCES `recents`(`id`) ON DELETE CASCADE
                        )
                        """
                )

                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_defaultConnection_userId` ON `defaultConnection` (`userId`)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_defaultConnection_recentId` ON `defaultConnection` (`recentId`)"
                )

                // Migrate previous users to Last connection by default
                database.execSQL(
                    """
                        INSERT INTO defaultConnection (userId, connectionType, recentId)
                        SELECT 
                            userId, 
                            '${ConnectionType.LAST_CONNECTION}' AS connectionType, 
                            NULL AS recentId
                        FROM UserEntity
                        """
                )
            }
        }
    }
}
