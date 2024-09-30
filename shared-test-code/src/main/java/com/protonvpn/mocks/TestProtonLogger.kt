/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.mocks

import com.protonvpn.android.logging.FileLogWriter
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogEventType
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLoggerInterface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.File

/**
 * A logger that prints to the console. Useful in more complex unit tests.
 */
class TestProtonLogger : ProtonLoggerInterface {
    override fun log(event: LogEventType, message: String) {
        println("${event.category.toLog()}:${event.name} | $message")
    }

    override fun logCustom(category: LogCategory, message: String) {
        println("${category.toLog()} | $message")
    }

    override fun logCustom(level: LogLevel, category: LogCategory, message: String) {
        logCustom(category, message)
    }

    override fun logBlocking(event: LogEventType, message: String) {
        log(event, message)
    }

    override fun formatTime(timeMs: Long): String = ""

    override suspend fun getLogFilesForUpload(): List<FileLogWriter.LogFile> = emptyList()

    override fun getLogLinesForDisplay(): Flow<List<String>> = emptyFlow()

    override fun clearUploadTempFiles(files: List<FileLogWriter.LogFile>) {
    }

    override suspend fun getLogFileForSharing(): File? = null
}
