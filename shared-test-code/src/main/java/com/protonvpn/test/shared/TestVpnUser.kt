/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.test.shared

import com.protonvpn.android.auth.data.VpnUser
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.session.SessionId

object TestVpnUser {
    fun create(
        id: String = "id",
        maxTier: Int = 2,
        maxConnect: Int = 2,
        subscribed: Int = VpnUser.VPN_SUBSCRIBED_FLAG
    ) = VpnUser(
        userId = UserId(id),
        subscribed = subscribed,
        services = 4,
        delinquent = 0,
        credit = 0,
        hasPaymentMethod = false,
        status = 1,
        planName = "plan",
        planDisplayName = "Plan",
        maxTier = maxTier,
        maxConnect = maxConnect,
        name = "name",
        password = "pass",
        groupId = "",
        updateTime = 0,
        expirationTime = Integer.MAX_VALUE,
        sessionId = SessionId("session id"),
        autoLoginId = null,
    )
}
