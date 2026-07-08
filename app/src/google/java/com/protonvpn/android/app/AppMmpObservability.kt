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

import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateSpec
import com.protonvpn.android.appconfig.periodicupdates.UpdateAction
import com.protonvpn.android.mmp.IsMmpFeatureFlagEnabled
import com.protonvpn.android.mmp.events.usecases.GetMmpEvents
import com.protonvpn.android.mmp.events.usecases.ResetMmpEvents
import com.protonvpn.android.mmp.events.usecases.SendMmpEvents
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import dagger.Lazy
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

@Reusable
class AppMmpObservability @Inject constructor(
    private val isMmpFeatureFlagEnabled: IsMmpFeatureFlagEnabled,
    private val userSettings: EffectiveCurrentUserSettings,
    private val mainScope: CoroutineScope,
    private val periodicUpdateManager: Lazy<PeriodicUpdateManager>,
    private val resetMmpEvents: Lazy<ResetMmpEvents>,
    sendMmpEvents: Lazy<SendMmpEvents>,
    getMmpEvents: Lazy<GetMmpEvents>,
) {

    private val updateMmpEventsAction = UpdateAction(
        id = UPDATE_MMP_EVENTS_ACTION_ID,
        action = { mmpEvents -> sendMmpEvents.get().invoke(mmpEvents) },
        defaultInput = { getMmpEvents.get().invoke() },
    )

    fun start() {
        combine(
            isMmpFeatureFlagEnabled.observe().distinctUntilChanged(),
            userSettings.telemetry,
            ::Pair,
        )
            .onEach { (isMmpEnabled, isTelemetryEnabled) ->
                if(isMmpEnabled && isTelemetryEnabled) {
                    periodicUpdateManager.get().registerUpdateAction(
                        action = updateMmpEventsAction,
                        updateSpec = arrayOf(
                            PeriodicUpdateSpec(
                                intervalMs = sendMmpEventsIntervalMillis,
                                conditions = emptySet(),
                            ),
                        ),
                    )
                } else {
                    periodicUpdateManager.get().unregister(action = updateMmpEventsAction)

                    val mmpFeatureFlag = isMmpFeatureFlagEnabled.getFlag()

                    if(!isTelemetryEnabled || (mmpFeatureFlag != null && !mmpFeatureFlag.value)) {
                        resetMmpEvents.get().invoke()
                    }
                }
            }
            .launchIn(scope = mainScope)
    }

    private companion object {

        private const val UPDATE_MMP_EVENTS_ACTION_ID = "update_mmp_events"

        private val sendMmpEventsIntervalMillis = 1.days.inWholeMilliseconds

    }

}
