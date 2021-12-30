/*
 * Copyright (c) 2019 Proton Technologies AG
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

import com.protonvpn.android.BuildConfig
import kotlinx.coroutines.flow.map
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat
import java.lang.IllegalArgumentException

enum class LogLevel {
    TRACE, DEBUG, INFO, WARN, ERROR, FATAL;
}

interface LogWriter {
    @Suppress("LongParameterList")
    fun write(
        timestamp: String,
        level: LogLevel,
        category: LogCategory,
        eventName: String?,
        message: String,
        blocking: Boolean
    )
}

open class ProtonLoggerImpl(
    private val wallClock: () -> Long,
    private val fileLogWriter: FileLogWriter,
    otherWriters: List<LogWriter> = emptyList()
) {
    private val writers = otherWriters + fileLogWriter
    private val timestampFormatter: DateTimeFormatter = ISODateTimeFormat.dateTime()

    fun log(event: LogEventType, message: String = "") {
        logEvent(event.level, event.category, event.name, message, false)
    }

    // Log custom event/message with log level info.
    fun logCustom(category: LogCategory, message: String) =
        logCustom(LogLevel.INFO, category, message)

    fun logCustom(level: LogLevel, category: LogCategory, message: String) {
        logEvent(level, category, null, message, false)
    }

    fun logBlocking(event: LogEventType, message: String) {
        logEvent(event.level, event.category, event.name, message, true)
    }

    fun formatTime(timeMs: Long): String =
        timestampFormatter.print(DateTime(timeMs, DateTimeZone.UTC))

    private fun getTimestampNow(): String = formatTime(wallClock())

    private fun logEvent(
        level: LogLevel,
        category: LogCategory,
        eventName: String?,
        message: String,
        blocking: Boolean
    ) {
        if (!shouldLog(level)) return

        writers.forEach { it.write(getTimestampNow(), level, category, eventName, message, blocking) }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun getLogFilesForUpload() = fileLogWriter.getLogFilesForUpload()

    fun getLogLinesForDisplay() = fileLogWriter.getLogLinesForDisplay().map {
        replaceDateForDisplay(it)
    }

    fun clearUploadTempFiles(files: List<FileLogWriter.LogFile>) = fileLogWriter.clearUploadTempFiles(files)

    private fun shouldLog(level: LogLevel): Boolean = BuildConfig.DEBUG || level > LogLevel.DEBUG

    private fun replaceDateForDisplay(logLine: String): String {
        val firstSeparatorIndex = logLine.indexOf(' ')
        return if (firstSeparatorIndex > 0) {
            try {
                val date = timestampFormatter.parseDateTime(logLine.substring(0, firstSeparatorIndex))
                val localDateString = date.toLocalTime().toString()
                logLine.replaceRange(0, firstSeparatorIndex, localDateString)
            } catch (e: IllegalArgumentException) {
                logLine
            }
        } else {
            logLine
        }
    }
}
