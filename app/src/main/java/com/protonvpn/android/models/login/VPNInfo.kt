/*
 * Copyright (c) 2018 Proton AG
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

@Serializable
data class VPNInfo(
    @SerialName(value = "Status") val status: Int,
    @SerialName(value = "ExpirationTime") val expirationTime: Int,
    @SerialName(value = "PlanName") val tierName: String?,
    @SerialName(value = "PlanTitle") val planDisplayName: String?,
    @SerialName(value = "MaxTier") val maxTier: Int?,
    @SerialName(value = "MaxConnect") val maxConnect: Int,
    @SerialName(value = "Name") val name: String,
    @SerialName(value = "GroupID") val groupId: String?,
    @SerialName(value = "Password") val password: String
) : java.io.Serializable {
    val userTierUnknown get() = tierName == null && maxConnect > 1
}
