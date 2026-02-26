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

import androidx.annotation.VisibleForTesting
import com.protonvpn.android.di.WallClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days

private val NotificationRepeatIntervalMs = 1.days.inWholeMilliseconds

@Singleton
class StreamingUpsellRestrictionsNotificationTrigger @Inject constructor(
    eventStreamingRestricted: StreamingUpsellRestrictionsFlow,
    private val restrictionsUpsellStore: RestrictionsUpsellStore,
    @param:WallClock private val now: () -> Long,
) {

    @VisibleForTesting
    val eventNotification: Flow<Unit> =
        eventStreamingRestricted.mapNotNull {
            // Don't use restrictionsUpsellStore.state in combine to avoid initializing the store
            // on app start.
            val lastNotificationTimestamp =
                restrictionsUpsellStore.state.first().streaming.lastNotificationTimestamp
            if (lastNotificationTimestamp + NotificationRepeatIntervalMs <= now()) {
                restrictionsUpsellStore.update { current ->
                    current.copy(
                        streaming = current.streaming.copy(lastNotificationTimestamp = now())
                    )
                }
                Unit
            } else {
                null
            }
        }
}
