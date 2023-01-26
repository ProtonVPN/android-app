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

package com.protonvpn.android.appconfig.periodicupdates

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity
data class PeriodicCallInfo(
    @PrimaryKey val id: UpdateActionId,
    val timestamp: Long,
    val wasSuccess: Boolean,
    val jitterRatio: Float,
    val nextTimestampOverride: Long?
)

@Dao
interface PeriodicUpdatesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(callInfo: PeriodicCallInfo)

    @Query("SELECT * FROM PeriodicCallInfo")
    suspend fun getAll(): List<PeriodicCallInfo>
}
