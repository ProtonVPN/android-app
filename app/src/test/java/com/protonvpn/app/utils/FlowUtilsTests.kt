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

import com.protonvpn.android.utils.mapState
import com.protonvpn.android.utils.tickFlow
import com.protonvpn.android.utils.withPrevious
import com.protonvpn.test.shared.runWhileCollecting
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

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

    @Test
    fun `mapState maps every value`() = runTest {
        val source = MutableStateFlow(1)
        val mapped = source.mapState { it.toString() }
        source.map {  }

        assertEquals("1", mapped.value)
        val updates = runWhileCollecting(mapped) {
            runCurrent()
            source.value = 2
            runCurrent()
            source.value = 3
            runCurrent()
        }
        assertEquals(listOf("1", "2", "3"), updates)
    }

    @Test
    fun `tick flow emits immediately and then every stepMs`() = runTest {
        val flow = tickFlow(1.seconds) { currentTime }
        val timestamps = runWhileCollecting(flow) {
            advanceTimeBy(3001)
        }
        assertEquals(listOf(0L, 1000L, 2000L, 3000L), timestamps)
    }
}
