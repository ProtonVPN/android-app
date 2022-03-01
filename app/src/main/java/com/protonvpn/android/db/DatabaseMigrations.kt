/*
 * Copyright (c) 2021 Proton Technologies AG
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

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.proton.core.account.data.db.AccountDatabase
import me.proton.core.featureflag.data.db.FeatureFlagDatabase
import me.proton.core.user.data.db.AddressDatabase
import me.proton.core.user.data.db.UserDatabase
import me.proton.core.usersettings.data.db.OrganizationDatabase

object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            AccountDatabase.MIGRATION_4.migrate(database)
            AddressDatabase.MIGRATION_3.migrate(database)
            UserDatabase.MIGRATION_1.migrate(database)
        }
    }
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            FeatureFlagDatabase.MIGRATION_0.migrate(database)
            FeatureFlagDatabase.MIGRATION_1.migrate(database)
            OrganizationDatabase.MIGRATION_1.migrate(database)
        }
    }
}
