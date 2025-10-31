/*
 * Copyright (c) 2025. Proton AG
 *
 *  This file is part of ProtonVPN.
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

package com.protonvpn.android.update

import com.protonvpn.android.ui.storage.UiStateStorage
import dagger.Reusable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

// Don't show the dot for very fresh updates, give chance for regular auto update to update the app
// without bothering the user.
private const val MIN_STALENESS_DAYS = 7

interface ShouldShowAppUpdateDotFlow : Flow<Boolean>

@Reusable
class ShouldShowAppUpdateDotFlowImpl @Inject constructor(
    appUpdateManager: AppUpdateManager,
    uiStateStorage: UiStateStorage,
    featureFlagEnabled: IsAppUpdateBannerFeatureFlagEnabled,
): ShouldShowAppUpdateDotFlow {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val flow = featureFlagEnabled.observe()
        .flatMapLatest { isFfEnabled ->
            if (isFfEnabled) {
                updateFlow
            } else {
                flowOf(false)
            }
        }

    private val updateFlow = combine(
        uiStateStorage.state.map { it.lastAppUpdatePromptAckedVersion },
        appUpdateManager.checkForUpdateFlow,
    ) { versionAcked, update ->
        val versionAvailable = update?.availableVersionCode
        (update?.stalenessDays ?: 0) >= MIN_STALENESS_DAYS &&
                versionAvailable != null &&
                (versionAcked == null || versionAcked < versionAvailable)
    }

    override suspend fun collect(collector: FlowCollector<Boolean>) {
        flow.collect(collector)
    }
}