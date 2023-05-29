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

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recents",
    indices = [
        Index(value = ["isPinned"]),
        Index(value = ["lastConnectionAttemptTimestamp"]),
        Index(value = ["lastPinnedTimestamp"])
    ]
)
data class RecentConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isPinned: Boolean,
    val lastConnectionAttemptTimestamp: Long,
    val lastPinnedTimestamp: Long,
    @Embedded
    val connectIntentData: ConnectIntentData
    // Note: whenever adding new fields to this class make sure to update RecentsDao.updateConnectionTimestamp query.
)
