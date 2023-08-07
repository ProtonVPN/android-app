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

package com.protonvpn.app.models.vpn

import com.protonvpn.test.shared.createServer
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerTests {

    @Test
    fun `test server number`() {
        val testCases = mapOf(
            "no number" to 1,
            "two #2" to 2,
            "#10" to 10,
            "large #143" to 143,
            "trailing chars #543 abc" to 543,
            "stray hash #" to 1,
            "stray # hash in the middle" to 1,
            "" to 1,
            "multiple # hashes #123" to 1,
            "first number #123 taken #321" to 123,
            "raw number 123" to 1,
            "123#" to 1,
            "zero #0" to 0,
            "# 123" to 1,
            "AA+5" to 1,
            "very long#123123123123123" to 1,
            "negative#-1234" to 1,
            "fraction#123.456" to 123,
        )
        testCases.forEach { (name, expectedResult )->
            val server = try {
                createServer(serverName = name)
            } catch (e: Throwable) {
                throw RuntimeException("Exception for case `$name`", e)
            }
            assertEquals("Invalid result for case '$name'", expectedResult, server.serverNumber)
        }
    }
}
