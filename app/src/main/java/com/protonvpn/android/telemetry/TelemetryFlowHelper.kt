/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.android.telemetry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class TelemetryEventData(
    val measurementGroup: String,
    val eventName: String,
    val values: Map<String, Long> = emptyMap(),
    val dimensions: Map<String, String> = emptyMap()
)

// Utility class to help maintaining right order of telemetry events that are produced by suspending
// functions. All events should be reported via this class.
@Singleton
class TelemetryFlowHelper @Inject constructor(
    private val mainScope: CoroutineScope,
    private val telemetry: Telemetry,
) {
    // Run the actions sequentially to ensure the suspending parts of flows finish before others are
    // executed.
    private val serialExecutor = Channel<suspend () -> Unit>(capacity = Channel.UNLIMITED).apply {
        mainScope.launch {
            consumeEach { action -> action() }
        }
    }

    fun event(sendImmediately: Boolean = false, getEventData: suspend () -> TelemetryEventData?) {
        runSerially {
            getEventData()?.let {
                telemetry.event(it.measurementGroup, it.eventName, it.values, it.dimensions, sendImmediately)
            }
        }
    }

    fun runSerially(block: suspend () -> Unit) {
        serialExecutor.trySend(block)
    }
}
