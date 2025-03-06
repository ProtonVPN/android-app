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

package com.protonvpn.app.telemetry

import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.appconfig.GetFeatureFlags
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.telemetry.DefaultCommonDimensions
import com.protonvpn.android.telemetry.DefaultTelemetryReporter
import com.protonvpn.android.telemetry.Telemetry
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.ui.planupgrade.UpgradeFlowType
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createAccountUser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.proton.core.auth.test.fake.FakeIsCredentialLessEnabled
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class UpgradeTelemetryTests {

    @MockK
    private lateinit var mockTelemetry: Telemetry

    private var fakeTime: Long = 0L // It's not related to test scheduler's clock.
    private lateinit var featureFlagsFlow: MutableStateFlow<FeatureFlags>
    private lateinit var testScope: TestScope
    private lateinit var testUserProvider: TestCurrentUserProvider
    private val freeVpnUser = TestUser.freeUser.vpnUser

    private lateinit var upgradeTelemetry: UpgradeTelemetry

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        testScope = TestScope(UnconfinedTestDispatcher())
        fakeTime = 0

        every { mockTelemetry.event(UPSELL_GROUP, any(), any(), any()) } just runs

        testUserProvider = TestCurrentUserProvider(freeVpnUser, createAccountUser(createdAtUtc = 100L))
        val currentUser = CurrentUser(testUserProvider)

        featureFlagsFlow = MutableStateFlow(FeatureFlags())
        val getFeatureFlags = GetFeatureFlags(featureFlagsFlow)
        val commonDimensions = DefaultCommonDimensions(
            currentUser,
            VpnStateMonitor(),
            ServerListUpdaterPrefs(MockSharedPreferencesProvider()),
            FakeIsCredentialLessEnabled(true)
        )
        val helper = TelemetryFlowHelper(testScope.backgroundScope, DefaultTelemetryReporter(mockTelemetry))
        upgradeTelemetry = UpgradeTelemetry(
            commonDimensions,
            currentUser,
            getFeatureFlags,
            clock = { fakeTime },
            helper
        )
    }

    @Test
    fun `upsell_display event dimensions`() = testScope.runTest {
        upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.COUNTRIES, "ref")

        val expectedDimensions = mapOf(
            "modal_source" to "countries",
            "new_free_plan_ui" to "yes",
            "user_country" to "n/a",
            "vpn_status" to "off",
            "user_plan" to "free",
            "days_since_account_creation" to "0",
            "reference" to "ref",
            "is_credential_less_enabled" to "yes",
            "user_tier" to "free"
        )
        verify {
            mockTelemetry.event(UPSELL_GROUP, "upsell_display", emptyMap(), expectedDimensions)
        }
    }

    @Test
    fun `flow_type dimension`() = testScope.runTest {
        upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.COUNTRIES, "ref")
        upgradeTelemetry.onUpgradeAttempt(UpgradeFlowType.ONE_CLICK)

        verify {
            mockTelemetry.event(UPSELL_GROUP, "upsell_display", emptyMap(), any())
            mockTelemetry.event(
                UPSELL_GROUP,
                "upsell_upgrade_attempt",
                emptyMap(),
                withArg { assertEquals( "one_click", it["flow_type"]) }
            )
        }
    }

    @Test
    fun `modal_source is carried over to subsequent events`() = testScope.runTest {
        upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.NETSHIELD)
        upgradeTelemetry.onUpgradeAttempt(UpgradeFlowType.REGULAR)
        upgradeTelemetry.onUpgradeSuccess("new_plan", UpgradeFlowType.REGULAR)

        verify {
            listOf("upsell_display", "upsell_upgrade_attempt", "upsell_success").forEach { event ->
                mockTelemetry.event(
                    UPSELL_GROUP,
                    event,
                    emptyMap(),
                    withArg { assertEquals("netshield", it["modal_source"]) }
                )
            }
        }
    }

    @Test
    fun `when new flow starts it overrides the previous modal_source`() = testScope.runTest {
        upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.NETSHIELD)
        upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.ADVANCED_CUSTOMIZATION)
        upgradeTelemetry.onUpgradeAttempt(UpgradeFlowType.REGULAR)
        upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.PROFILES)
        upgradeTelemetry.onUpgradeAttempt(UpgradeFlowType.REGULAR)
        upgradeTelemetry.onUpgradeSuccess("new_plan", UpgradeFlowType.REGULAR)

        verify {
            listOf("upsell_display", "upsell_upgrade_attempt", "upsell_success").forEach { event ->
                mockTelemetry.event(
                    UPSELL_GROUP,
                    event,
                    emptyMap(),
                    withArg { assertEquals("profiles", it["modal_source"]) }
                )
            }
        }
    }

    @Test
    fun `on success both old and new plan is reported`() = testScope.runTest {
        upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.ADVANCED_CUSTOMIZATION)
        upgradeTelemetry.onUpgradeAttempt(UpgradeFlowType.REGULAR)
        upgradeTelemetry.onUpgradeSuccess("new_plan", UpgradeFlowType.REGULAR)

        verify {
            mockTelemetry.event(
                UPSELL_GROUP,
                "upsell_success",
                emptyMap(),
                withArg {
                    assertEquals("free", it["user_plan"])
                    assertEquals("new_plan", it["upgraded_user_plan"])
                }
            )
        }
    }

    @Test
    fun `test days_since_account_creation buckets`() = testScope.runTest {
        fun testCase(timeSinceCreation: Duration, expectedBucket: String, createdAtUtc: Long = 1) {
            testUserProvider.user = createAccountUser(createdAtUtc = createdAtUtc)
            fakeTime = createdAtUtc + timeSinceCreation.inWholeMilliseconds
            val dimensionsSlot = slot<Map<String, String>>() // Using slots allows for cleaner failure messages.
            every { mockTelemetry.event(any(), any(), any(), capture(dimensionsSlot)) } just runs
            upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.ADVANCED_CUSTOMIZATION)
            assertEquals(expectedBucket, dimensionsSlot.captured["days_since_account_creation"])
        }

        testCase(createdAtUtc = 0, timeSinceCreation = 100.days, expectedBucket = "n/a") // Unknown creation date, rare.
        testCase(5.hours, "0")
        testCase(1.days, "1-3")
        testCase(3.days, "1-3")
        testCase(5.days, "4-7")
        testCase(8.days - 1.seconds, "4-7")
        testCase(8.days, "8-14")
        testCase(14.days + 1.seconds, "8-14")
        testCase(15.days - 1.seconds, "8-14")
        testCase(15.days, ">14")
        testCase(60.days, ">14")
    }

    @Test
    fun `upgrade more than 10 minutes after first event is ignored`() = testScope.runTest {
        upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.ADVANCED_CUSTOMIZATION)
        upgradeTelemetry.onUpgradeAttempt(UpgradeFlowType.REGULAR)
        fakeTime = 10.minutes.inWholeMilliseconds + 1
        upgradeTelemetry.onUpgradeSuccess("new_plan", UpgradeFlowType.REGULAR)

        verify(exactly = 0) {
            mockTelemetry.event(UPSELL_GROUP, "upsell_success", any(), any())
        }
    }

    companion object {
        private const val UPSELL_GROUP = "vpn.any.upsell"
    }
}
