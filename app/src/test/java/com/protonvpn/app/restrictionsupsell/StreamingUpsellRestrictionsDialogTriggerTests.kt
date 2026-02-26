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

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.restrictonsupsell.FakeIsStreamingRestrictionUpsellEnabled
import com.protonvpn.android.restrictonsupsell.RestrictionsUpsellStore
import com.protonvpn.android.restrictonsupsell.RestrictionsUpsellStoreProvider
import com.protonvpn.android.restrictonsupsell.StreamingUpsellRestrictionsDialogTrigger
import com.protonvpn.android.restrictonsupsell.StreamingUpsellRestrictionsFlow
import com.protonvpn.android.restrictonsupsell.StreamingUpsellRestrictionsNotificationTrigger
import com.protonvpn.android.vpn.VpnConnectionRestriction
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.restrictions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingUpsellRestrictionsDialogTriggerTests {


    private lateinit var testScope: TestScope
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var restrictionsUpsellStore: RestrictionsUpsellStore

    private lateinit var dialogTrigger: StreamingUpsellRestrictionsDialogTrigger

    @Before
    fun setup() {
        vpnStateMonitor = VpnStateMonitor()

        testScope = TestScope()
        val restrictionsUpsellStoreProvider =
            RestrictionsUpsellStoreProvider(InMemoryDataStoreFactory())
        val isFfEnabled = FakeIsStreamingRestrictionUpsellEnabled(true)
        val testUserProvider = TestCurrentUserProvider(TestUser.freeUser.vpnUser)
        val currentUser = CurrentUser(testUserProvider)
        restrictionsUpsellStore =
            RestrictionsUpsellStore({ restrictionsUpsellStoreProvider }, currentUser)
        val restrictionsFlow =
            StreamingUpsellRestrictionsFlow(isFfEnabled, vpnStateMonitor, currentUser)
        dialogTrigger =
            StreamingUpsellRestrictionsDialogTrigger(
                mainScope = testScope.backgroundScope,
                isStreamingRestrictionUpsellEnabled = isFfEnabled,
                eventStreamingRestricted = restrictionsFlow,
                currentUser = currentUser,
                restrictionsUpsellStore = restrictionsUpsellStore,
                now = testScope::currentTime,
            )

        // Start the test some time after the epoch.
        testScope.advanceTimeBy(10.days)
        dialogTrigger.start()
        testScope.runCurrent() // Let it initialize the flows.
    }

    @Test
    fun `WHEN streaming restriction is emitted THEN store gets lastEventTimestamp and lastEventConnectionId`() =
        testScope.runTest {
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
            assertFalse(dialogTrigger.shouldShowUpsellDialogOnOpen())
        }

    @Test
    fun `GIVEN event within last hour and dialog not shown for this connection WHEN shouldShowUpsellDialogOnOpen THEN returns true`() =
        testScope.runTest {
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming)
            )
            runCurrent()
            advanceTimeBy(45.minutes)
            assertTrue(dialogTrigger.shouldShowUpsellDialogOnOpen())
        }

    @Test
    fun `GIVEN event within last hour but dialog already shown for this connection WHEN shouldShowUpsellDialogOnOpen THEN returns false`() =
        testScope.runTest {
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming)
            )
            runCurrent()
            dialogTrigger.onShowUpsellDialogShown()
            runCurrent()
            assertFalse(dialogTrigger.shouldShowUpsellDialogOnOpen())
        }

    @Test
    fun `GIVEN event more than 1 hour ago WHEN shouldShowUpsellDialogOnOpen THEN returns false`() =
        testScope.runTest {
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming)
            )
            runCurrent()
            advanceTimeBy(2.hours)
            assertFalse(dialogTrigger.shouldShowUpsellDialogOnOpen())
        }

    @Test
    fun `GIVEN events more 1 hour ago WHEN event happens less than 1 hour ago THEN shouldShowUpsellDialogOnOpen returns true`() =
        testScope.runTest {
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming)
            )
            runCurrent()
            advanceTimeBy(2.hours)
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming)
            )
            runCurrent()
            advanceTimeBy(30.minutes)
            assertTrue(dialogTrigger.shouldShowUpsellDialogOnOpen())
        }

    @Test
    fun `GIVEN two connections event for A then B and dialog shown for A only WHEN shouldShowUpsellDialogOnOpen THEN returns true for B`() =
        testScope.runTest {
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
            runCurrent()
            assertTrue(dialogTrigger.shouldShowUpsellDialogOnOpen())
        }

    @Test
    fun `GIVEN two connections event for A and dialog shown for A and no new event for B WHEN shouldShowUpsellDialogOnOpen THEN returns false`() =
        testScope.runTest {
            val connectionIdA = UUID.randomUUID()
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming, id = connectionIdA)
            )
            runCurrent()
            dialogTrigger.onShowUpsellDialogShown()
            runCurrent()
            assertFalse(dialogTrigger.shouldShowUpsellDialogOnOpen())
        }

    @Test
    fun `WHEN onShowUpsellDialogShown THEN lastDialogConnectionId equals lastEventConnectionId and shouldShowUpsellDialogOnOpen returns false until new connection event`() =
        testScope.runTest {
            val connectionId = UUID.randomUUID()
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming, id = connectionId)
            )
            runCurrent()
            assertTrue(dialogTrigger.shouldShowUpsellDialogOnOpen())
            dialogTrigger.onShowUpsellDialogShown()
            runCurrent()
            val state = restrictionsUpsellStore.state.first().streaming
            assertEquals(connectionId.toString(), state.lastDialogConnectionId)
            assertFalse(dialogTrigger.shouldShowUpsellDialogOnOpen())
            val connectionId2 = UUID.randomUUID()
            vpnStateMonitor.eventRestrictions.tryEmit(
                restrictions(VpnConnectionRestriction.Streaming, id = connectionId2)
            )
            runCurrent()
            assertTrue(dialogTrigger.shouldShowUpsellDialogOnOpen())
        }
}
