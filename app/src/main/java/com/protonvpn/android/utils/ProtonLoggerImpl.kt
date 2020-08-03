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
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy
import ch.qos.logback.core.util.FileSize
import ch.qos.logback.core.util.StatusPrinter
import com.protonvpn.android.BuildConfig
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.slf4j.Logger.ROOT_LOGGER_NAME
import org.slf4j.LoggerFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

@ExperimentalCoroutinesApi
open class ProtonLoggerImpl(val scope: CoroutineScope, appContext: Context) {

    protected val logDir = appContext.applicationInfo.dataDir + "/log/"
    protected val fileName = "Data.log"
    protected val fileName2 = "Data1.log"
    val newItemsChannel = BroadcastChannel<String>(Channel.BUFFERED)
    private val simpleDateFormat = SimpleDateFormat("HH:mm:ss")
    private val logger = LoggerFactory.getLogger(ProtonLoggerImpl::class.java) as ch.qos.logback.classic.Logger

    init {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext

        val patternEncoder = PatternLayoutEncoder().apply {
            this.context = context
            pattern = "%d{HH:mm:ss}: %msg%n"
            start()
        }

        val fileAppender = RollingFileAppender<ILoggingEvent>().apply {
            this.context = context
            file = logDir + fileName
        }

        val rollingPolicy = FixedWindowRollingPolicy().apply {
            this.context = context
            setParent(fileAppender)
            fileNamePattern = "$logDir/Data%i.log"
            minIndex = 1
            maxIndex = 2
            start()
        }

        val triggerPolicy = SizeBasedTriggeringPolicy<ILoggingEvent>().apply {
            maxFileSize = FileSize.valueOf("300kb")
            this.context = context
            start()
        }

        fileAppender.triggeringPolicy = triggerPolicy
        fileAppender.rollingPolicy = rollingPolicy

        val logcatAppender = LogcatAppender().apply {
            this.context = context
            encoder = patternEncoder
            start()
        }

        fileAppender.encoder = patternEncoder
        fileAppender.start()
        val root = context.getLogger(ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        root.addAppender(fileAppender)
        root.addAppender(logcatAppender)

        StatusPrinter.print(context)
    }

    fun getLogFiles(): List<File> {
        val logFile = File(ProtonLogger.logDir, ProtonLogger.fileName)
        val logFile2 = File(ProtonLogger.logDir, ProtonLogger.fileName2)
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

    fun log(message: String, captureInSentry: Boolean = false) {
        logger.debug(message)
        val timeStamp: String = simpleDateFormat.format(Date())
        scope.launch {
            newItemsChannel.send("$timeStamp: $message")
        }
        if (!BuildConfig.DEBUG && captureInSentry) {
            Sentry.capture(message)
        }
    }
}
