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
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import me.proton.core.account.data.entity.AccountEntity
import me.proton.core.domain.entity.UserId

@Entity(
    tableName = "recents",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["isPinned"]),
        Index(value = ["lastConnectionAttemptTimestamp"]),
        Index(value = ["lastPinnedTimestamp"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RecentConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: UserId,
    val isPinned: Boolean,
    val lastConnectionAttemptTimestamp: Long,
    val lastPinnedTimestamp: Long,
    @Embedded
    val connectIntentData: ConnectIntentData
    // Note: whenever adding new fields to this class make sure to update RecentsDao.updateConnectionTimestamp query.
)
