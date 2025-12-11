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

package com.protonvpn.android.redesign.excludedlocations.usecases

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.redesign.excludedlocations.ExcludedLocations
import com.protonvpn.android.redesign.excludedlocations.data.ExcludedLocationsDao
import com.protonvpn.android.redesign.excludedlocations.data.toDomain
import dagger.Reusable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@Reusable
class ObserveExcludedLocations @Inject constructor(
    private val currentUser: CurrentUser,
    private val excludedLocationsDao: ExcludedLocationsDao,
) {

    operator fun invoke(): Flow<ExcludedLocations> = currentUser.vpnUserFlow
        .flatMapLatest { vpnUser ->
            if (vpnUser == null || vpnUser.isFreeUser) {
                flowOf(ExcludedLocations(allLocations = emptyList()))
            } else {
                excludedLocationsDao.observeAll(userId = vpnUser.userId.id)
                    .map { excludedLocationEntities ->
                        excludedLocationEntities
                            .toDomain()
                            .let(::ExcludedLocations)
                    }
            }
        }

}
