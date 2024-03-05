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

package com.protonvpn.tests.base.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.base.ui.speedBytesToString
import com.protonvpn.android.base.ui.volumeBytesToString
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedAndSizeFormatTests {

    @Test
    fun speedFormattingTests() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("123.00 B/s", 123L.speedBytesToString(ctx))
        assertEquals("1.23 KB/s", 1_230L.speedBytesToString(ctx))
        assertEquals("12.30 KB/s", 12_300L.speedBytesToString(ctx))
        assertEquals("1.23 MB/s", 1_230_000L.speedBytesToString(ctx))
        assertEquals("1.23 GB/s", 1_230_000_000L.speedBytesToString(ctx))
        assertEquals("1.23 TB/s", 1_230_000_000_000L.speedBytesToString(ctx))
        assertEquals("123.00 TB/s", 123_000_000_000_000L.speedBytesToString(ctx))
    }

    @Test
    fun volumeFormattingTests() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("123.00 B", 123L.volumeBytesToString(ctx))
        assertEquals("1.23 KB", 1_230L.volumeBytesToString(ctx))
        assertEquals("12.30 KB", 12_300L.volumeBytesToString(ctx))
        assertEquals("1.23 MB", 1_230_000L.volumeBytesToString(ctx))
        assertEquals("1.23 GB", 1_230_000_000L.volumeBytesToString(ctx))
        assertEquals("1.23 TB", 1_230_000_000_000L.volumeBytesToString(ctx))
        assertEquals("123.00 TB", 123_000_000_000_000L.volumeBytesToString(ctx))
    }
}
