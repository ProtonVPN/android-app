/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.excludedlocations.usecases

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.excludedlocations.ExcludedLocation
import com.protonvpn.android.excludedlocations.data.ExcludedLocationsDao
import com.protonvpn.android.excludedlocations.data.toEntity
import dagger.Reusable
import javax.inject.Inject

@Reusable
class RemoveExcludedLocation @Inject constructor(
    private val currentUser: CurrentUser,
    private val excludedLocationsDao: ExcludedLocationsDao,
) {

    suspend operator fun invoke(excludedLocation: ExcludedLocation) {
        val vpnUser = currentUser.vpnUser()

        if(vpnUser == null) {
            ProtonLogger.logCustom(
                category = LogCategory.SETTINGS,
                message = "Cannot remove excluded location: no VPN user found",
            )

            return
        }

        if(vpnUser.isFreeUser) {
            ProtonLogger.logCustom(
                category = LogCategory.SETTINGS,
                message = "Cannot remove excluded location: free VPN user is not allowed",
            )

            return
        }

        excludedLocationsDao.delete(entity = excludedLocation.toEntity(userId = vpnUser.userId))
    }

}
