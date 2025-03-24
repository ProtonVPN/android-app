/*
 * Copyright (c) 2021. Proton AG
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

package com.protonvpn.tests.logging

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.logging.CurrentStateLoggerGlobal
import com.protonvpn.android.logging.FileLogWriter
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogEventType
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLoggerImpl
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private val ISO_DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
private const val TIMESTAMP = "2011-11-11T11:11:11.123Z"
private val TIMESTAMP_DATE = ISO_DATE_FORMATTER.parse(TIMESTAMP)!!
private val FIXED_CLOCK = { TIMESTAMP_DATE.time }

private val TestEvent = LogEventType(LogCategory.APP, "TEST", LogLevel.INFO)
private const val TEST_EVENT = "| INFO  | APP:TEST |"

@OptIn(ExperimentalCoroutinesApi::class)
class ProtonLoggerImplTests {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    private lateinit var testDir: File
    private lateinit var logDir: File

    @MockK
    private lateinit var currentStateLogger: CurrentStateLoggerGlobal

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testDir = File(context.cacheDir, "tests")
        logDir = File(testDir, "proton_logger")
    }

    @After
    fun teardown() {
        if (this::testDir.isInitialized) {
            testDir.deleteRecursively()
        }
    }

    @Test
    fun testLogsWritten() = runLoggerTest { logger ->
        logger.log(TestEvent, "message1")
        logger.log(TestEvent, "message2")

        assertEquals(
            testEventLines("message1", "message2"),
            File(logDir, "Data.log").readLines()
        )
    }

    @Test
    @Suppress("BlockingMethodInNonBlockingContext")
    fun testUploadFilesNotAppendedTo() = runLoggerTest { logger ->
        logger.log(TestEvent, "message1")
        logger.log(TestEvent, "message2")

        val uploadFiles = logger.getLogFilesForUpload()
        logger.log(TestEvent, "message3")

        assertEquals(1, uploadFiles.size)
        val uploadFile = uploadFiles[0].file
        assertEquals(testEventLines("message1", "message2"), uploadFile.readLines())

        logger.clearUploadTempFiles(uploadFiles)
    }

    @Test
    @Suppress("BlockingMethodInNonBlockingContext")
    fun testClearUploadTempFiles() = runLoggerTest { logger ->
        logger.log(TestEvent, "message1")
        val uploadFiles = logger.getLogFilesForUpload()
        assertEquals(1, uploadFiles.size)
        logger.clearUploadTempFiles(uploadFiles)
        assertFalse(uploadFiles.first().file.exists())
    }

    @Test
    @Suppress("BlockingMethodInNonBlockingContext")
    fun testMultilineMessage() = runLoggerTest { logger ->
        logger.log(TestEvent, "line 1\nline 2\nline 3")
        logger.logCustom(LogCategory.APP, "custom line 1\ncustom line 2")
        val uploadFile = logger.getLogFilesForUpload().first().file
        assertEquals(
            listOf(
                "$TIMESTAMP $TEST_EVENT line 1",
                " line 2",
                " line 3",
                "$TIMESTAMP | INFO  | APP | custom line 1",
                " custom line 2"
            ),
            uploadFile.readLines()
        )
    }

    private fun testEventLines(vararg msg: String): List<String> =
        msg.map { testEventLine(TIMESTAMP, it) }

    private fun testEventLine(timestamp: String, message: String) =
        "$timestamp $TEST_EVENT $message"

    private fun runLoggerTest(block: suspend CoroutineScope.(logger: ProtonLoggerImpl) -> Unit) {
        runTest(UnconfinedTestDispatcher()) {
            val logger = ProtonLoggerImpl(
                FIXED_CLOCK,
                FileLogWriter(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    backgroundScope,
                    UnconfinedTestDispatcher(),
                    logDir.absolutePath,
                    currentStateLogger
                )
            )
            block(logger)
        }
    }
}
