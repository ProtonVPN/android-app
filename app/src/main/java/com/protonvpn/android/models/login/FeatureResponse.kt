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
class FeatureResponse(
    @SerialName(value = "Code") val code: Int,
    @SerialName(value = "Feature") val feature: Feature,
)

@Serializable
class Feature(
    @SerialName(value = "Code") val code: String,
    @SerialName(value = "Type") val type: String,
    @SerialName(value = "Minimum") val minimum: Float = 0f,
    @SerialName(value = "Maximum") val maximum: Float = 0f,
    @SerialName(value = "Global") val global: Boolean,
    @SerialName(value = "Writable") val writable: Boolean,
    @SerialName(value = "DefaultValue") val defaultValue: Boolean,
    @SerialName(value = "Value") val value: Boolean,
    @SerialName(value = "ExpirationTime") val expirationTime: Long = 0L,
    @SerialName(value = "UpdateTime") val updateTime: Long = 0L,
)
