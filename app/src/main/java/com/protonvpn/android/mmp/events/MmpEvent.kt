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

package com.protonvpn.android.mmp.events

import kotlinx.serialization.Serializable

data class MmpEvent(
    val timestamp: Long,
    val sessionStartTimestamp: Long?,
    private val eventType: MmpEventType,
) {

    enum class Type(val value: String) {
        Install(value = "install"),
        Open(value = "open"),
        Subscription(value = "sub"),
    }

    @Serializable
    data class SubscriptionDetails(
        val price: Long,
        val currency: String,
        val cycle: Int,
        val planName: String,
        val couponCode: String?,
        val transactionId: String?,
        val isFirstPurchase: Boolean?,
        val isFreeToPaid: Boolean?,
    )

    val type: Type = eventType.type

    val subscriptionDetails: SubscriptionDetails? = eventType.subscriptionDetails

}

sealed class MmpEventType {

    open val subscriptionDetails: MmpEvent.SubscriptionDetails? = null

    abstract val type: MmpEvent.Type

    data object Install : MmpEventType() {

        override val type: MmpEvent.Type = MmpEvent.Type.Install

    }

    data object Open : MmpEventType() {

        override val type: MmpEvent.Type = MmpEvent.Type.Open

    }

    data class Subscription(
        override val subscriptionDetails: MmpEvent.SubscriptionDetails,
    ) : MmpEventType() {

        override val type: MmpEvent.Type = MmpEvent.Type.Subscription

    }

}
