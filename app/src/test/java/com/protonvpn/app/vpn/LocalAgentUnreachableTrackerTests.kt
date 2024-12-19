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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkStatus
import org.junit.Assert.assertEquals
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
    private lateinit var testCoroutineScope: TestScope
    private var clockMs = 10_000L

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        networkStatusFlow = MutableSharedFlow(extraBufferCapacity = 1)
        every { mockNetworkManager.observe() } returns networkStatusFlow

        testCoroutineScope = TestScope(UnconfinedTestDispatcher())
        clockMs = 10_000L
        unreachableTracker = LocalAgentUnreachableTracker({ clockMs }, testCoroutineScope.backgroundScope, mockNetworkManager)
    }

    @Test
    fun `when unreachable errors before minimum time then fallback is not triggered`() {
        repeat(3) {
            val action = unreachableTracker.onUnreachable()
            assertEquals(LocalAgentUnreachableTracker.UnreachableAction.ERROR, action)
            clockMs += MIN_RETRY_TIME_MS / 3
        }
    }

    @Test
    fun `when unreachable error after minimum time then fallback is triggered`() {
        repeat(3) {
            val action = unreachableTracker.onUnreachable()
            assertEquals(LocalAgentUnreachableTracker.UnreachableAction.ERROR, action)
            clockMs += MAX_RETRY_TIME_MS / 3
        }
        assertEquals(LocalAgentUnreachableTracker.UnreachableAction.FALLBACK, unreachableTracker.onUnreachable())
    }


    @Test
    fun `when unreachable error happens while connected then the first response is silent`() {
        unreachableTracker.reset(connected = true)
        assertEquals(
            LocalAgentUnreachableTracker.UnreachableAction.SILENT_RECONNECT,
            unreachableTracker.onUnreachable()
        )
        `when unreachable error after minimum time then fallback is triggered`()
    }

    @Test
    fun `when fallback is triggered the interval time increases exponentially`() {
        unreachableTracker.onUnreachable()
        clockMs += MAX_RETRY_TIME_MS

        unreachableTracker.onFallbackTriggered()
        clockMs += 4 * MIN_RETRY_TIME_MS - 1
        assertEquals(LocalAgentUnreachableTracker.UnreachableAction.ERROR, unreachableTracker.onUnreachable())
        clockMs += 4 * MAX_JITTER_MS
        assertEquals(LocalAgentUnreachableTracker.UnreachableAction.FALLBACK, unreachableTracker.onUnreachable())

        unreachableTracker.onFallbackTriggered()
        clockMs += 9 * MIN_RETRY_TIME_MS - 1
        assertEquals(LocalAgentUnreachableTracker.UnreachableAction.ERROR, unreachableTracker.onUnreachable())
        clockMs += 9 * MAX_JITTER_MS
        assertEquals(LocalAgentUnreachableTracker.UnreachableAction.FALLBACK, unreachableTracker.onUnreachable())
    }

    @Test
    fun `when network changes while unreachable then minimal interval is used`() = testCoroutineScope.runTest {
        unreachableTracker.onUnreachable()
        unreachableTracker.onFallbackTriggered()
        networkStatusFlow.tryEmit(NetworkStatus.Unmetered)
        clockMs += MAX_RETRY_TIME_MS
        assertEquals(LocalAgentUnreachableTracker.UnreachableAction.FALLBACK, unreachableTracker.onUnreachable())
    }

}
