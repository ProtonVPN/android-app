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

package com.protonvpn.app.ui.planupgrade

import android.app.Activity
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.protonvpn.android.R
import com.protonvpn.android.ui.planupgrade.CommonUpgradeDialogViewModel
import com.protonvpn.android.ui.planupgrade.CommonUpgradeDialogViewModel.State
import com.protonvpn.android.ui.planupgrade.UpgradeDialogViewModel
import com.protonvpn.android.ui.planupgrade.UpgradeFlowType
import com.protonvpn.android.ui.planupgrade.usecase.CycleInfo
import com.protonvpn.android.ui.planupgrade.usecase.GiapPlanInfo
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.formatPrice
import com.protonvpn.test.shared.createDynamicPlan
import com.protonvpn.test.shared.toProductId
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.proton.core.domain.entity.AppStore
import me.proton.core.domain.entity.UserId
import me.proton.core.plan.domain.entity.DynamicPlan
import me.proton.core.plan.domain.entity.DynamicPlanInstance
import me.proton.core.plan.domain.entity.DynamicPlanPrice
import me.proton.core.plan.domain.usecase.PerformGiapPurchase
import me.proton.core.plan.presentation.entity.PlanCycle
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.Optional
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class UpgradeDialogViewModelTests {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private val userIdFlow = MutableStateFlow<UserId?>(UserId("test_user_id"))

    private lateinit var performGiapPurchase: PerformGiapPurchase<Activity>
    private lateinit var testScope: TestScope
    private lateinit var viewModel: UpgradeDialogViewModel

    private var isInAppAllowed = true
    private var giapPlans: List<GiapPlanInfo> = emptyList()

    private val dummyPrices = mapOf(
        PlanCycle.MONTHLY to mapOf("USD" to DynamicPlanPrice("", currency = "USD", current = 10_00)),
        PlanCycle.YEARLY to mapOf("USD" to DynamicPlanPrice("", currency = "USD", current = 100_00))
    )

    private val testPlanName = "myplan"
    private val testPlan = GiapPlanInfo(
        createDynamicPlan(testPlanName, prices = dummyPrices),
        testPlanName,
        "My Plan",
        listOf(
            CycleInfo(PlanCycle.MONTHLY, "m"),
            CycleInfo(PlanCycle.YEARLY, "y"),
        ),
        PlanCycle.MONTHLY
    )

    @Before
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)

        isInAppAllowed = true
        giapPlans = listOf(testPlan)
        performGiapPurchase = mockk()
        viewModel = UpgradeDialogViewModel(
            userId = userIdFlow,
            authOrchestrator = mockk(relaxed = true),
            plansOrchestrator = mockk(relaxed = true),
            isInAppUpgradeAllowed = { isInAppAllowed },
            upgradeTelemetry = mockk(relaxed = true),
            loadGoogleSubscriptionPlans = { giapPlans },
            performGiapPurchase = performGiapPurchase,
            userPlanManager = mockk(relaxed = true),
            waitForSubscription = mockk(relaxed = true),
            convertToObservabilityGiapStatus = Optional.empty(),
            observabilityManager = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load default plan and purchase`() = testScope.runTest {
        val purchaseResult = MutableSharedFlow<PerformGiapPurchase.Result>()
        coEvery { performGiapPurchase.invoke(any(), any(), any(), any()) } coAnswers {
            purchaseResult.first()
        }

        viewModel.loadPlans(listOf(testPlanName), null, null, true)

        Assert.assertEquals(PlanCycle.MONTHLY, viewModel.selectedCycle.value)

        viewModel.state.test {
            val loadedState = awaitItem()
            assertIs<State.PurchaseReady>(loadedState)
            val loadedPlan = loadedState.selectedPlan

            Assert.assertEquals("myplan", loadedPlan.planName)
            Assert.assertFalse(loadedState.inProgress)

            viewModel.onPaymentStarted(UpgradeFlowType.REGULAR)
            viewModel.pay(mockk(), UpgradeFlowType.ONE_CLICK)
            Assert.assertTrue(assertIs<State.PurchaseReady>(awaitItem()).inProgress)

            // Fail before succeeding
            purchaseResult.emit(PerformGiapPurchase.Result.Error.PurchaseNotFound)
            Assert.assertFalse(assertIs<State.PurchaseReady>(awaitItem()).inProgress)

            // Try again and succeed
            viewModel.onPaymentStarted(UpgradeFlowType.ONE_CLICK)
            viewModel.pay(mockk(), UpgradeFlowType.ONE_CLICK)
            Assert.assertTrue(assertIs<State.PurchaseReady>(awaitItem()).inProgress)
            purchaseResult.emit(mockk<PerformGiapPurchase.Result.GiapSuccess>())
            Assert.assertEquals(
                State.PurchaseSuccess("myplan", UpgradeFlowType.ONE_CLICK),
                awaitItem()
            )
        }
    }

    @Test
    fun `in-app payments disabled`() = testScope.runTest {
        isInAppAllowed = false
        viewModel.loadPlans(listOf(testPlanName), null, null, true)
        Assert.assertTrue(viewModel.state.value is State.UpgradeDisabled)
    }

    @Test
    fun `show error on plan load fail`() = testScope.runTest {
        giapPlans = emptyList()
        viewModel.loadPlans(listOf(testPlanName), null, null, true)
        val state = viewModel.state.first()
        assertIs<State.LoadError>(state)
        assertEquals(state.messageRes, R.string.error_fetching_prices)
    }

    @Test
    fun `show error when first plan is missing`() = testScope.runTest {
        viewModel.loadPlans(listOf("missing plan", testPlanName), null, null, true)
        val state = viewModel.state.first()
        assertIs<State.LoadError>(state)
        assertEquals(state.messageRes, R.string.error_fetching_prices)
    }

    @Test
    fun `ignore subsequent plans if missing`() = testScope.runTest {
        viewModel.loadPlans(listOf(testPlanName, "missing plan"), null, null, true)
        val state = viewModel.state.first()
        assertIs<State.PurchaseReady>(state)
        assertEquals(listOf(testPlanName), state.allPlans.map { it.planName })
    }

    @Test
    fun `calculate price info with and without savings`() = testScope.runTest {
        val priceInfo = UpgradeDialogViewModel.calculatePriceInfos(
            listOf(
                CycleInfo(PlanCycle.MONTHLY, "m"),
                CycleInfo(PlanCycle.YEARLY, "y")
            ),
            createDynamicPlan(
                "name",
                mapOf(
                    Pair(
                        1, // months
                        DynamicPlanInstance(
                            cycle = 1,
                            description = "1 month",
                            periodEnd = Instant.MAX,
                            price = mapOf(
                                "USD" to DynamicPlanPrice(id = "id", currency = "USD", current = 10_00, default = 15_00)
                            )
                        )
                    ),
                    Pair(
                        12, // months
                        DynamicPlanInstance(
                            cycle = 12,
                            description = "12 month",
                            periodEnd = Instant.MAX,
                            price = mapOf("USD" to DynamicPlanPrice(id = "id", currency = "USD", current = 100_00, default = null))
                        )
                    )
                )
            ),
            withSavePercent = true
        )
        Assert.assertEquals(
            // Checks also the descending order by the cycle length in the map
            listOf(
                PlanCycle.YEARLY to CommonUpgradeDialogViewModel.PriceInfo(
                    formattedPrice = formatPrice(100.0, "USD"),
                    savePercent = -44,
                    formattedPerMonthPrice = formatPrice(8.33, "USD"),
                    formattedRenewPrice = null
                ),
                PlanCycle.MONTHLY to CommonUpgradeDialogViewModel.PriceInfo(
                    formattedPrice = formatPrice(10.0, "USD"),
                    savePercent = -33,
                    formattedRenewPrice = formatPrice(15.0, "USD")
                )
            ),
            priceInfo.toList()
        )
    }

    @Test
    fun `show error when prices are missing for any of the plans`() = testScope.runTest {
        val plan1 = createDynamicPlan(
            "plan with prices",
            prices = mapOf(
                PlanCycle.MONTHLY to mapOf("USD" to DynamicPlanPrice("1", "USD", 1_00)),
                PlanCycle.YEARLY to mapOf("USD" to DynamicPlanPrice("1", "USD", 1_00)),
            )
        )
        val plan2 = createDynamicPlan(
            "plan with missing prices",
            prices = mapOf(PlanCycle.MONTHLY to emptyMap())
        )
        giapPlans = listOf(plan1, plan2).toGiapPlans()
        viewModel.loadPlans(listOf("plan with prices", "plan with missing prices"), null, null, true)
        assertIs<State.LoadError>(viewModel.state.first())
    }

    @Test
    fun `plan order matches the order of plan names to loadPlans`() = testScope.runTest {
        giapPlans = createDummyPlans("plan1", "plan2")

        viewModel.loadPlans(listOf("plan2", "plan1"), null, null, true)
        assertPlanNames(listOf("plan2", "plan1"), viewModel.state.first())
    }

    @Test
    fun `when allowMultiplePlans is true then Plus and Unlimited plans are used`() = testScope.runTest {
        giapPlans = createDummyPlans(Constants.CURRENT_PLUS_PLAN, Constants.CURRENT_BUNDLE_PLAN)
        viewModel.loadPlans(allowMultiplePlans = true)

        assertPlanNames(listOf(Constants.CURRENT_PLUS_PLAN, Constants.CURRENT_BUNDLE_PLAN), viewModel.state.first())
    }

    @Test
    fun `when allowMultiplePlans is false then only the first plan is used`() = testScope.runTest {
        giapPlans = createDummyPlans(Constants.CURRENT_PLUS_PLAN, Constants.CURRENT_BUNDLE_PLAN)
        viewModel.loadPlans(allowMultiplePlans = false)

        assertPlanNames(listOf(Constants.CURRENT_PLUS_PLAN), viewModel.state.first())
    }

    private fun assertPlanNames(expected: List<String>, state: State) {
        assertIs<State.PurchaseReady>(state)
        assertEquals(expected, state.allPlans.map { it.planName })
    }

    private fun createDummyPlans(vararg planNames: String): List<GiapPlanInfo> =
        planNames
            .map { createDynamicPlan(it, dummyPrices) }
            .toGiapPlans()


    private fun Collection<DynamicPlan>.toGiapPlans(): List<GiapPlanInfo> {
        val cycles = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY)
        return this.map { plan ->
            val cycleInfos = cycles.map { CycleInfo(it, it.toProductId(AppStore.GooglePlay)) }
            GiapPlanInfo(plan, plan.name ?: "", plan.name ?: "", cycleInfos, cycles.first())
        }
    }
}
