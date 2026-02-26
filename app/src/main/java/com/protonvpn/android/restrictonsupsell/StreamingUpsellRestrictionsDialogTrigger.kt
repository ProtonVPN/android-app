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

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.utils.flatMapLatestFreeUser
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private val UpsellDialogSinceLastEventMs = 1.hours.inWholeMilliseconds

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class StreamingUpsellRestrictionsDialogTrigger
@Inject
constructor(
    private val mainScope: CoroutineScope,
    private val isStreamingRestrictionUpsellEnabled: IsStreamingRestrictionUpsellEnabled,
    private val eventStreamingRestricted: StreamingUpsellRestrictionsFlow,
    private val currentUser: CurrentUser,
    private val restrictionsUpsellStore: RestrictionsUpsellStore,
    @param:WallClock private val now: () -> Long,
) {

    fun start() {
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
    }

    suspend fun shouldShowUpsellDialogOnOpen(): Boolean {
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

    fun onShowUpsellDialogShown() {
        mainScope.launch {
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
