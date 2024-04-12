/*
 * Copyright (c) 2024. Proton AG
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

import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.entity.Type
import me.proton.core.user.domain.entity.User

// We should upstream such helpers to Account modules.
fun createAccountUser(id: UserId = UserId("id"), type: Type = Type.Proton, createdAtUtc: Long = 0L) = User(
    userId = id,
    email = null,
    name = null,
    displayName = null,
    currency = "EUR",
    type = type,
    credit = 0,
    createdAtUtc = createdAtUtc,
    usedSpace = 0,
    maxSpace = 0,
    maxUpload = 0,
    role = null,
    private = false,
    subscribed = 0,
    services = 0,
    delinquent = null,
    recovery = null,
    keys = emptyList()
)
