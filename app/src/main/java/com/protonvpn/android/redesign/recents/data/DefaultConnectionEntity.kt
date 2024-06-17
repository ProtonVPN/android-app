/*
 * Copyright (c) 2024. Proton AG
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

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import me.proton.core.account.data.entity.AccountEntity


@Entity(
    tableName = "defaultConnection",
    indices = [
        Index(value = ["userId"], unique = true),
        Index(value = ["recentId"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RecentConnectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recentId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DefaultConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val connectionType: ConnectionType,
    val recentId: Long?,
)

enum class ConnectionType {
    FASTEST, LAST_CONNECTION, RECENT
}

sealed class DefaultConnection(
) {
    data object FastestConnection : DefaultConnection()
    data object LastConnection : DefaultConnection()
    data class Recent(val recentId: Long) : DefaultConnection()

}
fun DefaultConnection.getRecentIdOrNull(): Long? = (this as? DefaultConnection.Recent)?.recentId
fun DefaultConnectionEntity.toDefaultConnection(): DefaultConnection {
   return when (connectionType) {
        ConnectionType.FASTEST -> DefaultConnection.FastestConnection
        ConnectionType.LAST_CONNECTION -> DefaultConnection.LastConnection
        ConnectionType.RECENT -> DefaultConnection.Recent(recentId!!)
    }
}
