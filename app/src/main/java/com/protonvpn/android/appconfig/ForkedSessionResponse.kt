/*
 * Copyright (c) 2019 Proton AG
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
package com.protonvpn.android.appconfig

import com.protonvpn.android.models.login.LoginResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.core.network.domain.session.SessionId

@Serializable
class ForkedSessionResponse(
    @SerialName(value = "ExpiresIn") val expiresIn: Int,
    @SerialName(value = "TokenType") val tokenType: String,
    @SerialName(value = "UID") val uid: String,
    @SerialName(value = "RefreshToken") val refreshToken: String,
    @SerialName(value = "Payload") val payload: String?,
    @SerialName(value = "LocalID") val localId: Int,
    @SerialName(value = "Scopes") val scopes: Array<String>,
    @SerialName(value = "UserID") val userId: String
) {
    fun toLoginResponse(accessToken: String): LoginResponse =
        LoginResponse(accessToken, expiresIn, tokenType, scopes.joinToString(" "), uid, refreshToken, userId)

    val sessionId get() = SessionId(uid)
}
