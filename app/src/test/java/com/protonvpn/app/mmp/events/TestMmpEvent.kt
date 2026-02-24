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

package com.protonvpn.app.mmp.events

import com.protonvpn.android.mmp.events.MmpEvent
import com.protonvpn.android.mmp.events.MmpEventType
import com.protonvpn.android.mmp.events.data.MmpEventEntity

object TestMmpEvent {

    fun create(
        id: Long = 0L,
        timestamp: Long = 0L,
        sessionStartTimestamp: Long? = null,
        type: MmpEventType = MmpEventType.Install,
    ): MmpEvent = MmpEvent(
        id = id,
        timestamp = timestamp,
        sessionStartTimestamp = sessionStartTimestamp,
        eventType = type,
    )

    fun create(entity: MmpEventEntity): MmpEvent = create(
        id = entity.id,
        timestamp = entity.timestamp,
        sessionStartTimestamp = entity.sessionStartTimestamp,
        type = when(entity.type) {
            MmpEvent.Type.Install -> MmpEventType.Install
            MmpEvent.Type.Open -> MmpEventType.Open
            MmpEvent.Type.Subscription -> MmpEventType.Subscription(subscriptionDetails = entity.subscriptionDetails!!)
        },
    )

}
