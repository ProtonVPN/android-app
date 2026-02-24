package com.protonvpn.app.mmp.events.data

import com.protonvpn.android.mmp.events.MmpEvent
import com.protonvpn.android.mmp.events.data.MmpEventEntity

object TestMmpEventEntity {

    fun create(
        id: Long = 0,
        timestamp: Long = 0L,
        type: MmpEvent.Type = MmpEvent.Type.Install,
        sessionStartTimestamp: Long? = null,
        subscriptionDetails: MmpEvent.SubscriptionDetails? = null,
    ): MmpEventEntity = MmpEventEntity(
        id = id,
        timestamp = timestamp,
        type = type,
        sessionStartTimestamp = sessionStartTimestamp,
        subscriptionDetails = subscriptionDetails,
    )

    fun create(event: MmpEvent): MmpEventEntity = create(
        id = event.id,
        timestamp = event.timestamp,
        type = event.type,
        sessionStartTimestamp = event.sessionStartTimestamp,
        subscriptionDetails = event.subscriptionDetails,
    )

}
