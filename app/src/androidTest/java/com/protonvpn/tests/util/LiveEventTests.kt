/*
 * Copyright (c) 2019 Proton Technologies AG
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.protonvpn.android.utils.LiveEvent
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveEventTests {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private val lifecycleOwner = TestLifecycleOwner()
    private lateinit var event: LiveEvent
    private var notified = false

    @Before
    fun setup() {
        event = LiveEvent()
        notified = false
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    @Test
    fun testNoNotificationOnBind() {
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        event.emit()
        event.observe(lifecycleOwner) {
            notified = true
        }
        Assert.assertFalse(notified)

        event.emit()
        Assert.assertTrue(notified)
    }

    @Test
    fun testEmitWhenObserverNotActive() {
        event.observe(lifecycleOwner) {
            notified = true
        }
        event.emit()
        Assert.assertFalse(notified)

        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        Assert.assertTrue(notified)
    }
}
