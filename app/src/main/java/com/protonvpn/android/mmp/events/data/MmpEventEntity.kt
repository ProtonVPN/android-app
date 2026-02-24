/*
 * Copyright (c) 2026 Proton AG
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

package com.protonvpn.android.mmp.events.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.protonvpn.android.mmp.events.MmpEvent
import com.protonvpn.android.mmp.events.MmpEventType

@Entity(tableName = "mmpEvents")
data class MmpEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: MmpEvent.Type,
    val sessionStartTimestamp: Long?,
    @Embedded val subscriptionDetails: MmpEvent.SubscriptionDetails?,
)

fun List<MmpEventEntity>.toDomain(): List<MmpEvent> = mapNotNull(MmpEventEntity::toDomain)

private fun MmpEventEntity.toDomain(): MmpEvent? = when (type) {
    MmpEvent.Type.Install -> MmpEventType.Install
    MmpEvent.Type.Open -> MmpEventType.Open
    MmpEvent.Type.Subscription -> subscriptionDetails?.let(MmpEventType::Subscription)
}?.let { eventType ->
    MmpEvent(
        timestamp = timestamp,
        sessionStartTimestamp = sessionStartTimestamp,
        eventType = eventType,
    )
}

fun List<MmpEvent>.toEntities(): List<MmpEventEntity> = map(MmpEvent::toEntity)

fun MmpEvent.toEntity(): MmpEventEntity = MmpEventEntity(
    timestamp = timestamp,
    type = type,
    sessionStartTimestamp = sessionStartTimestamp,
    subscriptionDetails = subscriptionDetails,
)
