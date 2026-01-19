/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.redesign.recents.data

import com.protonvpn.android.profiles.data.Profile
import com.protonvpn.android.profiles.data.toProfile
import com.protonvpn.android.redesign.vpn.ConnectIntent

sealed class RecentConnection {
    abstract val id: Long
    abstract val isPinned: Boolean
    abstract val connectIntent: ConnectIntent

    data class UnnamedRecent(
        override val id: Long,
        override val isPinned: Boolean,
        override val connectIntent: ConnectIntent,
    ) : RecentConnection()

    data class ProfileRecent(
        override val id: Long,
        override val isPinned: Boolean,
        val profile: Profile,
    ) : RecentConnection() {
        override val connectIntent get() = profile.connectIntent
    }
}

fun RecentConnectionWithIntent.toRecentConnection(): RecentConnection {
    // TODO: what to do when data cannot be deserialized because it's invalid?
    //  Currently this code may throw exceptions but it's probably best not to crash the app because it'll become
    //  useless until the user clears the data or reinstalls.
    return if (profile != null) {
        RecentConnection.ProfileRecent(
            id = recent.id,
            isPinned = recent.isPinned,
            profile = profile.toProfile(),
        )
    } else {
        RecentConnection.UnnamedRecent(
            id = recent.id,
            isPinned = recent.isPinned,
            connectIntent = requireNotNull(unnamedRecent).connectIntentData.toConnectIntent()
        )
    }
}
