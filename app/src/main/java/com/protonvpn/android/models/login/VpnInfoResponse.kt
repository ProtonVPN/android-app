/*
 * Copyright (c) 2017 Proton Technologies AG
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
package com.protonvpn.android.models.login

import android.content.Context
import com.protonvpn.android.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.joda.time.Days

@Serializable
data class VpnInfoResponse(
    @SerialName(value = "Code") val code: Int,
    @SerialName(value = "VPN") val vpnInfo: VPNInfo,
    @SerialName(value = "Subscribed") private val subscribed: Int,
    @SerialName(value = "Services") private val services: Int,
    @SerialName(value = "Delinquent") private val delinquent: Int
) : java.io.Serializable {

    val accountType = if (services == 4)
        "ProtonVPN Account" else "ProtonMail Account"

    val password: String get() = vpnInfo.password
    val maxSessionCount: Int get() = vpnInfo.getMaxConnect()
    val vpnUserName: String get() = vpnInfo.name

    val isUserDelinquent: Boolean
        get() = delinquent >= 3

    fun hasAccessToTier(serverTier: Int) = userTier >= serverTier

    val userTierName: String get() = vpnInfo.tierName ?: "free"

    val userTier: Int get() = vpnInfo.maxTier ?: 0

    val isTrialExpired get() = vpnInfo.isTrialExpired()

    fun getTrialRemainingTimeString(context: Context): String {
        val period = if (vpnInfo.isRemainingTimeAccessible)
            vpnInfo.trialRemainingTime else Days.days(7).toPeriod()
        val resources = context.resources
        val days = resources.getQuantityString(R.plurals.counter_days, period.days, period.days)
        val hours = resources.getQuantityString(R.plurals.counter_hours, period.hours, period.hours)
        val minutes = resources.getQuantityString(R.plurals.counter_minutes, period.minutes, period.minutes)
        val seconds = resources.getQuantityString(R.plurals.counter_seconds, period.seconds, period.seconds)
        return "$days $hours $minutes $seconds"
    }
}
