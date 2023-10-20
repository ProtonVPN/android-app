/*
 * Copyright (c) 2023 Proton AG
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
package com.protonvpn.android.appconfig

import com.protonvpn.android.appconfig.periodicupdates.IsInForeground
import com.protonvpn.android.appconfig.periodicupdates.PeriodicActionResult
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateSpec
import com.protonvpn.android.appconfig.periodicupdates.registerAction
import com.protonvpn.android.auth.usecase.CurrentUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import me.proton.core.domain.entity.UserId
import me.proton.core.featureflag.domain.ExperimentalProtonFeatureFlag
import me.proton.core.featureflag.domain.FeatureFlagManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalProtonFeatureFlag::class)
@Singleton
class CoreFeatureFlagUpdater @Inject constructor(
    private val scope: CoroutineScope,
    private val currentUser: CurrentUser,
    private val featureFlagManager: FeatureFlagManager,
    private val periodicUpdateManager: PeriodicUpdateManager,
    @IsInForeground inForeground: Flow<Boolean>,
) {
    private val coreFeatureFlagsUpdate = periodicUpdateManager.registerAction(
        "core_feature_flags",
        ::refreshInternal,
        { currentUser.user()?.userId },
        PeriodicUpdateSpec(UPDATE_DELAY_FOREGROUND, setOf(inForeground)),
        PeriodicUpdateSpec(UPDATE_DELAY_BACKGROUND, setOf()),
    )

    fun start() {
        // No persistency so far for feature flags, so we need to always force refresh on start.
        // We also need to refresh each time user changes
        currentUser.vpnUserFlow
            .distinctUntilChangedBy { Pair(it?.userId, it?.planName) }
            .map { it?.userId }
            .onEach {
                periodicUpdateManager.executeNow(coreFeatureFlagsUpdate, it)
            }.launchIn(scope)
    }

    private fun refreshInternal(userId: UserId?) : PeriodicActionResult<Unit> {
        featureFlagManager.refreshAll(userId)
        // No way to check if the refresh succeeded, worst case it'll try again later
        return PeriodicActionResult(Unit, isSuccess = true)
    }

    companion object {
        private val UPDATE_DELAY_FOREGROUND = TimeUnit.HOURS.toMillis(2)
        private val UPDATE_DELAY_BACKGROUND = TimeUnit.HOURS.toMillis(12)
    }
}