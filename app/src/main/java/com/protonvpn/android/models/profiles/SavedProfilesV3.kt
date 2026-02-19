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
package com.protonvpn.android.models.profiles

import com.protonvpn.android.models.profiles.ServerWrapper.Companion.makePreBakedFastest
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
class SavedProfilesV3(val profileList: MutableList<Profile>) {
    companion object {
        // Use hardcoded IDs for prebaked profiles.
        // It's not strictly necessary but should make things a bit more robust.
        val FASTEST_PROFILE_ID: UUID? =
            UUID.fromString("82c935d8-2968-4cc5-8ea7-8d73270efe57")

        fun defaultProfiles(): SavedProfilesV3 {
            val fastest = Profile(makePreBakedFastest(), FASTEST_PROFILE_ID)
            return SavedProfilesV3(mutableListOf(fastest))
        }
    }
}
