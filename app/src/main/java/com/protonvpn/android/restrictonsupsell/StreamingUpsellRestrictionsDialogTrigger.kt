/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.restrictonsupsell

import android.app.Activity
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.planupgrade.PlusOnlyUpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradeStreamingBlockFragment
import com.protonvpn.android.utils.flatMapLatestFreeUser
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val UpsellDialogSinceLastEventMs = 1.hours.inWholeMilliseconds
private val UpsellDialogMinIntervalMs = 1.minutes.inWholeMilliseconds

@Reusable
class OpenUpgradeStreamingBlockDialog @Inject constructor() {
    operator fun invoke(activity: Activity) {
        PlusOnlyUpgradeDialogActivity.launch<UpgradeStreamingBlockFragment>(activity)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class StreamingUpsellRestrictionsDialogTrigger @Inject constructor(
    private val mainScope: CoroutineScope,
    private val isStreamingRestrictionUpsellEnabled: IsStreamingRestrictionUpsellEnabled,
    private val isTv: IsTvCheck,
    private val eventStreamingRestricted: StreamingUpsellRestrictionsFlow,
    private val currentUser: CurrentUser,
    private val foregroundActivityTracker: ForegroundActivityTracker,
    private val restrictionsUpsellStore: RestrictionsUpsellStore,
    private val openUpgradeDialog: OpenUpgradeStreamingBlockDialog,
    @param:WallClock private val now: () -> Long,
) {
    // There are two independent paths to show the upsell dialog and they are difficult to
    // synchronize. Use this lastShownTimestamp to make sure we don't show the dialog twice within
    // a short time frame.
    private var lastShownTimestamp: Long? = null

    fun start() {
        // TV upsell will be implemented in the future.
        if (isTv()) return

        currentUser.vpnUserFlow
            .flatMapLatestFreeUser {
                eventStreamingRestricted.onEach { restrictions ->
                    restrictionsUpsellStore.update { current ->
                        val streaming =
                            current.streaming.copy(
                                lastEventTimestamp = now(),
                                lastEventConnectionId = restrictions.connectionId.toString(),
                            )
                        current.copy(streaming = streaming)
                    }
                }
            }
            .launchIn(mainScope)

        foregroundActivityTracker.foregroundBackgroundTransitionFlow
            .onEach { (wasInForeground, isInForeground) ->
                if (!wasInForeground && isInForeground && shouldShowUpsellDialogOnOpen()) {
                    val foregroundActivity = foregroundActivityTracker.foregroundActivity
                    if (foregroundActivity != null) {
                        showNow(foregroundActivity)
                    }
                }
            }
            .launchIn(mainScope)
    }

    suspend fun showNow(activity: Activity) {
        // Ideally the upsell notification should be hidden when the user upgrades but it should
        // be a rare case and checking user type here is much simpler.
        if (currentUser.vpnUser()?.isFreeUser != true) return
        val lastShown = lastShownTimestamp
        if (lastShown != null && lastShown > now() - UpsellDialogMinIntervalMs) return

        onShowUpsellDialogShown()
        openUpgradeDialog(activity)
    }

    private suspend fun shouldShowUpsellDialogOnOpen(): Boolean {
        if (!isStreamingRestrictionUpsellEnabled() || currentUser.vpnUser()?.isFreeUser != true) {
            return false
        }

        val state = restrictionsUpsellStore.state.first().streaming
        val withinTime = state.lastEventTimestamp + UpsellDialogSinceLastEventMs > now()
        val wasShownForThisConnection =
            state.lastEventConnectionId != null &&
                state.lastEventConnectionId == state.lastDialogConnectionId
        return withinTime && !wasShownForThisConnection
    }

    private fun onShowUpsellDialogShown() {
        lastShownTimestamp = now()
        mainScope.launch(start = CoroutineStart.UNDISPATCHED) {
            restrictionsUpsellStore.update { current ->
                val streaming =
                    current.streaming.copy(
                        lastDialogConnectionId = current.streaming.lastEventConnectionId
                    )
                current.copy(streaming = streaming)
            }
        }
    }
}
