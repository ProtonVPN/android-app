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

package com.protonvpn.android.vpn

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.utils.implies
import dagger.Reusable
import javax.inject.Inject

@Reusable
class DefaultAvailableConnection @Inject constructor(
    private val profileManager: ProfileManager,
    private val currentUser: CurrentUser,
) {

    operator fun invoke(): Profile =
        (listOf(profileManager.getDefaultOrFastest()) + profileManager.getSavedProfiles())
            .first {
                (it.isSecureCore == true).implies(currentUser.vpnUserCached()?.isUserPlusOrAbove == true)
            }
}
