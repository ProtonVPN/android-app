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

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.protonvpn.android.components.GsonModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class LoginBody(
    @SerialName("Username") val username: String,
    @SerialName("SRPSession") val srpSession: String,
    @SerialName("ClientEphemeral") val clientEphemeral: String,
    @SerialName("ClientProof") val clientProof: String,
    @SerialName("TwoFactorCode") val twoFactorCode: String
) : GsonModel {

    override fun toJson(): JsonObject {
        val jsonString = GsonBuilder().create().toJson(this, LoginBody::class.java)
        val parser = JsonParser()
        return parser.parse(jsonString).asJsonObject
    }
}
