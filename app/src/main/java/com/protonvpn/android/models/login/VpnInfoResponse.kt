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

import com.protonvpn.android.auth.data.VpnUser
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.session.SessionId
import org.joda.time.DateTime

@Serializable
data class VpnInfoResponse(
    @SerialName(value = "Code") val code: Int,
    @SerialName(value = "VPN") val vpnInfo: VPNInfo,
    @SerialName(value = "Subscribed") val subscribed: Int,
    @SerialName(value = "Services") val services: Int,
    @SerialName(value = "Delinquent") val delinquent: Int
) : java.io.Serializable

fun VpnInfoResponse.toVpnUserEntity(userId: UserId, sessionId: SessionId) =
    VpnUser(
        userId = userId,
        subscribed = subscribed,
        services = services,
        delinquent = delinquent,
        status = vpnInfo.status,
        expirationTime = vpnInfo.expirationTime,
        planName = vpnInfo.tierName,
        planDisplayName = vpnInfo.planDisplayName,
        maxTier = vpnInfo.maxTier,
        maxConnect = vpnInfo.maxConnect,
        name = vpnInfo.name,
        groupId = vpnInfo.groupId.orEmpty(),
        password = vpnInfo.password,
        updateTime = DateTime().millis,
        sessionId = sessionId
    )
