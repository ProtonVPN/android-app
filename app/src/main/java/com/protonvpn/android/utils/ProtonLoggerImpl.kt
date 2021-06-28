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
package com.protonvpn.android.utils

import android.content.Context
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.android.LogcatAppender
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase
import ch.qos.logback.core.encoder.Encoder
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy
import ch.qos.logback.core.util.FileSize
import ch.qos.logback.core.util.StatusPrinter
import com.protonvpn.android.BuildConfig
import io.sentry.Sentry
import io.sentry.event.Event
import io.sentry.event.EventBuilder
import io.sentry.event.interfaces.ExceptionInterface
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.GregorianCalendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

private const val LOG_PATTERN = "%d{HH:mm:ss}%property{timeZone}: %msg"
private const val LOG_QUEUE_MAX_SIZE = 100
private const val LOG_ROTATE_SIZE = "300kb"

open class ProtonLoggerImpl(
    appContext: Context,
    private val mainScope: CoroutineScope,
    loggerDispatcher: CoroutineDispatcher,
    logDir: String,
    logPattern: String = LOG_PATTERN
) {
    data class LogFile(val name: String, val file: File)

    private class BackgroundLogger(
        private val appContext: Context,
        mainScope: CoroutineScope,
        private val loggerDispatcher: CoroutineDispatcher,
        private val messages: Flow<String>,
        private val logDir: String,
        private val logPattern: String,
        uniqueLoggerName: String
    ) {

        private lateinit var logContext: LoggerContext

        private val fileName = "Data.log"
        private val fileName2 = "Data1.log"
        private val logger =
            LoggerFactory.getLogger(uniqueLoggerName) as ch.qos.logback.classic.Logger

        init {
            mainScope.launch(loggerDispatcher) {
                initialize()
                clearUploadTempFiles()
                processLogs()
            }
        }

        /**
         * Copy log files to a temporary location so that they can be safely uploaded without
         * additional data being appended to them. The files will be deleted by clearUploadTempFiles
         * called after initialization.
         */
        @Throws(IOException::class)
        suspend fun getFilesForUpload(): List<LogFile> =
            withContext(loggerDispatcher) {
                val logFiles: MutableList<LogFile> = mutableListOf()
                val temporaryDirectory = getUploadTempFilesDir()
                temporaryDirectory.mkdir()
                getLogFiles().forEach { file ->
                    val temporaryFile = File.createTempFile(file.name, null, temporaryDirectory)
                    file.copyTo(temporaryFile, overwrite = true)
                    temporaryFile.deleteOnExit()

                    logFiles.add(LogFile(file.name, temporaryFile))
                }
                logFiles
            }

        suspend fun clearUploadTempFiles(files: List<LogFile>) {
            withContext(loggerDispatcher) {
                val temporaryDirectory = getUploadTempFilesDir()
                if (temporaryDirectory.exists()) {
                    try {
                        files.forEach { it.file.delete() }
                    } catch (e: IOException) {
                        logException("Unable to clear temporary upload log file.", e)
                    }
                }
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        fun getLogLines(): Flow<String> = callbackFlow {
            getLogFiles().forEach { file ->
                file.bufferedReader().use { reader ->
                    reader.lineSequence()
                        .takeWhile { isActive }
                        .forEach { line -> send(line) }
                }
            }
            val encoder = createAndStartEncoder(logger.loggerContext, logPattern)
            val appender = ChannelAdapter(this, encoder)
            appender.start()
            logger.addAppender(appender)
            awaitClose { logger.detachAppender(appender) }
        }.flowOn(loggerDispatcher)

        private fun initialize() {
            logContext = LoggerFactory.getILoggerFactory() as LoggerContext

            val patternEncoder = createAndStartEncoder(logContext, "$logPattern%n")

            val fileAppender = RollingFileAppender<ILoggingEvent>().apply {
                this.context = logContext
                file = "$logDir/$fileName"
            }

            val rollingPolicy = FixedWindowRollingPolicy().apply {
                this.context = logContext
                setParent(fileAppender)
                fileNamePattern = "$logDir/Data%i.log"
                minIndex = 1
                maxIndex = 2
                start()
            }

            val triggerPolicy = SizeBasedTriggeringPolicy<ILoggingEvent>().apply {
                maxFileSize = FileSize.valueOf(LOG_ROTATE_SIZE)
                this.context = logContext
                start()
            }

            fileAppender.triggeringPolicy = triggerPolicy
            fileAppender.rollingPolicy = rollingPolicy

            val logcatAppender = LogcatAppender().apply {
                this.context = logContext
                encoder = patternEncoder
                start()
            }

            fileAppender.encoder = patternEncoder
            fileAppender.start()

            logger.addAppender(fileAppender)
            logger.addAppender(logcatAppender)

            StatusPrinter.print(logContext)
        }

        private suspend fun processLogs() {
            messages.collect { message ->
                logContext.putProperty("timeZone", timeZoneSuffix(GregorianCalendar()))
                logger.debug(message)
            }
        }

        private fun getLogFiles(): List<File> {
            val logFile = File(logDir, fileName)
            val logFile2 = File(logDir, fileName2)
            val list = ArrayList<File>()
            if (logFile.exists() && logFile2.exists() && logFile.lastModified() < logFile2.lastModified()) {
                list.add(logFile)
                list.add(logFile2)
            } else {
                if (logFile2.exists()) {
                    list.add(logFile2)
                }
                if (logFile.exists()) {
                    list.add(logFile)
                }
            }
            return list
        }

        private fun clearUploadTempFiles() {
            val dir = getUploadTempFilesDir()
            if (dir.exists()) {
                try {
                    dir.deleteRecursively()
                } catch (e: IOException) {
                    logException("Unable to clear temporary upload log files.", e)
                }
            }
        }

        private fun getUploadTempFilesDir(): File = File(appContext.cacheDir, "log_upload")

        private fun createAndStartEncoder(
            loggerContext: LoggerContext,
            pattern: String
        ) = PatternLayoutEncoder().apply {
            this.context = loggerContext
            this.pattern = pattern
            start()
        }

        private class ChannelAdapter(
            private val channel: SendChannel<String>,
            private val encoder: Encoder<ILoggingEvent>
        ) : UnsynchronizedAppenderBase<ILoggingEvent>() {

            override fun append(eventObject: ILoggingEvent) {
                val line = encoder.encode(eventObject).decodeToString()
                channel.sendBlocking(line)
            }
        }
    }

    private val logMessageQueue =
        MutableSharedFlow<String>(10, LOG_QUEUE_MAX_SIZE, BufferOverflow.DROP_LATEST)

    private val backgroundLogger = BackgroundLogger(
        appContext,
        mainScope,
        loggerDispatcher,
        logMessageQueue,
        logDir,
        logPattern,
        getUniqueLoggerName()
    )

    fun logSentryEvent(event: Event) {
        if (!BuildConfig.DEBUG) {
            Sentry.capture(event)
        }
        log(event.message)
    }

    fun log(message: String) {
        logMessageQueue.tryEmit(message)
    }

    fun getLogLines() = backgroundLogger.getLogLines()

    @Suppress("BlockingMethodInNonBlockingContext")
    @Throws(IOException::class)
    suspend fun getLogFilesForUpload(): List<LogFile> = backgroundLogger.getFilesForUpload()

    @Suppress("BlockingMethodInNonBlockingContext")
    fun getLogFilesForUpload(callback: (List<LogFile>) -> Unit) {
        mainScope.launch {
            val files = try {
                backgroundLogger.getFilesForUpload()
            } catch (e: IOException) {
                logException("Unable to prepare logs for upload", e)
                emptyList<LogFile>()
            }
            callback(files)
        }
    }

    fun clearUploadTempFiles(files: List<LogFile>) {
        mainScope.launch {
            backgroundLogger.clearUploadTempFiles(files)
        }
    }

    companion object {
        private var instanceNumber = 0

        private fun getUniqueLoggerName(): String = synchronized(this) {
            val uniqueName = "ProtonLogger_$instanceNumber"
            ++instanceNumber
            return uniqueName
        }
    }
}

private fun logException(message: String, throwable: Throwable) {
    val event = EventBuilder()
        .withMessage(message)
        .withSentryInterface(ExceptionInterface(throwable))
        .build()
    ProtonLogger.logSentryEvent(event)
}

fun timeZoneSuffix(time: GregorianCalendar): String {
    val timeZoneOffsetMs = time.timeZone.getOffset(time.timeInMillis)
    val timeZoneOffsetH = TimeUnit.MILLISECONDS.toMinutes(timeZoneOffsetMs.toLong()) / 60.0
    return (if (timeZoneOffsetH % 1.0 == 0.0) "%+.0f" else "%+.1f").format(Locale.US, timeZoneOffsetH)
}
