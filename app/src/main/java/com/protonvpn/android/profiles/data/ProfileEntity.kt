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

package com.protonvpn.android.profiles.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.protonvpn.android.redesign.recents.data.ConnectIntentData
import me.proton.core.account.data.entity.AccountEntity
import me.proton.core.domain.entity.UserId

@Entity(
    tableName = "profiles",
    indices = [
        Index(value = ["userId"]),
    ],
    // ConnectIntentData.profileId is used as primary key to avoid duplication. It'll still be
    // auto-generated upon insertion (with unique values but without sqlite AUTOINCREMENT).
    primaryKeys = ["profileId"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
    ]
)
data class ProfileEntity(
    val userId: UserId,
    val name: String,
    val color: ProfileColor,
    val icon: ProfileIcon,
    @ColumnInfo(defaultValue = "")
    val autoOpenText: String,
    @ColumnInfo(defaultValue = "0")
    val autoOpenEnabled: Boolean,
    @ColumnInfo(defaultValue = "0")
    val autoOpenUrlPrivately: Boolean,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "NULL")
    val lastConnectedAt: Long?,
    @ColumnInfo(defaultValue = "1")
    val isUserCreated: Boolean,
    @Embedded
    val connectIntentData: ConnectIntentData
)

enum class ProfileIcon {
    Icon1, Icon2, Icon3, Icon4, Icon5, Icon6, Icon7, Icon8, Icon9, Icon10, Icon11, Icon12
}

enum class ProfileColor {
    Color1, Color2, Color3, Color4, Color5, Color6
}
