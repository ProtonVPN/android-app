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
import com.protonvpn.android.excludedlocations.ExcludedLocations
import com.protonvpn.android.excludedlocations.data.ExcludedLocationsDao
import com.protonvpn.android.excludedlocations.data.toDomain
import com.protonvpn.android.redesign.settings.IsAutomaticConnectionPreferencesFeatureFlagEnabled
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@Reusable
class ObserveExcludedLocations @Inject constructor(
    mainScope: CoroutineScope,
    currentUser: CurrentUser,
    private val excludedLocationsDao: ExcludedLocationsDao,
    private val isAutomaticConnectionEnabled: IsAutomaticConnectionPreferencesFeatureFlagEnabled,
) {

    private val excludedLocationsFlow = currentUser.vpnUserFlow
        .flatMapLatest { vpnUser ->
            if (vpnUser == null || vpnUser.isFreeUser || !isAutomaticConnectionEnabled()) {
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
        .stateIn(
            scope = mainScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = null,
        )

    operator fun invoke(): Flow<ExcludedLocations> = excludedLocationsFlow.filterNotNull()

}
