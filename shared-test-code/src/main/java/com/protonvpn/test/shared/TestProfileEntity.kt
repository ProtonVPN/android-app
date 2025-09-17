/*
 * Copyright (c) 2024 Proton AG
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

import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileEntity
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.redesign.vpn.ConnectIntent
import me.proton.core.domain.entity.UserId

fun createProfileEntity(
    id: Long = 1L,
    userId: UserId = UserId("1"),
    name: String = "Profile1",
    color: ProfileColor = ProfileColor.Color1,
    icon: ProfileIcon = ProfileIcon.Icon1,
    createdAt: Long = 0L,
    isUserCreated: Boolean = true,
    connectIntent: ConnectIntent,
) = ProfileEntity(
    userId,
    name,
    color,
    icon,
    autoOpenText = "",
    autoOpenEnabled = false,
    autoOpenUrlPrivately = false,
    createdAt,
    isUserCreated = isUserCreated,
    lastConnectedAt = null,
    connectIntentData = connectIntent.toData().copy(profileId = id),
)
