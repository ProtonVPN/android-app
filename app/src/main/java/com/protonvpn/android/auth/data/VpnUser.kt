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

package com.protonvpn.android.auth.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.CASCADE
import androidx.room.Index
import com.protonvpn.android.models.vpn.Server
import me.proton.core.account.data.entity.AccountEntity
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.session.SessionId
import org.joda.time.DateTime
import org.joda.time.Days
import org.joda.time.Interval
import org.joda.time.Period

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
            onDelete = CASCADE
        )
    ]
)
data class VpnUser(
    val userId: UserId,
    val subscribed: Int,
    val services: Int,
    val delinquent: Int,
    val status: Int,
    val expirationTime: Int,
    val planName: String?,
    val maxTier: Int?,
    val maxConnect: Int,
    val name: String,
    val groupId: String,
    val password: String,
    val updateTime: Long,
    val sessionId: SessionId
) {
    val accountType get() = if (services == 4)
        "ProtonVPN Account" else "ProtonMail Account"

    val isFreeUser get() = maxTier == 0
    val isTrialUser get() = planName == "trial"
    val isBasicUser get() = userTier == 1
    val isUserPlusOrAbove get() = userTier > 1
    val isUserDelinquent get() = delinquent >= 3

    val userTier: Int get() = maxTier ?: 0
    val userTierName: String get() = planName ?: "free"

    val isRemainingTimeAccessible: Boolean
        get() = expirationTime != 0

    fun isTrialExpired(): Boolean = DateTime(expirationTime * 1000L).isBeforeNow

    val trialRemainingTime: Period get() =
        if (!isRemainingTimeAccessible)
            Days.days(7).toPeriod()
        else try {
            val interval =
                Interval(DateTime(), DateTime(expirationTime * 1000L))
            interval.toPeriod()
        } catch (e: IllegalArgumentException) {
            Period(0, 0, 0, 0)
        }
}

fun VpnUser?.hasAccessToServer(server: Server?) =
    this != null && server != null && server.tier <= userTier

fun VpnUser?.hasAccessToAnyServer(servers: List<Server>) =
    servers.any { hasAccessToServer(it) }
