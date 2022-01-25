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
        return "${category.toLog()}$eventPart | $messagePart"
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
