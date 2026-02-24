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

package com.protonvpn.app.mmp.events.data

import com.protonvpn.android.mmp.events.MmpEvent
import com.protonvpn.android.mmp.events.data.MmpEventRequestBody
import com.protonvpn.android.mmp.referrer.MmpReferrer

object TestMmpEventRequestBody {

    fun create(
        asid: String,
        osVersion: String = "12",
        appIdentifier: String = "ch.protonvpn.android",
        appPackageName: String = "ch.protonvpn.android",
        eventTimestampMs: Long = 0L,
        eventType: String = "install",
        installRef: String? = "utm_source=google-play&utm_medium=organic",
        isReinstall: Boolean = false,
        platform: String = "android",
        sessionStartMs: Long? = null,
        contentList: List<String>? = null,
        price: Long? = null,
        currency: String? = null,
        cycle: Int? = null,
        couponCode: String? = null,
        transactionId: String? = null,
        isFirstPurchase: Boolean? = null,
        isFreeToPaid: Boolean? = null,
    ): MmpEventRequestBody = MmpEventRequestBody(
        osVersion = osVersion,
        appIdentifier = appIdentifier,
        appPackageName = appPackageName,
        asid = asid,
        eventTimestampMs = eventTimestampMs,
        eventType = eventType,
        installRef = installRef,
        isReinstall = isReinstall,
        platform = platform,
        sessionStartMs = sessionStartMs,
        contentList = contentList,
        price = price,
        currency = currency,
        cycle = cycle,
        couponCode = couponCode,
        transactionId = transactionId,
        isFirstPurchase = isFirstPurchase,
        isFreeToPaid = isFreeToPaid,
    )

    fun create(mmpReferrer: MmpReferrer, mmpEvent: MmpEvent): MmpEventRequestBody = create(
        asid = mmpReferrer.asid,
        installRef = mmpReferrer.referrerLink,
        eventType = mmpEvent.type.value,
        eventTimestampMs = mmpEvent.timestamp,
        sessionStartMs = mmpEvent.sessionStartTimestamp,
        contentList = mmpEvent.subscriptionDetails?.planName?.let(::listOf),
        price = mmpEvent.subscriptionDetails?.price,
        currency = mmpEvent.subscriptionDetails?.currency,
        cycle = mmpEvent.subscriptionDetails?.cycle,
        couponCode = mmpEvent.subscriptionDetails?.couponCode,
        transactionId = mmpEvent.subscriptionDetails?.transactionId,
        isFirstPurchase = mmpEvent.subscriptionDetails?.isFirstPurchase,
        isFreeToPaid = mmpEvent.subscriptionDetails?.isFreeToPaid,
    )

    fun create(
        mmpReferrer: MmpReferrer,
        mmpEvents: List<MmpEvent>,
    ): List<MmpEventRequestBody> = mmpEvents.map { mmpEvent -> create(mmpReferrer, mmpEvent) }

}
