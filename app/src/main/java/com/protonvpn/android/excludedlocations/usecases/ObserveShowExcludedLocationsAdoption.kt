/*
 * Copyright (c) 2026 Proton AG
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

import com.protonvpn.android.redesign.settings.IsAutomaticConnectionPreferencesFeatureFlagEnabled
import com.protonvpn.android.ui.storage.UiStateStorage
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@Reusable
class ObserveShowExcludedLocationsAdoption @Inject constructor(
    uiStateStorage: UiStateStorage,
    private val isAutomaticConnectionEnabled: IsAutomaticConnectionPreferencesFeatureFlagEnabled,
) {

    private val shouldShowExcludedLocationsAdoptionFlow = uiStateStorage.state
        .map { it.shouldShowExcludedLocationsAdoption }
        .distinctUntilChanged()

    operator fun invoke(): Flow<Boolean> = combine(
        isAutomaticConnectionEnabled.observe(),
        shouldShowExcludedLocationsAdoptionFlow,
    ) { isAutomaticConnectionEnabled, shouldShowExcludedLocationsAdoption ->
        isAutomaticConnectionEnabled && shouldShowExcludedLocationsAdoption
    }

}
