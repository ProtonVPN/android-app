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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.File

/**
 * A static facade for a logger.
 *
 * It allows easy access to logging anywhere in the code base without any assumptions on the actual
 * logging implementation. This allows logging to be used in code that is unit tested (the default
 * implementation does nothing).
 *
 * Typically an Application subclass creates a real logger implementation and sets it by calling
 * setLogger().
 */
object ProtonLogger : ProtonLoggerInterface {

    private var logger: ProtonLoggerInterface = NoopProtonLogger()

    override fun log(event: LogEventType, message: String) =
        logger.log(event, message)

    override fun logCustom(category: LogCategory, message: String) =
        logger.logCustom(category, message)

    override fun logCustom(level: LogLevel, category: LogCategory, message: String) =
        logger.logCustom(level, category, message)

    override fun logBlocking(event: LogEventType, message: String) =
        logger.logBlocking(event, message)

    override fun formatTime(timeMs: Long): String = logger.formatTime(timeMs)

    override suspend fun getLogFilesForUpload(): List<FileLogWriter.LogFile> = logger.getLogFilesForUpload()

    override fun getLogLinesForDisplay(): Flow<List<String>> = logger.getLogLinesForDisplay()

    override fun clearUploadTempFiles(files: List<FileLogWriter.LogFile>) =
        logger.clearUploadTempFiles(files)

    override suspend fun getLogFileForSharing(): File? = logger.getLogFileForSharing()

    @JvmStatic
    fun setLogger(newLogger: ProtonLoggerInterface) {
        logger = newLogger
    }
}

private class NoopProtonLogger : ProtonLoggerInterface {
    override fun log(event: LogEventType, message: String) {}
    override fun logCustom(category: LogCategory, message: String) {}
    override fun logCustom(level: LogLevel, category: LogCategory, message: String) {}
    override fun logBlocking(event: LogEventType, message: String) {}
    override fun formatTime(timeMs: Long): String = ""
    override suspend fun getLogFilesForUpload(): List<FileLogWriter.LogFile> = emptyList()
    override fun getLogLinesForDisplay(): Flow<List<String>> = emptyFlow()
    override fun clearUploadTempFiles(files: List<FileLogWriter.LogFile>) {}
    override suspend fun getLogFileForSharing(): File? = null
}
