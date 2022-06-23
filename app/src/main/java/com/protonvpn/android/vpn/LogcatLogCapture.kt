/*
 * Copyright (c) 2021. Proton Technologies AG
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

import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.di.ElapsedRealtimeClock
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG_MESSAGE_SEPARATOR = ": " // This is what logcat uses.

/**
 * Capture logcat messages from VPN libraries and the secondary process and log them to ProtonLogger.
 */
@Singleton
class LogcatLogCapture @Inject constructor(
    private val mainScope: CoroutineScope,
    private val dispatcherProvider: VpnDispatcherProvider,
    @ElapsedRealtimeClock private val monoClock: () -> Long
) {

    init {
        mainScope.launch(dispatcherProvider.infiniteIo) {
            captureCharonWireguardLogs()
        }
    }

    private suspend fun captureCharonWireguardLogs() {
        do {
            val start = monoClock()
            try {
                val wireguardTag = "WireGuard/GoBackend/${Constants.WIREGUARD_TUNNEL_NAME}"
                val process = Runtime.getRuntime().exec(
                    "logcat -v brief -s $wireguardTag:* charon:* ${Constants.SECONDARY_PROCESS_TAG}:* -T 1"
                )
                BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                    lines.forEach {
                        parseAndLog(it)
                    }
                }
                ProtonLogger.logCustom(LogLevel.WARN, LogCategory.APP, "Logcat streaming ended")
            } catch (e: IOException) {
                ProtonLogger.logCustom(
                    LogLevel.WARN,
                    LogCategory.APP,
                    "Log capturing from logcat failed: ${e.message}"
                )
            }
            // Avoid busy loop if capture fails early
            if (monoClock() - start < TimeUnit.MINUTES.toMillis(5))
                delay(TimeUnit.MINUTES.toMillis(1))
            ProtonLogger.logCustom(LogCategory.APP, "Restarting logcat capture")
        } while (true)
    }

    private fun parseAndLog(line: String) {
        if (line.isEmpty() || line.startsWith("--------- beginning")) return
        val split = line.split(TAG_MESSAGE_SEPARATOR, limit = 2)
        if (split.size == 2) {
            val level = toLogLevel(split[0][0])
            val category = toCategory(split[0])
            ProtonLogger.logCustom(level, category, split[1])
        } else {
            ProtonLogger.logCustom(LogCategory.APP, line)
        }
    }

    private fun toLogLevel(logcatLevel: Char) = when (logcatLevel) {
        'V' -> LogLevel.DEBUG
        'D' -> LogLevel.INFO // We want to log debug messages from protocols
        'I' -> LogLevel.INFO
        'W' -> LogLevel.WARN
        else -> LogLevel.ERROR
    }

    private fun toCategory(lineBeginning: String): LogCategory =
        if (lineBeginning.contains(Constants.SECONDARY_PROCESS_TAG))
            LogCategory.APP
        else
            LogCategory.PROTOCOL
}
