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

package com.protonvpn.android.app

import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.mmp.IsMmpFeatureFlagEnabled
import com.protonvpn.android.mmp.events.MmpEventType
import com.protonvpn.android.mmp.events.usecases.SaveMmpEvent
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.ui.ForegroundActivityTracker
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppOpenMmpTracker @Inject constructor(
    private val isMmpEnabled: IsMmpFeatureFlagEnabled,
    private val userSettings: EffectiveCurrentUserSettings,
    private val mainScope: CoroutineScope,
    @param:WallClock private val now: () -> Long,
    private val foregroundActivityTracker: Lazy<ForegroundActivityTracker>,
    private val appFeaturesPrefs: Lazy<AppFeaturesPrefs>,
    private val saveMmpEvent: Lazy<SaveMmpEvent>,
) {

    @Volatile
    private var isAppMovedToBackground = false

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        combine(
            isMmpEnabled.observe(),
            userSettings.telemetry,
        ) { isMmpEnabled, isTelemetryEnabled -> isMmpEnabled && isTelemetryEnabled }
            .filter { isSavingMmpEventsAllowed -> isSavingMmpEventsAllowed }
            .flatMapLatest { foregroundActivityTracker.get().foregroundBackgroundTransitionFlow }
            .onEach { (wasInForeground, isInForeground) ->
                when {
                    wasInForeground && !isInForeground -> onAppBackgrounded()
                    !wasInForeground && isInForeground -> onAppForegrounded()
                }
            }
            .launchIn(scope = mainScope)
    }

    private fun onAppBackgrounded() {
        appFeaturesPrefs.get().lastAppInForegroundTimestamp = now()

        isAppMovedToBackground = true
    }

    private fun onAppForegrounded() {
        mainScope.launch {
            val lastForegroundTimestamp = appFeaturesPrefs.get().lastAppInForegroundTimestamp

            when {
                lastForegroundTimestamp == null || !isAppMovedToBackground -> {
                    saveMmpEvent.get().invoke(eventType = MmpEventType.Open)
                }

                now() - lastForegroundTimestamp >= INACTIVITY_THRESHOLD_MILLIS -> {
                    saveMmpEvent.get().invoke(eventType = MmpEventType.Open, isSessionRestartRequired = true)
                }
            }
        }
    }

    private companion object {

        private const val INACTIVITY_THRESHOLD_MILLIS = 30 * 60 * 1_000 // 30 mins

    }

}
