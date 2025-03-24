/*
 * Copyright (c) 2017 Proton AG
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
class LoginInfoResponse(
    @SerialName(value = "Code") val code: Int,
    @SerialName(value = "Modulus") val modulus: String,
    @SerialName(value = "ServerEphemeral") val serverEphemeral: String,
    @SerialName(value = "Version") private val version: Int,
    @SerialName(value = "Salt") val salt: String,
    @SerialName(value = "SRPSession") val srpSession: String
) {
    fun getVersion(): Long = version.toLong()
}
