/*
 * Copyright (c) 2021. Proton Technologies AG
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.joda.time.DateTimeZone
import org.joda.time.format.ISODateTimeFormat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private const val TIMESTAMP = "2011-11-11T11:11:11.123Z"
private val TIMESTAMP_DATE = ISODateTimeFormat.dateTimeParser().parseDateTime(TIMESTAMP)
private val FIXED_CLOCK = { TIMESTAMP_DATE.millis }

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

    // Use an explicit dispatcher because it needs to be passed to ProtonLoggerImpl
    private lateinit var testDispatcher: TestCoroutineDispatcher

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testDir = File(context.cacheDir, "tests")
        logDir = File(testDir, "proton_logger")
        testDispatcher = TestCoroutineDispatcher()
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
    fun testGetLogLinesForDisplay() = runLoggerTest { logger ->
        logger.log(TestEvent, "message1")
        logger.log(TestEvent, "message2")
        val logLines = mutableListOf<String>()

        val originalTZ = DateTimeZone.getDefault()
        val timeZone = DateTimeZone.forOffsetHours(5)
        DateTimeZone.setDefault(timeZone)
        try {
            val collectJob = launch {
                logger.getLogLinesForDisplay().toList(logLines)
            }
            val localTime = TIMESTAMP_DATE.withZone(timeZone).toLocalTime().toString()

            assertEquals(
                listOf("message1", "message2").map { testEventLine(localTime, it) },
                logLines
            )

            logger.log(TestEvent, "message3")
            assertEquals(
                listOf("message1", "message2", "message3").map { testEventLine(localTime, it) },
                logLines
            )

            collectJob.cancel()
        } finally {
            DateTimeZone.setDefault(originalTZ)
        }

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

    @Test
    @Suppress("BlockingMethodInNonBlockingContext")
    fun testUtcTimestamp() = runLoggerTest { logger ->
        val originalTZ = DateTimeZone.getDefault()
        arrayOf(DateTimeZone.forOffsetHours(1), DateTimeZone.forOffsetHours(5)).forEach { tz ->
            DateTimeZone.setDefault(tz)
            logger.log(TestEvent, tz.toString())
        }
        DateTimeZone.setDefault(originalTZ)

        val uploadFiles = logger.getLogFilesForUpload()
        assertEquals(1, uploadFiles.size)
        val uploadFile = uploadFiles[0].file
        assertEquals(
            listOf("$TIMESTAMP $TEST_EVENT +01:00", "$TIMESTAMP $TEST_EVENT +05:00"),
            uploadFile.readLines()
        )
    }

    private fun testEventLines(vararg msg: String): List<String> =
        msg.map { testEventLine(TIMESTAMP, it) }

    private fun testEventLine(timestamp: String, message: String) =
        "$timestamp $TEST_EVENT $message"

    private fun runLoggerTest(block: suspend CoroutineScope.(logger: ProtonLoggerImpl) -> Unit) {
        testDispatcher.runBlockingTest {
            // Logger needs a scope to run its processing. This scope needs to be cancelled before
            // runBlockingTest block finishes.
            val loggerScope = CoroutineScope(EmptyCoroutineContext + testDispatcher)
            val logger = ProtonLoggerImpl(
                FIXED_CLOCK,
                FileLogWriter(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    loggerScope,
                    testDispatcher,
                    logDir.absolutePath,
                    currentStateLogger
                )
            )
            block(logger)
            loggerScope.cancel()
        }
    }
}
