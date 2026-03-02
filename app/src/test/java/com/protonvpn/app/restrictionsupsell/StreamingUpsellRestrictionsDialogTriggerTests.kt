/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.app.restrictionsupsell

import android.app.Activity
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.restrictonsupsell.FakeIsStreamingRestrictionUpsellEnabled
import com.protonvpn.android.restrictonsupsell.OpenUpgradeStreamingBlockDialog
import com.protonvpn.android.restrictonsupsell.RestrictionsUpsellStore
import com.protonvpn.android.restrictonsupsell.RestrictionsUpsellStoreProvider
import com.protonvpn.android.restrictonsupsell.StreamingUpsellRestrictionsDialogTrigger
import com.protonvpn.android.restrictonsupsell.StreamingUpsellRestrictionsFlow
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.vpn.VpnConnectionRestriction
import com.protonvpn.android.vpn.VpnConnectionRestrictions
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.restrictions
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.util.EnumSet
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingUpsellRestrictionsDialogTriggerTests {

    @MockK private lateinit var mockDialogOpener: OpenUpgradeStreamingBlockDialog
    @MockK private lateinit var mockIsTvCheck: IsTvCheck

    private lateinit var testForegroundActivityFlow: MutableStateFlow<Activity?>
    private lateinit var testScope: TestScope
    private lateinit var testUserProvider: TestCurrentUserProvider
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var restrictionsUpsellStore: RestrictionsUpsellStore

    private lateinit var dialogTrigger: StreamingUpsellRestrictionsDialogTrigger

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        vpnStateMonitor = VpnStateMonitor()
        testScope = TestScope()
        every { mockDialogOpener.invoke(any()) } just Runs
        every { mockIsTvCheck.invoke() } returns false
        val restrictionsUpsellStoreProvider =
            RestrictionsUpsellStoreProvider(InMemoryDataStoreFactory())
        testUserProvider = TestCurrentUserProvider(TestUser.freeUser.vpnUser)
        restrictionsUpsellStore =
            RestrictionsUpsellStore(
                { restrictionsUpsellStoreProvider },
                CurrentUser(testUserProvider),
            )
        testForegroundActivityFlow = MutableStateFlow(null)
    }

    private fun TestScope.createAndStartDialogTrigger() {
        val isFfEnabled = FakeIsStreamingRestrictionUpsellEnabled(true)
        val currentUser = CurrentUser(testUserProvider)
        val foregroundActivityTracker =
            ForegroundActivityTracker(testScope.backgroundScope, testForegroundActivityFlow)
        val restrictionsFlow =
            StreamingUpsellRestrictionsFlow(isFfEnabled, vpnStateMonitor, currentUser)

        dialogTrigger =
            StreamingUpsellRestrictionsDialogTrigger(
                mainScope = testScope.backgroundScope,
                isStreamingRestrictionUpsellEnabled = isFfEnabled,
                isTv = mockIsTvCheck,
                eventStreamingRestricted = restrictionsFlow,
                foregroundActivityTracker = foregroundActivityTracker,
                currentUser = currentUser,
                restrictionsUpsellStore = restrictionsUpsellStore,
                openUpgradeDialog = mockDialogOpener,
                now = testScope::currentTime,
            )

        // Start the test some time after the epoch.
        advanceTimeBy(10.days)
        dialogTrigger.start()
        runCurrent() // Let it initialize the flows.
    }

    @Test
    fun `WHEN streaming restriction is emitted THEN store gets lastEventTimestamp and lastEventConnectionId`() =
        testScope.runTest {
            createAndStartDialogTrigger()
            val connectionId = UUID.randomUUID()
            val eventTime = currentTime
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming, id = connectionId)
            )
            runCurrent()
            val state = restrictionsUpsellStore.state.first().streaming
            assertEquals(eventTime, state.lastEventTimestamp)
            assertEquals(connectionId.toString(), state.lastEventConnectionId)
        }

    @Test
    fun `GIVEN no event WHEN shouldShowUpsellDialogOnOpen THEN returns false`() =
        testScope.runTest {
            createAndStartDialogTrigger()
            runCurrentAndGoForeground()
            verify(exactly = 0) { mockDialogOpener.invoke(any()) }
        }

    @Test
    fun `GIVEN event within last hour and dialog not shown for this connection WHEN shouldShowUpsellDialogOnOpen THEN returns true`() =
        testScope.runTest {
            createAndStartDialogTrigger()
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming)
            )
            runCurrentAndGoForeground(advanceTimeBy = 45.minutes)
            verify { mockDialogOpener.invoke(any()) }
        }

    @Test
    fun `GIVEN event within last hour but dialog already shown for this connection WHEN shouldShowUpsellDialogOnOpen THEN returns false`() =
        testScope.runTest {
            createAndStartDialogTrigger()
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming)
            )
            runCurrent()
            dialogTrigger.onShowUpsellDialogShown()
            runCurrentAndGoForeground()
            verify(exactly = 0) { mockDialogOpener.invoke(any()) }
        }

    @Test
    fun `GIVEN event more than 1 hour ago WHEN shouldShowUpsellDialogOnOpen THEN returns false`() =
        testScope.runTest {
            createAndStartDialogTrigger()
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming)
            )
            runCurrentAndGoForeground(2.hours)
            verify(exactly = 0) { mockDialogOpener.invoke(any()) }
        }

    @Test
    fun `GIVEN events more 1 hour ago WHEN event happens less than 1 hour ago THEN shouldShowUpsellDialogOnOpen returns true`() =
        testScope.runTest {
            createAndStartDialogTrigger()
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming)
            )
            runCurrent()
            advanceTimeBy(2.hours)
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming)
            )
            runCurrentAndGoForeground(30.minutes)
            verify { mockDialogOpener.invoke(any()) }
        }

    @Test
    fun `GIVEN two connections event for A then B and dialog shown for A only WHEN shouldShowUpsellDialogOnOpen THEN returns true for B`() =
        testScope.runTest {
            createAndStartDialogTrigger()
            val connectionIdA = UUID.randomUUID()
            val connectionIdB = UUID.randomUUID()
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming, id = connectionIdA)
            )
            runCurrent()
            dialogTrigger.onShowUpsellDialogShown()
            runCurrent()
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming, id = connectionIdB)
            )
            runCurrentAndGoForeground()
            verify { mockDialogOpener.invoke(any()) }
        }

    @Test
    fun `GIVEN two connections event for A and dialog shown for A and no new event for B WHEN shouldShowUpsellDialogOnOpen THEN returns false`() =
        testScope.runTest {
            createAndStartDialogTrigger()
            val connectionIdA = UUID.randomUUID()
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming, id = connectionIdA)
            )
            runCurrent()
            dialogTrigger.onShowUpsellDialogShown()
            runCurrentAndGoForeground()
            verify(exactly = 0) { mockDialogOpener.invoke(any()) }
        }

    @Test
    fun `WHEN onShowUpsellDialogShown THEN lastDialogConnectionId equals lastEventConnectionId and shouldShowUpsellDialogOnOpen returns false until new connection event`() =
        testScope.runTest {
            createAndStartDialogTrigger()
            val connectionId = UUID.randomUUID()
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming, id = connectionId)
            )
            runCurrentAndGoForeground()
            verify(exactly = 1) { mockDialogOpener.invoke(any()) }
            runCurrentAndGoForeground()
            val state = restrictionsUpsellStore.state.first().streaming
            assertEquals(connectionId.toString(), state.lastDialogConnectionId)
            verify(exactly = 1) { mockDialogOpener.invoke(any()) }
            val connectionId2 = UUID.randomUUID()
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming, id = connectionId2)
            )
            runCurrentAndGoForeground()
            verify(exactly = 2) { mockDialogOpener.invoke(any()) }
        }

    @Test
    fun `WHEN app goes to foreground multiple times THEN dialog is triggered only once`() =
        testScope.runTest {
            createAndStartDialogTrigger()
            val streamingRestriction =
                VpnConnectionRestrictions(
                    EnumSet.of(VpnConnectionRestriction.Streaming),
                    UUID.randomUUID(),
                )

            vpnStateMonitor.eventRestrictions.tryEmit(streamingRestriction)
            runCurrentAndGoForeground()
            verify(exactly = 1) { mockDialogOpener.invoke(any()) }

            runCurrentAndGoForeground()
            verify(exactly = 1) { mockDialogOpener.invoke(any()) }
        }

    @Test
    fun `GIVEN TV WHEN conditions for dialog are met THEN no dialog is triggered`() =
        testScope.runTest {
            every { mockIsTvCheck.invoke() } returns true
            createAndStartDialogTrigger() // The TV check is performed right on start.
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming)
            )
            runCurrentAndGoForeground()
            verify(exactly = 0) { mockDialogOpener.invoke(any()) }
        }

    private fun TestScope.runCurrentAndGoForeground(advanceTimeBy: Duration = 0.minutes) {
        testForegroundActivityFlow.tryEmit(null)
        advanceTimeBy(advanceTimeBy)
        runCurrent()
        testForegroundActivityFlow.tryEmit(mockk())
        runCurrent()
    }
}
