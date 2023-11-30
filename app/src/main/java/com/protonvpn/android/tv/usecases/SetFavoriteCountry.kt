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

package com.protonvpn.android.tv.usecases

import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.tv.vpn.createProfileForCountry
import com.protonvpn.android.userstorage.ProfileManager
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@Reusable
class SetFavoriteCountry @Inject constructor(
    private val mainScope: CoroutineScope,
    private val profileManager: ProfileManager,
    private val userSettingsManager: CurrentUserLocalSettingsManager,
) {

    operator fun invoke(countryCode: String?) {
        profileManager.deleteProfile(profileManager.findDefaultProfile())
        if (countryCode != null) {
            val newDefaultProfile = createProfileForCountry(countryCode)
            profileManager.addToProfileList(newDefaultProfile)
            mainScope.launch {
                userSettingsManager.updateDefaultProfile(newDefaultProfile.id)
            }
        }
    }
}
