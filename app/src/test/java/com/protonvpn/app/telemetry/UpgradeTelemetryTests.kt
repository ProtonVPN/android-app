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

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.promooffers.usecase.FakeIsIapClientSidePromo12mExperimentEnabled
import com.protonvpn.android.promooffers.usecase.GetEligibleIntroductoryOffers
import com.protonvpn.android.promooffers.usecase.HasAnyIntroOffer
import com.protonvpn.android.telemetry.DefaultCommonDimensions
import com.protonvpn.android.telemetry.DefaultTelemetryReporter
import com.protonvpn.android.telemetry.Telemetry
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.telemetry.UpgradeTrigger
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.ui.planupgrade.UpgradeFlowType
import com.protonvpn.android.ui.planupgrade.comparison_table.FakeIsUpsellComparisonTableEnabled
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createAccountUser
import io.mockk.MockKAnnotations
import io.mockk.MockKAssertScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.proton.core.auth.test.fake.FakeIsCredentialLessEnabled
import me.proton.core.plan.presentation.entity.PlanCycle
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
    private lateinit var mockGetEligibleIntroductoryOffers: GetEligibleIntroductoryOffers
    @MockK
    private lateinit var mockTelemetry: Telemetry

    private var fakeTime: Long = 0L // It's not related to test scheduler's clock.
    private lateinit var experiment12mPromoFF: FakeIsIapClientSidePromo12mExperimentEnabled
    private lateinit var testScope: TestScope
    private lateinit var testUserProvider: TestCurrentUserProvider
    private val freeVpnUser = TestUser.freeUser.vpnUser

    private lateinit var upgradeTelemetry: UpgradeTelemetry

    private val dummyOffer = GetEligibleIntroductoryOffers.Offer("", PlanCycle.MONTHLY, "EUR", 99)

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        testScope = TestScope(UnconfinedTestDispatcher())
        fakeTime = 0

        every { mockTelemetry.event(UPSELL_GROUP, any(), any(), any()) } just runs

        experiment12mPromoFF = FakeIsIapClientSidePromo12mExperimentEnabled(false)
        testUserProvider = TestCurrentUserProvider(freeVpnUser, createAccountUser(createdAtUtc = 100L))
        val currentUser = CurrentUser(testUserProvider)

        val commonDimensions = DefaultCommonDimensions(
            currentUser,
            VpnStateMonitor(),
            ServerListUpdaterPrefs(MockSharedPreferencesProvider()),
            FakeIsCredentialLessEnabled(true)
        )
        val helper = TelemetryFlowHelper(testScope.backgroundScope, DefaultTelemetryReporter(mockTelemetry))
        upgradeTelemetry = UpgradeTelemetry(
            mainScope = testScope.backgroundScope,
            commonDimensions = commonDimensions,
            currentUser = currentUser,
            clock = { fakeTime },
            telemetryHelperLazy = { helper },
            hasAnyIntroOffer = HasAnyIntroOffer({ mockGetEligibleIntroductoryOffers }),
            isUpsellComparisonTableEnabled = FakeIsUpsellComparisonTableEnabled(false),
            isIapClientSidePromo12MExperimentEnabled = experiment12mPromoFF,
        )
    }

    @Test
    fun `upsell_display event dimensions`() = testScope.runTest {
        upgradeTelemetry.onUpgradeFlowStarted(
            upgradeSource = UpgradeSource.COUNTRIES,
            upgradeTrigger = UpgradeTrigger.COUNTRY_SELECTION,
            reference = "ref",
        )

        val expectedDimensions = mapOf(
            "modal_source" to "countries",
            "modal_trigger" to "country_selection",
            "new_free_plan_ui" to "yes",
            "user_country" to "n/a",
            "vpn_status" to "off",
            "user_plan" to "free",
            "days_since_account_creation" to "0",
            "reference" to "ref",
            "is_credential_less_enabled" to "yes",
            "user_tier" to "free",
            "vpn_upsell_modal_comparison_table_20260427" to "control",
        )
        verify {
            mockTelemetry.event(UPSELL_GROUP, "upsell_display", emptyMap(), expectedDimensions)
        }
    }

    @Test
    fun `flow_type dimension`() = testScope.runTest {
        upgradeTelemetry.onUpgradeFlowStarted(
            upgradeSource = UpgradeSource.COUNTRIES,
            upgradeTrigger = UpgradeTrigger.HOME_CAROUSEL,
            reference = "ref",
        )
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
    fun `modal_source and has_eligible_price are carried over to subsequent events`() = testScope.runTest {
        upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.NETSHIELD, UpgradeTrigger.SETTINGS, null)
        upgradeTelemetry.onPricesLoaded(hasIntroPrices = true)
        upgradeTelemetry.onUpgradeAttempt(UpgradeFlowType.REGULAR)
        upgradeTelemetry.onUpgradeSuccess("new_plan", UpgradeFlowType.REGULAR, PlanCycle.FREE.value)

        verify {
            listOf(
                "upsell_display",
                "upsell_price_display",
                "upsell_upgrade_attempt",
                "upsell_success"
            ).forEach { event ->
                mockTelemetry.event(
                    UPSELL_GROUP,
                    event,
                    emptyMap(),
                    withArg { assertEquals("netshield", it["modal_source"]) }
                )
            }
            listOf("upsell_price_display", "upsell_upgrade_attempt", "upsell_success").forEach { event ->
                mockTelemetry.event(
                    UPSELL_GROUP,
                    event,
                    emptyMap(),
                    withArg { assertEquals("true", it["has_intro_price"]) }
                )
            }
        }
    }

    @Test
    fun `when new flow starts it overrides the previous modal_source`() = testScope.runTest {
        upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.NETSHIELD, UpgradeTrigger.SETTINGS, null)
        upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.ADVANCED_CUSTOMIZATION, UpgradeTrigger.SETTINGS, null)
        upgradeTelemetry.onUpgradeAttempt(UpgradeFlowType.REGULAR)
        upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.PROFILES, UpgradeTrigger.PROFILES, null)
        upgradeTelemetry.onUpgradeAttempt(UpgradeFlowType.REGULAR)
        upgradeTelemetry.onUpgradeSuccess("new_plan", UpgradeFlowType.REGULAR, PlanCycle.FREE.value)

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
        upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.ADVANCED_CUSTOMIZATION, UpgradeTrigger.SETTINGS, null)
        upgradeTelemetry.onUpgradeAttempt(UpgradeFlowType.REGULAR)
        upgradeTelemetry.onUpgradeSuccess("new_plan", UpgradeFlowType.REGULAR, PlanCycle.YEARLY.value)

        verify {
            mockTelemetry.event(
                UPSELL_GROUP,
                "upsell_success",
                emptyMap(),
                withArg {
                    assertEquals("free", it["user_plan"])
                    assertEquals("new_plan", it["upgraded_user_plan"])
                    assertEquals("12", it["billing_cycle"])
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
            upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.ADVANCED_CUSTOMIZATION, UpgradeTrigger.SETTINGS, null)
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
        upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.ADVANCED_CUSTOMIZATION, UpgradeTrigger.SETTINGS, null)
        upgradeTelemetry.onUpgradeAttempt(UpgradeFlowType.REGULAR)
        fakeTime = 10.minutes.inWholeMilliseconds + 1
        upgradeTelemetry.onUpgradeSuccess("new_plan", UpgradeFlowType.REGULAR, PlanCycle.MONTHLY.value)

        verify(exactly = 0) {
            mockTelemetry.event(UPSELL_GROUP, "upsell_success", any(), any())
        }
    }

    @Test
    fun `GIVEN 12m experiment WHEN user is eligible for intro price THEN experiment events are reported`() =
        testScope.runTest {
            coEvery { mockGetEligibleIntroductoryOffers.invoke(any()) } returns listOf(dummyOffer)
            every { mockTelemetry.event(EXPERIMENT_GROUP, any(), any(), any()) } just runs
            upgradeTelemetry.start()
            experiment12mPromoFF.setVariantName("12m")
            experiment12mPromoFF.setEnabled(true)

            with(upgradeTelemetry) {
                onUpgradeFlowStarted(UpgradeSource.PROFILES, UpgradeTrigger.PROFILES)
                onPricesLoaded(hasIntroPrices = true)
                onUpgradeAttempt(UpgradeFlowType.ONE_CLICK)
                onUpgradeSuccess(null, UpgradeFlowType.ONE_CLICK, 1)
            }

            verify {
                val dimensionAssert: MockKAssertScope.(Map<String, String>) -> Unit =
                    { assertEquals("12m", it["experiment_variant"]) }
                mockTelemetry.event(EXPERIMENT_GROUP, "experiment_enrolled", any(), withArg(dimensionAssert))
                mockTelemetry.event(EXPERIMENT_GROUP, "upsell_display", any(), withArg(dimensionAssert))
                mockTelemetry.event(EXPERIMENT_GROUP, "upsell_price_display", any(), withArg(dimensionAssert))
                mockTelemetry.event(EXPERIMENT_GROUP, "upsell_upgrade_attempt", any(), withArg(dimensionAssert))
                mockTelemetry.event(EXPERIMENT_GROUP, "upsell_success", any(), withArg(dimensionAssert))
            }
        }

    @Test
    fun `GIVEN 12m experiment WHEN user is not eligible for intro price THEN no experiment events are reported`() =
        testScope.runTest {
            coEvery { mockGetEligibleIntroductoryOffers.invoke(any()) } returns emptyList()
            upgradeTelemetry.start()
            experiment12mPromoFF.setVariantName("12m")
            experiment12mPromoFF.setEnabled(true)

            with(upgradeTelemetry) {
                onUpgradeFlowStarted(UpgradeSource.PROFILES, UpgradeTrigger.PROFILES)
                onPricesLoaded(hasIntroPrices = true)
                onUpgradeAttempt(UpgradeFlowType.ONE_CLICK)
                onUpgradeSuccess(null, UpgradeFlowType.ONE_CLICK, 1)
            }

            verify(exactly = 0) {
                mockTelemetry.event(EXPERIMENT_GROUP, any(), any(), any())
            }
        }

    @Test
    fun `GIVEN 12m experiment WHEN user is eligible for intro price in control group THEN experiment events report the control group`() =
        testScope.runTest {
            coEvery { mockGetEligibleIntroductoryOffers.invoke(any()) } returns listOf(dummyOffer)
            every { mockTelemetry.event(EXPERIMENT_GROUP, any(), any(), any()) } just runs

            upgradeTelemetry.start()
            experiment12mPromoFF.setVariantName("control")
            experiment12mPromoFF.setEnabled(true)
            with(upgradeTelemetry) {
                onUpgradeFlowStarted(UpgradeSource.PROFILES, UpgradeTrigger.PROFILES)
            }

            verify {
                val dimensionAssert: MockKAssertScope.(Map<String, String>) -> Unit =
                    { assertEquals("control", it["experiment_variant"]) }
                mockTelemetry.event(EXPERIMENT_GROUP, "experiment_enrolled", any(), withArg(dimensionAssert))
                mockTelemetry.event(EXPERIMENT_GROUP, "upsell_display", any(), withArg(dimensionAssert) )
            }
        }

    companion object {
        private const val UPSELL_GROUP = "vpn.any.upsell"
        private const val EXPERIMENT_GROUP = "vpn.any.experiment_12m_promo_202605"
    }
}
