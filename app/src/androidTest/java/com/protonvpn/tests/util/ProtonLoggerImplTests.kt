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

package com.protonvpn.tests.util

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.utils.ProtonLoggerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private const val LOG_PATTERN = "%msg"

@OptIn(ExperimentalCoroutinesApi::class)
class ProtonLoggerImplTests {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    private lateinit var testDir: File
    private lateinit var logDir: File

    // Use an explicit dispatcher because it needs to be passed to ProtonLoggerImpl
    private lateinit var testDispatcher: TestCoroutineDispatcher

    @Before
    fun setup() {
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
        logger.log("message1")
        logger.log("message2")

        assertEquals(listOf("message1", "message2"), File(logDir, "Data.log").readLines())
    }

    @Test
    fun testGetLogLines() = runLoggerTest { logger ->
        logger.log("message1")
        logger.log("message2")
        val logLines = mutableListOf<String>()
        val collectJob = launch {
            logger.getLogLines().toList(logLines)
        }
        assertEquals(listOf("message1", "message2"), logLines)

        logger.log("message3")
        assertEquals(listOf("message1", "message2", "message3"), logLines)

        collectJob.cancel()
    }

    @Test
    @Suppress("BlockingMethodInNonBlockingContext")
    fun testUploadFilesNotAppendedTo() = runLoggerTest { logger ->
        logger.log("message1")
        logger.log("message2")

        val uploadFiles = logger.getLogFilesForUpload()
        logger.log("message3")

        assertEquals(1, uploadFiles.size)
        val uploadFile = uploadFiles[0].file
        assertEquals(listOf("message1", "message2"), uploadFile.readLines())

        logger.clearUploadTempFiles(uploadFiles)
    }

    @Test
    @Suppress("BlockingMethodInNonBlockingContext")
    fun testClearUploadTempFiles() = runLoggerTest { logger ->
        logger.log("message1")
        val uploadFiles = logger.getLogFilesForUpload()
        assertEquals(1, uploadFiles.size)
        logger.clearUploadTempFiles(uploadFiles)
        assertFalse(uploadFiles.first().file.exists())
    }

    private fun runLoggerTest(block: suspend CoroutineScope.(logger: ProtonLoggerImpl) -> Unit) {
        testDispatcher.runBlockingTest {
            // Logger needs a scope to run its processing. This scope needs to be cancelled before
            // runBlockingTest block finishes.
            val loggerScope = CoroutineScope(EmptyCoroutineContext + testDispatcher)
            val logger = ProtonLoggerImpl(
                InstrumentationRegistry.getInstrumentation().targetContext,
                loggerScope,
                testDispatcher,
                logDir.absolutePath,
                LOG_PATTERN
            )
            block(logger)
            loggerScope.cancel()
        }
    }
}
