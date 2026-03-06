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

import app.cash.turbine.test
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.restrictonsupsell.FakeIsStreamingRestrictionUpsellEnabled
import com.protonvpn.android.restrictonsupsell.RestrictionsUpsellStore
import com.protonvpn.android.restrictonsupsell.RestrictionsUpsellStoreProvider
import com.protonvpn.android.restrictonsupsell.StreamingUpsellRestrictionsFlow
import com.protonvpn.android.restrictonsupsell.StreamingUpsellRestrictionsNotificationTrigger
import com.protonvpn.android.vpn.VpnConnectionRestriction
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.restrictions
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingUpsellRestrictionsNotificationTriggerTests {

    private lateinit var isFfEnabled: FakeIsStreamingRestrictionUpsellEnabled
    private lateinit var testUserProvider: TestCurrentUserProvider
    private lateinit var testScope: TestScope
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var restrictionsUpsellStore: RestrictionsUpsellStore

    private lateinit var notificationTrigger: StreamingUpsellRestrictionsNotificationTrigger

    @Before
    fun setup() {
        vpnStateMonitor = VpnStateMonitor()
        testScope = TestScope()
        val restrictionsUpsellStoreProvider =
            RestrictionsUpsellStoreProvider(InMemoryDataStoreFactory())
        isFfEnabled = FakeIsStreamingRestrictionUpsellEnabled(true)
        testUserProvider = TestCurrentUserProvider(TestUser.freeUser.vpnUser)
        val currentUser = CurrentUser(testUserProvider)
        restrictionsUpsellStore =
            RestrictionsUpsellStore({ restrictionsUpsellStoreProvider }, currentUser)
        val restrictionsFlow =
            StreamingUpsellRestrictionsFlow(isFfEnabled, vpnStateMonitor, currentUser)
        notificationTrigger =
            StreamingUpsellRestrictionsNotificationTrigger(
                mainScope = testScope.backgroundScope,
                eventStreamingRestricted = restrictionsFlow,
                restrictionsUpsellStore = restrictionsUpsellStore,
                now = testScope::currentTime,
                isTv = mockk(relaxed = true),
                showNotification = mockk(relaxed = true),
            )

        // Start the test some time after the epoch.
        testScope.advanceTimeBy(10.days)
        notificationTrigger.start()
        testScope.runCurrent()
    }

    @Test
    fun `WHEN streaming restriction is reported THEN streaming notification event is emitted`() =
        testScope.runTest {
            notificationTrigger.eventNotification.test {
                vpnStateMonitor.eventRestrictions.tryEmit(
                    restrictions(VpnConnectionRestriction.Streaming)
                )
                awaitItem()
            }
        }

    @Test
    fun `GIVEN FF disabled WHEN streaming restriction is reported THEN notification is not triggered`() =
        testScope.runTest {
            isFfEnabled.setEnabled(false)
            notificationTrigger.eventNotification.test {
                vpnStateMonitor.eventRestrictions.tryEmit(
                    restrictions(VpnConnectionRestriction.Streaming)
                )
                expectNoEvents()
            }
        }

    @Test
    fun `WHEN streaming restriction is reported multiple times THEN streaming notification event is emitted at most once per day`() =
        testScope.runTest {
            notificationTrigger.eventNotification.test {
                vpnStateMonitor.eventRestrictions.tryEmit(
                    restrictions(VpnConnectionRestriction.Streaming)
                )
                awaitItem()
                advanceTimeBy(1.hours)
                vpnStateMonitor.eventRestrictions.tryEmit(
                    restrictions(VpnConnectionRestriction.Streaming)
                )
                expectNoEvents()
                advanceTimeBy(1.days)
                vpnStateMonitor.eventRestrictions.tryEmit(
                    restrictions(VpnConnectionRestriction.Streaming)
                )
                awaitItem()
            }
        }

    @Test
    fun `WHEN p2p restriction is reported THEN streaming notification event is not triggered`() =
        testScope.runTest {
            notificationTrigger.eventNotification.test {
                vpnStateMonitor.eventRestrictions.tryEmit(
                    restrictions(VpnConnectionRestriction.P2P)
                )
                expectNoEvents()
            }
        }

    @Test
    fun `WHEN p2p and streaming restriction is reported THEN streaming notification event is triggered`() =
        testScope.runTest {
            notificationTrigger.eventNotification.test {
                vpnStateMonitor.eventRestrictions.tryEmit(
                    restrictions(VpnConnectionRestriction.P2P, VpnConnectionRestriction.Streaming)
                )
                awaitItem()
            }
        }

    @Test
    fun `GIVEN plus user WHEN streaming restriction is reported THEN notification is not triggered`() =
        // This case should not happen in practice.
        testScope.runTest {
            testUserProvider.vpnUser = TestUser.plusUser.vpnUser
            notificationTrigger.eventNotification.test {
                vpnStateMonitor.eventRestrictions.tryEmit(
                    restrictions(VpnConnectionRestriction.P2P, VpnConnectionRestriction.Streaming)
                )
                expectNoEvents()
            }
        }
}
