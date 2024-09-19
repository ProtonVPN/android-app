/*
 * Copyright (c) 2024 Proton AG
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
package com.protonvpn.android

import android.content.Context
import android.os.Build
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.logging.AppCrash
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.SentryLogScrubber
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import io.sentry.Sentry
import io.sentry.SentryEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.proton.core.featureflag.domain.ExperimentalProtonFeatureFlag
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureId
import okhttp3.internal.closeQuietly
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_CHARS = 4000
private const val CHANNEL_BUFFER_SIZE = 40
private const val REPORTER_DELAY_MS = 10_000L

@OptIn(ExperimentalProtonFeatureFlag::class)
@Reusable
class GoLangSendCrashesToSentryEnabled @Inject constructor(
    private val currentUser: CurrentUser,
    private val featureFlagManager: FeatureFlagManager
) {
    suspend operator fun invoke() =
        Build.VERSION.SDK_INT >= 30 && featureFlagManager.getValue(currentUser.user()?.userId, FeatureId(FLAG_ID))

    companion object {
        const val FLAG_ID = "GoLangSendCrashesToSentryEnabled"
    }
}

@Singleton
class GoLangCrashReporter @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val mainScope: CoroutineScope,
    private val dispatcherProvider: VpnDispatcherProvider,
    private val goLangSendCrashesToSentryEnabled: GoLangSendCrashesToSentryEnabled,
) {
    fun start() {
        mainScope.launch(dispatcherProvider.Io) {
            if (goLangSendCrashesToSentryEnabled()) {
                // This is costly, let's postpone it until after the launch.
                delay(REPORTER_DELAY_MS)

                val file = appContext.goErrorLogFile()
                if (file.exists()) {
                    try {
                        val content = file.readLines()
                        if (content.isNotEmpty()) {
                            val firstLine = content.first()
                            val event = SentryEvent(GoLangCrash(firstLine))
                            event.setExtra("stack", content.joinToString("\n"))
                            event.setExtra("stack_time", Date(file.lastModified()).toGMTString())
                            event.fingerprints = extractFingerprints(content)
                            Sentry.captureEvent(event)
                            ProtonLogger.log(AppCrash, "GoLang crash report: $firstLine")
                        }
                    } catch (e: IOException) {
                        ProtonLogger.logCustom(
                            LogCategory.APP,
                            "Error reading GoLang crash log: ${e.message}"
                        )
                    } finally {
                        file.delete()
                    }
                }
            }
        }
    }

    private fun extractFingerprints(content: List<String>): List<String> = buildList {
        // Filter out hex digits from the fingerprint to prevent addresses from splitting otherwise identical issue.
        // This will replace characters from text but grouping should still work well based on the remaining chars.
        val messageLine = content.first().replace("[0-9A-Fa-f]".toRegex(), "_")
        add(messageLine)
        if (content.first().endsWith("send on closed channel")) {
            val channelCloseFrame = content
                .drop(1)
                .find { it.isNotBlank() && !it.startsWith("goroutine") }
            if (channelCloseFrame != null)
                add(channelCloseFrame)
        }
    }
}

@Singleton
class GoLangCrashLogger @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val mainScope: CoroutineScope,
    private val dispatcherProvider: VpnDispatcherProvider,
    private val sentryLogScrubber: dagger.Lazy<SentryLogScrubber>,
) {
    private val channel = Channel<String>(CHANNEL_BUFFER_SIZE, BufferOverflow.DROP_LATEST)
    private var consumer : Job? = null

    fun onErrorLine(line: String) {
        if (consumer == null) {
            // Ignore lines before panic
            if (!line.isCrashHeader()) return

            startConsumer()
        }

        channel.trySend(sentryLogScrubber.get().scrubMessage(line))
    }

    private fun startConsumer() {
        consumer = mainScope.launch(dispatcherProvider.infiniteIo) {
            var output: FileOutputStream? = null
            var charsWritten = 0
            try {
                // In GoLang callstacks there can be couple of header lines,
                // let's capture them all.
                var wasHeader = false
                while (true) {
                    val line = channel.receive()
                    val isHeader = line.isCrashHeader()
                    if (!wasHeader && isHeader) {
                        output?.closeQuietly()
                        output = FileOutputStream(appContext.goErrorLogFile(), false)
                        charsWritten = 0
                    }
                    wasHeader = isHeader
                    output?.let {
                        val newCharsWritten = charsWritten + line.length + 1
                        if (newCharsWritten < MAX_CHARS) {
                            output?.write(line.toByteArray())
                            output?.write("\n".toByteArray())
                            charsWritten = newCharsWritten
                        } else {
                            output?.closeQuietly()
                            output = null
                        }
                    }
                }
            } catch (e: IOException) {
                ProtonLogger.logCustom(LogCategory.APP, "Error writing GoLang crash log: ${e.message}")
            } finally {
                output?.closeQuietly()
            }
        }
    }

    private fun String.isCrashHeader() =
        startsWith("panic: ") || startsWith("runtime: ") || startsWith("fatal error: ") || startsWith("throw: ")
}

private fun Context.goErrorLogFile() = File(applicationInfo.dataDir + "/log", "go_errors.log")

class GoLangCrash(message: String) : Throwable("Native crash in GoLang library: $message")
