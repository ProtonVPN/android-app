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

package com.protonvpn.android.telemetry

import android.content.Context
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileWriter
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TELEMETRY_FILE_NAME = "telemetry"

@Singleton
class TelemetryCache @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val mainScope: CoroutineScope,
    dispatcherProvider: VpnDispatcherProvider,
) {
    private val json = Json

    private val serialIo = dispatcherProvider.newSingleThreadDispatcher()

    private var hasReportedWriteException = false

    suspend fun load(maxTimestamp: Long): List<TelemetryEvent> =
        withContext(serialIo) {
            val file = getFile()
            if (file.exists()) {
                try {
                    file.useLines { lines ->
                        lines.map { line ->
                            json.decodeFromString<TelemetryEvent>(line)
                        }.filter {
                            it.timestamp >= maxTimestamp
                        }.toList()
                    }
                } catch (e: Throwable) {
                    ProtonLogger.logCustom(LogCategory.TELEMETRY, "Unable to read cache file: $e")
                    Sentry.captureException(TelemetryError("Unable to read cache", e))
                    emptyList()
                }
            } else {
                emptyList()
            }
        }

    fun save(events: List<TelemetryEvent>) {
        val eventsToSave = events.toList() // Make a copy in case the argument is a mutable list.
        mainScope.launch(serialIo) {
            try {
                @Suppress("BlockingMethodInNonBlockingContext")
                FileWriter(getFile()).use { writer ->
                    eventsToSave.forEach { event ->
                        writer.write(json.encodeToString(event))
                        writer.write("\n")
                    }
                }
            } catch (e: IOException) {
                ProtonLogger.logCustom(LogCategory.TELEMETRY, "Unable to save cache file: $e")
                if (!hasReportedWriteException) {
                    // Report it only once per process to avoid sending too many events.
                    Sentry.captureException(TelemetryError("Unable to write cache", e))
                    hasReportedWriteException = true
                }
            }
        }
    }

    private fun getFile(): File = File(appContext.cacheDir, TELEMETRY_FILE_NAME)
}
