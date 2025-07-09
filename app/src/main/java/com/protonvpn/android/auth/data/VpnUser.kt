/*
 * Copyright (c) 2021 Proton AG
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

package com.protonvpn.android.auth.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.protonvpn.android.servers.Server
import me.proton.core.account.data.entity.AccountEntity
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.session.SessionId

@Entity(
    primaryKeys = ["userId"],
    indices = [
        Index("userId")
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
data class VpnUser(
    val userId: UserId,
    val subscribed: Int,
    val services: Int,
    val delinquent: Int,
    @ColumnInfo(defaultValue = "0") val credit: Int,
    @ColumnInfo(defaultValue = "false") val hasPaymentMethod: Boolean,
    val status: Int,
    val expirationTime: Int,
    val planName: String?,
    val planDisplayName: String?,
    val maxTier: Int?,
    val maxConnect: Int,
    val name: String,
    val groupId: String,
    val password: String,
    val updateTime: Long,
    val sessionId: SessionId,
    val autoLoginName: String?,
) {
    val accountType get() = if (services == 4)
        "Proton VPN Account" else "Proton Mail Account"

    val isFreeUser get() = maxTier == FREE_TIER
    val isBasicUser get() = userTier == BASIC_TIER
    val isUserBasicOrAbove get() = userTier >= BASIC_TIER
    val isUserPlusOrAbove get() = userTier >= PLUS_TIER
    val isUserDelinquent get() = delinquent >= 3
    val isPMTeam get() = maxTier == 3

    val hasSubscription get() = subscribed != 0

    val userTier: Int get() = maxTier ?: FREE_TIER
    val userTierName: String get() = planName ?: "free"

    companion object {
        const val FREE_TIER = 0
        const val BASIC_TIER = 1
        const val PLUS_TIER = 2
        const val VPN_SUBSCRIBED_FLAG = 4
    }
}

fun VpnUser?.hasAccessToServer(server: Server?) =
    this != null && server != null && server.tier <= userTier

fun Server?.haveAccessWith(userTier: Int?) =
    userTier != null && this != null && tier <= userTier

fun VpnUser?.hasAccessToAnyServer(servers: List<Server>) =
    servers.any { hasAccessToServer(it) }
