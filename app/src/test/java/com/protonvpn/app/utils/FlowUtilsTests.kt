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

package com.protonvpn.app.utils

import com.protonvpn.android.utils.withPrevious
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowUtilsTests {

    @Test
    fun `withPrevious emits pairs of values`() = runTest {
        val allEmissions = flowOf(1, 2, 3, 4).withPrevious().toList()
        assertEquals(listOf(Pair(1, 2), Pair(2, 3), Pair(3, 4)), allEmissions)
    }

    @Test
    fun `withPrevious doesn't emit when there is only one element in source flow`() = runTest {
        val result = flowOf(1).withPrevious().firstOrNull()
        assertEquals(null, result)
    }

    @Test
    fun `withPrevious works with nullable values`() = runTest {
        val result = flowOf(null, null, null, 1).withPrevious().toList()
        assertEquals(listOf(Pair(null, null), Pair(null, null), Pair(null, 1)), result)
    }
}
