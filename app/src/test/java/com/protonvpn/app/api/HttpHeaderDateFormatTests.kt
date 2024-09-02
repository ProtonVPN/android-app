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

package com.protonvpn.app.api

import com.protonvpn.android.api.httpHeaderDateFormatter
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoField
import kotlin.test.assertEquals

class HttpHeaderDateFormatTests {

    @Test
    fun formatEpoch() {
        assertEquals("Thu, 01 Jan 1970 00:00:00 GMT", httpHeaderDateFormatter.format(Instant.ofEpochMilli(0)))
    }

    @Test
    fun formatSpecificDate() {
        val dateTime = ZonedDateTime.of(2024, 7, 9, 15, 59, 10, 0, ZoneId.of("GMT"))
        val formatted = httpHeaderDateFormatter.format(dateTime)
        assertEquals("Tue, 09 Jul 2024 15:59:10 GMT", formatted)

        val parsed = httpHeaderDateFormatter.parse(formatted)
        assertEquals(dateTime.toInstant().epochSecond, parsed.getLong(ChronoField.INSTANT_SECONDS))
    }

    @Test
    fun formatSpecificDateWithZone() {
        val dateTime = ZonedDateTime.of(2024, 7, 9, 15, 59, 10, 0, ZoneOffset.ofHours(1))
        val formatted = httpHeaderDateFormatter.format(dateTime)
        assertEquals("Tue, 09 Jul 2024 14:59:10 GMT", formatted)

        val parsed = httpHeaderDateFormatter.parse(formatted)
        assertEquals(dateTime.toInstant().epochSecond, parsed.getLong(ChronoField.INSTANT_SECONDS))
    }
}
