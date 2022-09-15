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

package com.protonvpn.android.logging

import android.util.Log
import com.protonvpn.android.BuildConfig

class LogcatLogWriter : LogWriter {
    override fun write(
        timestamp: String,
        level: LogLevel,
        category: LogCategory,
        eventName: String?,
        message: String,
        blocking: Boolean
    ) {
        Log.println(getPriority(level), "ProtonLogger", createLine(category, eventName, message))
    }

    private fun createLine(category: LogCategory, event: String?, message: String): String {
        val eventPart = event?.let { ":$it" }.orEmpty()

        val messagePart = message.replace("\n", "\n ")
        val withCaller = messagePart + getCallerInfo()
        return "${category.toLog()}$eventPart | $withCaller"
    }

    private fun getCallerInfo(): String {
        val stacks = if (BuildConfig.DEBUG) Throwable().stackTrace else null

        if (stacks == null || stacks.isEmpty()) {
            return ""
        }
        val callerInfo = kotlin.runCatching {
            val protonLogger = stacks.indexOfLast { it.fileName?.contains("ProtonLogger") == true }
            // Depth for core to land in meaningful class is +3, whereas for VPN it's +1 as of now
            // May not be accurate in all the cases
            val indexOfDepth = if (stacks[protonLogger + 1].fileName.contains("VpnCoreLogger")) 3 else 1
            String.format(
                    " (%s:%s)",
                    stacks[protonLogger + indexOfDepth].fileName,
                    stacks[protonLogger + indexOfDepth].lineNumber
                )
        }.getOrDefault("")

        // Filter out LogcatCapture and LoggingUtils (from core OkHttp) as those are not useful
        return if (!callerInfo.contains("LogcatLogCapture") && !callerInfo.contains("LoggingUtils"))
            callerInfo
        else
            ""
    }

    private fun getPriority(level: LogLevel): Int = when (level) {
        LogLevel.TRACE -> Log.VERBOSE
        LogLevel.DEBUG -> Log.DEBUG
        LogLevel.INFO -> Log.INFO
        LogLevel.WARN -> Log.WARN
        LogLevel.ERROR -> Log.ERROR
        LogLevel.FATAL -> Log.ERROR
    }
}
