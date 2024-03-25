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

package com.protonvpn.app.logging

import com.protonvpn.android.logging.FileLogWriter
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLoggerImpl
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

private const val TIMESTAMP_TEXT = "2011-11-11T11:11:11.123Z"
private const val TIMESTAMP = 1321009871123L

class ProtonLoggerImplTests {

    @Test
    fun `logged messages use UTC time`() = withCustomTimeZone(TimeZone.getTimeZone("GMT+5")) {
        val logLines = mutableListOf<Pair<String, String>>()
        val mockLogWriter = mockk<FileLogWriter>()
        every { mockLogWriter.write(any(), any(), any(), any(), any(), any()) } answers {
            logLines.add(args[0] as String to args[4] as String)
        }

        val wallClockUtc = TIMESTAMP
        val logger = ProtonLoggerImpl(
            wallClock = { wallClockUtc },
            mockLogWriter
        )
        logger.logCustom(LogCategory.APP, "message")

        val expected = TIMESTAMP_TEXT to "message"
        assertEquals(expected, logLines.first())
    }

    @Test
    fun `local time is used in log lines for display`() = withCustomTimeZone(TimeZone.getTimeZone("GMT+5")) {
        val mockLogWriter = mockk<FileLogWriter>()
        every { mockLogWriter.getLogLinesForDisplay() } returns flowOf(listOf("$TIMESTAMP_TEXT message"))

        val wallClockUtc = TIMESTAMP
        val logger = ProtonLoggerImpl(
            wallClock = { wallClockUtc },
            mockLogWriter
        )

        val logLines = runBlocking {
            logger.getLogLinesForDisplay().toList()
        }
        assertEquals(listOf("16:11:11.123 message"), logLines.first())
    }

    private fun withCustomTimeZone(tz: TimeZone, block: () -> Unit) {
        val originalTZ = TimeZone.getDefault()
        TimeZone.setDefault(tz)
        try {
            block()
        } finally {
            TimeZone.setDefault(originalTZ)
        }
    }
}
