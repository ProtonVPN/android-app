/*
 * Copyright (c) 2021 Proton Technologies AG
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
package com.protonvpn.app

import com.protonvpn.android.utils.parallelFirstOrNull
import com.protonvpn.android.utils.parallelSearch
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.IllegalStateException

class CollectionToolsTests {

    @Test
    fun testParallelFirstResult() = runBlockingTest {
        Assert.assertEquals(null, listOf<Int>().parallelFirstOrNull { it > 0 })
        Assert.assertEquals(1, listOf(1).parallelFirstOrNull { it > 0 })
        Assert.assertEquals(null, listOf(-1).parallelFirstOrNull { it > 0 })
        Assert.assertEquals(null, listOf(-1, -5).parallelFirstOrNull { it > 0 })
        Assert.assertEquals(3, listOf(-1, -5, 3).parallelFirstOrNull { it > 0 })
    }

    @Test
    fun testParallelFirstCancel() = runBlockingTest {
        val start = currentTime
        Assert.assertEquals(2, listOf(1, 2).parallelFirstOrNull {
            if (it == 1) {
                delay(10)
                throw IllegalStateException("Should have been cancelled")
            } else
                delay(2)
            true
        })
        val elapsed = currentTime - start
        assertTrue(elapsed in 2..9)
    }

    @Test
    fun testParallelSearch() = runBlockingTest {
        Assert.assertEquals(setOf(7, 3), listOf(-1, 7, -5, 3).parallelSearch(true) { it > 0 }.toSet())

        val result = listOf(-1, 7, -5, 3).parallelSearch(false) { it > 0 }
        Assert.assertEquals(1, result.size)
        Assert.assertTrue(result.first() == 7 || result.first() == 3)
    }
}
