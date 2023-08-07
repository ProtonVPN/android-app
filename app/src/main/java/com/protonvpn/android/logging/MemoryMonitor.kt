/*
 * Copyright (c) 2023. Proton AG
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
package com.protonvpn.android.logging

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import androidx.core.content.getSystemService
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.ElapsedRealtimeClock
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mainScope: CoroutineScope,
    private val vpnStateMonitor: VpnStateMonitor,
    private val currentUser: CurrentUser,
    @ElapsedRealtimeClock private val elapsedRealtimeClock: () -> Long
) {
    private val activityManager = context.getSystemService<ActivityManager>()

    data class MemResult(val pssKb: Int, val privateKb: Int, val rssKb: Int) {
        override fun toString() = "pss: ${pssKb/1024}MB, private: ${privateKb/1024}MB, rss: ${rssKb/1024}MB"
    }

    private var lastResult: MemResult? = null
    private var lastResultTimestamp: Long = 0

    fun start() {
        mainScope.launch {
            vpnStateMonitor.status
                .map { it.state != VpnState.Disabled }
                .distinctUntilChanged()
                .collectLatest { vpnActive ->
                    if (vpnActive) {
                        // Give the app a moment to start first
                        delay(MEM_LOG_INITIAL_DELAY)
                        while (isActive) {
                            logMemory()
                            delay(MEM_LOG_PERIODIC_DELAY)
                        }
                    }
                }
        }
    }

    private suspend fun logMemory() {
        val now = elapsedRealtimeClock()
        val elapsed = now - lastResultTimestamp
        if (elapsed > MEM_LOG_MIN_DELAY) {
            activityManager?.getProcessMemoryInfo(
                intArrayOf(Process.myPid())
            )?.let {
                val procInfo = it.first()
                val rss = with(procInfo) { totalPrivateDirty + totalPrivateClean + totalSharedDirty + totalSharedClean }
                val result = MemResult(
                    pssKb = procInfo.totalPss,
                    privateKb = procInfo.totalPrivateDirty + procInfo.totalPrivateClean,
                    rssKb = rss)
                val memState = ActivityManager.RunningAppProcessInfo()
                ActivityManager.getMyMemoryState(memState)
                val memInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                if (result == lastResult) {
                    ProtonLogger.logCustom(LogCategory.APP, "MemoryMonitor: $result (Stale)")
                } else {
                    ProtonLogger.logCustom(LogCategory.APP, "MemoryMonitor: $result," +
                        "available: ${memInfo.availMem / 1024 / 1024}MB, low: ${memInfo.lowMemory}")
                    ProtonLogger.logCustom(LogCategory.APP, "MemoryMonitor importance: " +
                        "${memState.importance}/${memState.importanceReasonCode}/" +
                        memState.importanceReasonComponent?.shortClassName)

                    if (
                        result.privateKb > HIGH_USAGE_THRESHOLD_KB &&
                        elapsed > SENTRY_LOG_DELAY &&
                        currentUser.vpnUser()?.isPMTeam == true
                    ) {
                        val event = SentryEvent().apply {
                            message = Message().apply {
                                message = "MemoryMonitor threshold exceeded: %s"
                                params = listOf("${result.privateKb/1024}MB " +
                                    "> ${HIGH_USAGE_THRESHOLD_KB/1024}")
                            }
                        }
                        Sentry.captureEvent(event)
                    }
                }

                lastResult = result
                lastResultTimestamp = now
            }
        }
    }

    fun onTrimMemory() {
        mainScope.launch { logMemory() }
    }

    companion object {
        val MEM_LOG_INITIAL_DELAY = TimeUnit.SECONDS.toMillis(4)
        val MEM_LOG_PERIODIC_DELAY = TimeUnit.MINUTES.toMillis(20)
        val MEM_LOG_MIN_DELAY = TimeUnit.MINUTES.toMillis(3)
        val SENTRY_LOG_DELAY = TimeUnit.HOURS.toMillis(1)
        const val HIGH_USAGE_THRESHOLD_KB = 250/*MB*/ * 1024
    }
}