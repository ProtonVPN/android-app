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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.ui.planupgrade.CommonUpgradeDialogViewModel
import com.protonvpn.android.ui.planupgrade.UpgradeDialogViewModel
import com.protonvpn.android.ui.planupgrade.usecase.CycleInfo
import com.protonvpn.android.ui.planupgrade.usecase.GiapPlanInfo
import com.protonvpn.android.utils.formatPrice
import com.protonvpn.app.upgrade.createDynamicPlan
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.proton.core.domain.entity.UserId
import me.proton.core.paymentiap.presentation.viewmodel.GoogleProductDetails
import me.proton.core.paymentiap.presentation.viewmodel.GoogleProductId
import me.proton.core.plan.presentation.entity.PlanCycle
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpgradeDialogViewModelTests {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private val userIdFlow = MutableStateFlow<UserId?>(null)

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
            createDynamicPlan("myplan"),
            "myplan",
            "My Plan",
            listOf(
                CycleInfo(PlanCycle.MONTHLY, "m"),
                CycleInfo(PlanCycle.YEARLY, "y"),
            ),
            PlanCycle.MONTHLY
        )
        viewModel = UpgradeDialogViewModel(
            userId = userIdFlow,
            authOrchestrator = mockk(relaxed = true),
            plansOrchestrator = mockk(relaxed = true),
            isInAppUpgradeAllowed = { isInAppAllowed },
            upgradeTelemetry = mockk(relaxed = true),
            loadDefaultGiapPlan = { giapPlan },
            oneClickPaymentsEnabled = { oneClickPaymentsEnabled },
            false
        )
    }

    @Test
    fun `load default plan and purchase`() = testScope.runTest {
        viewModel.loadPlans()

        Assert.assertEquals(PlanCycle.MONTHLY, viewModel.selectedCycle.value)
        val loadedPlan = (viewModel.state.value as? CommonUpgradeDialogViewModel.State.PlanLoaded)?.plan
        Assert.assertEquals("myplan", loadedPlan?.name)

        viewModel.onPricesAvailable(
            mapOf(
                GoogleProductId("m") to priceDetails(10),
                GoogleProductId("y") to priceDetails(100)
            )
        )
        Assert.assertTrue((viewModel.state.value as? CommonUpgradeDialogViewModel.State.PurchaseReady)?.inProgress == false)

        viewModel.onPaymentStarted()
        Assert.assertTrue((viewModel.state.value as? CommonUpgradeDialogViewModel.State.PurchaseReady)?.inProgress == true)

        // Fail before succeeding
        viewModel.onErrorInFragment()
        Assert.assertTrue((viewModel.state.value as? CommonUpgradeDialogViewModel.State.PurchaseReady)?.inProgress == false)

        // Try again and succeed
        viewModel.onPaymentStarted()
        viewModel.onPurchaseSuccess()
        Assert.assertEquals(
            CommonUpgradeDialogViewModel.State.PurchaseSuccess("myplan", "My Plan"),
            viewModel.state.value
        )
    }

    @Test
    fun `in-app payments disabled`() = testScope.runTest {
        isInAppAllowed = false
        viewModel.loadPlans()
        Assert.assertTrue(viewModel.state.value is CommonUpgradeDialogViewModel.State.UpgradeDisabled)
    }

    @Test
    fun `enter fallback when 1-click payments disabled`() = testScope.runTest {
        oneClickPaymentsEnabled = false
        viewModel.loadPlans()
        Assert.assertTrue(viewModel.state.value is CommonUpgradeDialogViewModel.State.PlansFallback)
    }

    @Test
    fun `enter fallback on plan load fail`() = testScope.runTest {
        giapPlan = null
        viewModel.loadPlans()
        Assert.assertTrue(viewModel.state.value is CommonUpgradeDialogViewModel.State.PlansFallback)
    }

    @Test
    fun `calculate price info with savings`() = testScope.runTest {
        val priceInfo = UpgradeDialogViewModel.calculatePriceInfos(
            listOf(
                CycleInfo(PlanCycle.MONTHLY, "m"),
                CycleInfo(PlanCycle.YEARLY, "y")
            ),
            mapOf(
                GoogleProductId("m") to priceDetails(10),
                GoogleProductId("y") to priceDetails(100)
            )
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

private fun priceDetails(price: Int) = GoogleProductDetails(
    priceAmountMicros = price * 1000000L,
    currency = "USD",
    formattedPriceAndCurrency = formatPrice(price.toDouble(), "USD")
)