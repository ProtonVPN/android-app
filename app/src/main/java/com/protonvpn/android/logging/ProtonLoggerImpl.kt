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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val MAX_MESSAGE_LENGTH = 50_000

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

interface ProtonLoggerInterface {
    fun log(event: LogEventType, message: String = "")

    // Log custom event/message with log level info.
    fun logCustom(category: LogCategory, message: String)
    fun logCustom(level: LogLevel, category: LogCategory, message: String)
    fun logBlocking(event: LogEventType, message: String)
    fun formatTime(timeMs: Long): String

    suspend fun getLogFilesForUpload(): List<FileLogWriter.LogFile>
    fun getLogLinesForDisplay(): Flow<String>
    fun clearUploadTempFiles(files: List<FileLogWriter.LogFile>)
    suspend fun getLogFileForSharing(): File?
}

open class ProtonLoggerImpl(
    private val wallClock: () -> Long,
    private val fileLogWriter: FileLogWriter,
    otherWriters: List<LogWriter> = emptyList()
) : ProtonLoggerInterface {
    private val writers = otherWriters + fileLogWriter
    private val timestampFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val logsTimeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun log(event: LogEventType, message: String) {
        logEvent(event.level, event.category, event.name, message, false)
    }

    // Log custom event/message with log level info.
    override fun logCustom(category: LogCategory, message: String) =
        logCustom(LogLevel.INFO, category, message)

    override fun logCustom(level: LogLevel, category: LogCategory, message: String) {
        logEvent(level, category, null, message, false)
    }

    override fun logBlocking(event: LogEventType, message: String) {
        logEvent(event.level, event.category, event.name, message, true)
    }

    override fun formatTime(timeMs: Long): String =
        timestampFormatter.format(Date(timeMs))

    private fun getTimestampNow(): String = formatTime(wallClock())

    private fun logEvent(
        level: LogLevel,
        category: LogCategory,
        eventName: String?,
        message: String,
        blocking: Boolean
    ) {
        if (!shouldLog(level)) return

        val truncatedMessage =
            if (message.length <= MAX_MESSAGE_LENGTH) message else message.take(MAX_MESSAGE_LENGTH)
        writers.forEach { it.write(getTimestampNow(), level, category, eventName, truncatedMessage, blocking) }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun getLogFilesForUpload() = fileLogWriter.getLogFilesForUpload()

    override fun getLogLinesForDisplay() = fileLogWriter.getLogLinesForDisplay().map {
        replaceDateForDisplay(it)
    }

    override fun clearUploadTempFiles(files: List<FileLogWriter.LogFile>) = fileLogWriter.clearUploadTempFiles(files)
    override suspend fun getLogFileForSharing(): File? = fileLogWriter.getLogFileForSharing()

    private fun shouldLog(level: LogLevel): Boolean = BuildConfig.DEBUG || level > LogLevel.DEBUG

    private fun replaceDateForDisplay(logLine: String): String {
        val firstSeparatorIndex = logLine.indexOf(' ')
        return if (firstSeparatorIndex > 0) {
            try {
                val date = timestampFormatter.parse(logLine.substring(0, firstSeparatorIndex))
                val localDateString = logsTimeFormatter.format(date)
                logLine.replaceRange(0, firstSeparatorIndex, localDateString)
            } catch (e: ParseException) {
                logLine
            }
        } else {
            logLine
        }
    }
}
