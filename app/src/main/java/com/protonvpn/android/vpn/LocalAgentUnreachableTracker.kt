/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.android.vpn

import com.protonvpn.android.di.ElapsedRealtimeClock
import com.protonvpn.android.utils.jitterMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkStatus
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAgentUnreachableTracker @Inject constructor(
    @ElapsedRealtimeClock private val elapsedRealtimeMs: () -> Long,
    mainScope: CoroutineScope,
    networkManager: NetworkManager
) {
    enum class UnreachableAction {
        SILENT_RECONNECT, FALLBACK, ERROR
    }

    private val fallbackIntervalMinMs = TimeUnit.SECONDS.toMillis(30)
    private val fallbackIntervalMaxMs = TimeUnit.MINUTES.toMillis(15)

    private var lastFallbackMs = 0L
    private var fallbackTriggerCount = 0
    private var isConnected = false
    private var lastUnreachableAction: UnreachableAction? = null

    init {
        networkManager.observe()
            .filterNot { it == NetworkStatus.Disconnected }
            .onEach { onNetworkChange() }
            .launchIn(mainScope)
    }

    fun onUnreachable(): UnreachableAction {
        val wasConnected = isConnected
        isConnected = false

        // Note: silent reconnect does count towards timer for triggering fallback.
        val now = elapsedRealtimeMs()
        if (lastFallbackMs == 0L)
            lastFallbackMs = now

        val unreachableAction = when {
            wasConnected -> UnreachableAction.SILENT_RECONNECT
            now - lastFallbackMs >= fallbackIntervalMs() -> UnreachableAction.FALLBACK
            else -> UnreachableAction.ERROR
        }
        lastUnreachableAction = unreachableAction
        return unreachableAction
    }

    fun onFallbackTriggered() {
        ++fallbackTriggerCount
        lastFallbackMs = elapsedRealtimeMs()
    }

    fun isSilentReconnect() = lastUnreachableAction == UnreachableAction.SILENT_RECONNECT

    fun reset(connected: Boolean) {
        isConnected = connected
        lastUnreachableAction = null
        fallbackTriggerCount = 0
        lastFallbackMs = 0
    }

    private fun onNetworkChange() {
        if (lastFallbackMs > 0) { // Only update state when tracking errors.
            fallbackTriggerCount = 0
            lastFallbackMs = elapsedRealtimeMs()
        }
    }

    private fun fallbackIntervalMs() =
        jitterMs(
            minOf(
                square(1L + fallbackTriggerCount) * fallbackIntervalMinMs,
                fallbackIntervalMaxMs
            )
        )

    private fun square(a: Long): Long = a * a
}
