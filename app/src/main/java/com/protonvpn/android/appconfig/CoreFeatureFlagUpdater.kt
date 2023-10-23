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
import com.protonvpn.android.appconfig.periodicupdates.retryAfterIfApplicable
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.utils.runChatchingCheckedExceptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.core.domain.entity.UserId
import me.proton.core.featureflag.domain.ExperimentalProtonFeatureFlag
import me.proton.core.featureflag.domain.usecase.FetchUnleashTogglesRemote
import me.proton.core.network.domain.ApiException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalProtonFeatureFlag::class)
@Singleton
class CoreFeatureFlagUpdater @Inject constructor(
    private val scope: CoroutineScope,
    private val currentUser: CurrentUser,
    private val fetchFlags: FetchUnleashTogglesRemote,
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
        // Refresh whenever logged user changes or their plan.
        currentUser.vpnUserFlow
            .distinctUntilChangedBy { Pair(it?.userId, it?.planName) }
            .drop(1) // Don't refresh on initial value, only on change
            .onEach {
                periodicUpdateManager.executeNow(coreFeatureFlagsUpdate, it?.userId)
            }
            .launchIn(scope)
    }

    private suspend fun refreshInternal(userId: UserId?): PeriodicActionResult<Unit> = suspend {
        fetchFlags(userId)
        PeriodicActionResult(Unit, isSuccess = true)
    }.runChatchingCheckedExceptions { e ->
        val retryAfter = (e as? ApiException)?.error?.retryAfterIfApplicable()?.inWholeMilliseconds
        PeriodicActionResult(Unit, isSuccess = false, retryAfter)
    }

    companion object {
        private val UPDATE_DELAY_FOREGROUND = TimeUnit.HOURS.toMillis(2)
        private val UPDATE_DELAY_BACKGROUND = TimeUnit.HOURS.toMillis(12)
    }
}
