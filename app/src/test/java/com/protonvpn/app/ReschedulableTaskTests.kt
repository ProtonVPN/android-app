/*
 * Copyright (c) 2019 Proton AG
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

import com.protonvpn.android.utils.ReschedulableTask
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class ReschedulableTaskTests {

    private var time = 0L
    private var counter = 0

    @Before
    fun setup() {
        time = 0L
        counter = 0
    }

    @Test
    fun testSchedule() = runTest {
        val task = ReschedulableTask(this, ::time) { counter++ }
        task.scheduleIn(5)
        delay(3)
        Assert.assertEquals(0, counter)
        delay(3)
        Assert.assertEquals(1, counter)
    }

    @Test
    fun testScheduleCancel() = runTest {
        val task = ReschedulableTask(this, ::time) { counter++ }
        task.scheduleIn(5)
        task.cancelSchedule()
        delay(10)
        Assert.assertEquals(0, counter)
    }

    @Test
    fun testScheduleFromAction() = runTest {
        val task = ReschedulableTask(this, ::time) {
            counter++
            if (counter == 1)
                scheduleIn(1)
        }
        task.scheduleIn(1)
        delay(2)
        Assert.assertEquals(1, counter)
        delay(2)
        Assert.assertEquals(2, counter)
    }

    @Test
    fun testRescheduleLater() = runTest {
        val task = ReschedulableTask(this, ::time) { counter++ }

        task.scheduleIn(5)
        task.scheduleIn(10)
        delay(6)

        Assert.assertEquals(0, counter)
        delay(5)
        Assert.assertEquals(1, counter)
    }

    @Test
    fun testRescheduleEarlier() = runTest {
        val task = ReschedulableTask(this, ::time) { counter++ }

        task.scheduleIn(10)
        task.scheduleIn(5)
        delay(6)

        Assert.assertEquals(1, counter)
    }

    @Test
    fun testDelay0() = runTest {
        val task = ReschedulableTask(this, ::time) { counter++ }

        task.scheduleIn(0)
        task.cancelSchedule()

        // Make sure task didn't run synchronously
        Assert.assertEquals(0, counter)

        // Make sure task scheduled for immediate execution (delay=0) runs despite cancelSchedule()
        delay(1)
        Assert.assertEquals(1, counter)
    }
}
