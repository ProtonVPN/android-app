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
import com.protonvpn.android.ui.planupgrade.CommonUpgradeDialogViewModel
import com.protonvpn.android.ui.planupgrade.CommonUpgradeDialogViewModel.State
import com.protonvpn.android.ui.planupgrade.UpgradeDialogViewModel
import com.protonvpn.android.ui.planupgrade.UpgradeFlowType
import com.protonvpn.android.ui.planupgrade.usecase.CycleInfo
import com.protonvpn.android.ui.planupgrade.usecase.GiapPlanInfo
import com.protonvpn.android.utils.formatPrice
import com.protonvpn.app.upgrade.createDynamicPlan
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.proton.core.domain.entity.UserId
import me.proton.core.plan.domain.entity.DynamicPlanInstance
import me.proton.core.plan.domain.entity.DynamicPlanPrice
import me.proton.core.plan.domain.usecase.PerformGiapPurchase
import me.proton.core.plan.presentation.entity.PlanCycle
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
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
    private var oneClickPaymentsEnabled = true
    private var giapPlan: GiapPlanInfo? = null

    @Before
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)

        isInAppAllowed = true
        oneClickPaymentsEnabled = true
        giapPlan = GiapPlanInfo(
            createDynamicPlan(
                "myplan",
                prices = mapOf(
                    Pair(1, mapOf("USD" to DynamicPlanPrice("", currency = "USD", current = 10_00))),
                    Pair(12, mapOf("USD" to DynamicPlanPrice("", currency = "USD", current = 100_00)))
                )
            ),
            "myplan",
            "My Plan",
            listOf(
                CycleInfo(PlanCycle.MONTHLY, "m"),
                CycleInfo(PlanCycle.YEARLY, "y"),
            ),
            PlanCycle.MONTHLY
        )
        performGiapPurchase = mockk()
        viewModel = UpgradeDialogViewModel(
            userId = userIdFlow,
            authOrchestrator = mockk(relaxed = true),
            plansOrchestrator = mockk(relaxed = true),
            isInAppUpgradeAllowed = { isInAppAllowed },
            upgradeTelemetry = mockk(relaxed = true),
            loadDefaultGiapPlan = { giapPlan },
            oneClickPaymentsEnabled = { oneClickPaymentsEnabled },
            loadOnStart = false,
            performGiapPurchase = performGiapPurchase,
            userPlanManager = mockk(relaxed = true),
            waitForSubscription = mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load default plan and purchase`() = testScope.runTest {
        coEvery { performGiapPurchase(any(), any(), any(), any()) } returns
                mockk<PerformGiapPurchase.Result.GiapSuccess>()

        viewModel.loadPlans()

        Assert.assertEquals(PlanCycle.MONTHLY, viewModel.selectedCycle.value)

        val loadedPlan = assertIs<State.PurchaseReady>(viewModel.state.value).plan
        Assert.assertEquals("myplan", loadedPlan.name)
        Assert.assertFalse(assertIs<State.PurchaseReady>(viewModel.state.value).inProgress)

        viewModel.onPaymentStarted(UpgradeFlowType.REGULAR)
        Assert.assertTrue(assertIs<State.PurchaseReady>(viewModel.state.value).inProgress)

        // Fail before succeeding
        viewModel.onErrorInFragment()
        Assert.assertFalse(assertIs<State.PurchaseReady>(viewModel.state.value).inProgress)

        // Try again and succeed
        viewModel.onPaymentStarted(UpgradeFlowType.ONE_CLICK)
        viewModel.pay(mockk(), UpgradeFlowType.ONE_CLICK)
        Assert.assertEquals(
            State.PurchaseSuccess("myplan", UpgradeFlowType.ONE_CLICK),
            viewModel.state.value
        )
    }

    @Test
    fun `in-app payments disabled`() = testScope.runTest {
        isInAppAllowed = false
        viewModel.loadPlans()
        Assert.assertTrue(viewModel.state.value is State.UpgradeDisabled)
    }

    @Test
    fun `enter fallback when 1-click payments disabled`() = testScope.runTest {
        oneClickPaymentsEnabled = false
        viewModel.loadPlans()
        Assert.assertTrue(viewModel.state.value is State.PlansFallback)
    }

    @Test
    fun `enter fallback on plan load fail`() = testScope.runTest {
        giapPlan = null
        viewModel.loadPlans()
        Assert.assertTrue(viewModel.state.value is State.PlansFallback)
    }

    @Test
    fun `calculate price info with savings`() = testScope.runTest {
        val priceInfo = UpgradeDialogViewModel.calculatePriceInfos(
            listOf(
                CycleInfo(PlanCycle.MONTHLY, "m"),
                CycleInfo(PlanCycle.YEARLY, "y")
            ),
            mockk {
                every { instances } returns mapOf(
                    Pair(
                        1, // months
                        DynamicPlanInstance(
                            cycle = 1,
                            description = "1 month",
                            periodEnd = Instant.MAX,
                            price = mapOf("USD" to DynamicPlanPrice(id = "id", currency = "USD", current = 10_00))
                        )
                    ),
                    Pair(
                        12, // months
                        DynamicPlanInstance(
                            cycle = 12,
                            description = "12 month",
                            periodEnd = Instant.MAX,
                            price = mapOf("USD" to DynamicPlanPrice(id = "id", currency = "USD", current = 100_00))
                        )
                    )
                )
            }
        )
        Assert.assertEquals(
            // Checks also the descending order by the cycle length in the map
            listOf(
                PlanCycle.YEARLY to CommonUpgradeDialogViewModel.PriceInfo(
                    formattedPrice = formatPrice(100.0, "USD"),
                    savePercent = -16,
                    formattedPerMonthPrice = formatPrice(8.33, "USD")
                ),
                PlanCycle.MONTHLY to CommonUpgradeDialogViewModel.PriceInfo(
                    formattedPrice = formatPrice(10.0, "USD"),
                )
            ),
            priceInfo.toList()
        )
    }
}
