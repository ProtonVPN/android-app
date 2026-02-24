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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MmpEventRequestBody(
    @SerialName("os_version") val osVersion: String,
    @SerialName("app_identifier") val appIdentifier: String,
    @SerialName("app_package_name") val appPackageName: String,
    @SerialName("asid") val asid: String,
    @SerialName("event_timestamp_ms") val eventTimestampMs: Long,
    @SerialName("event_type") val eventType: String,
    @SerialName("install_ref") val installRef: String?,
    @SerialName("is_reinstall") val isReinstall: Boolean,
    @SerialName("platform") val platform: String,
    @SerialName("session_start_ms") val sessionStartMs: Long?,
    @SerialName("content_list") val contentList: List<String>?,
    @SerialName("price") val price: Long?,
    @SerialName("currency") val currency: String?,
    @SerialName("cycle") val cycle: Int?,
    @SerialName("coupon_code") val couponCode: String?,
    @SerialName("transaction_id") val transactionId: String?,
    @SerialName("is_first_purchase") val isFirstPurchase: Boolean?,
    @SerialName("is_free_to_paid") val isFreeToPaid: Boolean?,
)
