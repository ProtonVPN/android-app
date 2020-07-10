/*
 * Copyright (c) 2018 Proton Technologies AG
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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.joda.time.DateTime
import org.joda.time.Interval
import org.joda.time.Period

@Serializable
data class VPNInfo(
    @SerialName(value = "Status") val status: Int,
    @SerialName(value = "ExpirationTime") private val expirationTime: Int,
    @SerialName(value = "PlanName") val tierName: String,
    @SerialName(value = "MaxTier") val maxTier: Int,
    @SerialName(value = "MaxConnect") private val maxConnect: Int,
    @SerialName(value = "Name") val name: String,
    @SerialName(value = "GroupID") val groupId: String,
    @SerialName(value = "Password") val password: String
) : java.io.Serializable {

    val isRemainingTimeAccessible: Boolean
        get() = expirationTime != 0

    val trialRemainingTime: Period
        get() = try {
            val interval =
                    Interval(DateTime(), DateTime(expirationTime * 1000L))
            interval.toPeriod()
        } catch (e: IllegalArgumentException) {
            Period(0, 0, 0, 0)
        }

    // FIXME API should be sending correct information
    fun getMaxConnect(): Int =
        if (tierName == "free") 2 else maxConnect
}
