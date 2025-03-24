/*
 * Copyright (c) 2021 Proton AG
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

import com.protonvpn.android.utils.toSafeUtf8ByteArray
import org.junit.Assert
import org.junit.Test
import java.nio.charset.StandardCharsets

class AndroidUtilsOnDeviceTests {

    @Test
    fun testToSafeUtf8ByteArray() {
        Assert.assertArrayEquals(byteArrayOf(), "".toSafeUtf8ByteArray())
        Assert.assertArrayEquals("abc".toByteArray(), "abc".toSafeUtf8ByteArray())
        Assert.assertArrayEquals("utf8àáâą".toByteArray(StandardCharsets.UTF_8), "utf8àáâą".toSafeUtf8ByteArray())
    }
}
