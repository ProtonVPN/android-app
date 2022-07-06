/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.app.vpn

import com.protonvpn.android.vpn.LocalAgentUnreachableTracker
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestCoroutineScope
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val MIN_RETRY_TIME_MS = 30_000
private const val MAX_JITTER_MS = 6500 // Includes some margin to avoid rounding errors.
private const val MAX_RETRY_TIME_MS = MIN_RETRY_TIME_MS + MAX_JITTER_MS

@OptIn(ExperimentalCoroutinesApi::class)
class LocalAgentUnreachableTrackerTests {

    @RelaxedMockK
    private lateinit var mockNetworkManager: NetworkManager
    private lateinit var networkStatusFlow: MutableSharedFlow<NetworkStatus>

    private lateinit var unreachableTracker: LocalAgentUnreachableTracker
    private lateinit var testCoroutineScope: TestCoroutineScope
    private var clockMs = 10_000L

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        networkStatusFlow = MutableSharedFlow(extraBufferCapacity = 1)
        every { mockNetworkManager.observe() } returns networkStatusFlow

        testCoroutineScope = TestCoroutineScope()
        clockMs = 10_000L
        unreachableTracker = LocalAgentUnreachableTracker({ clockMs }, testCoroutineScope, mockNetworkManager)
    }

    @Test
    fun `when unreachable errors before minimum time then fallback is not triggered`() {
        repeat(3) {
            val shouldFallback = unreachableTracker.onUnreachable()
            assertFalse(shouldFallback)
            clockMs += MIN_RETRY_TIME_MS / 3
        }
    }

    @Test
    fun `when unreachable error after minimum time then fallback is triggered`() {
        repeat(3) {
            val shouldFallback = unreachableTracker.onUnreachable()
            assertFalse(shouldFallback)
            clockMs += MAX_RETRY_TIME_MS / 3
        }
        assertTrue(unreachableTracker.onUnreachable())
    }

    @Test
    fun `when fallback is triggered the interval time increases exponentially`() {
        unreachableTracker.onUnreachable()
        clockMs += MAX_RETRY_TIME_MS

        unreachableTracker.onFallbackTriggered()
        clockMs += 4 * MIN_RETRY_TIME_MS - 1
        assertFalse(unreachableTracker.onUnreachable())
        clockMs += 4 * MAX_JITTER_MS
        assertTrue(unreachableTracker.onUnreachable())

        unreachableTracker.onFallbackTriggered()
        clockMs += 9 * MIN_RETRY_TIME_MS - 1
        assertFalse(unreachableTracker.onUnreachable())
        clockMs += 9 * MAX_JITTER_MS
        assertTrue(unreachableTracker.onUnreachable())
    }

    @Test
    fun `when network changes while unreachable then minimal interval is used`() {
        unreachableTracker.onUnreachable()
        unreachableTracker.onFallbackTriggered()
        networkStatusFlow.tryEmit(NetworkStatus.Unmetered)
        clockMs += MAX_RETRY_TIME_MS
        assertTrue(unreachableTracker.onUnreachable())
    }
}
