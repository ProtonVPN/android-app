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

package com.protonvpn.android.ui.planupgrade

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.VpnTheme
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Sentry
import io.sentry.SentryEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import me.proton.core.network.domain.ApiException
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.presentation.util.getUserMessage
import me.proton.core.payment.domain.repository.BillingClientError
import me.proton.core.plan.presentation.entity.PlanCycle
import me.proton.core.presentation.utils.errorSnack
import me.proton.core.payment.presentation.R as PaymentR

@AndroidEntryPoint
class PaymentPanelFragment : Fragment() {

    private val viewModel by activityViewModels<UpgradeDialogViewModel>()

    // Needs to be class member for onError().
    private var panelViewState: MutableStateFlow<ViewState>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val currentViewState = MutableStateFlow<ViewState>(ViewState.Initializing)
        panelViewState = currentViewState

        viewModel.state.onEach { state ->
            when (state) {
                is CommonUpgradeDialogViewModel.State.PurchaseReady -> {
                    panelViewState?.value =
                        ViewState.PlanReady(
                            displayName = state.selectedPlan.displayName,
                            planName = state.selectedPlan.planName,
                            cycles = state.selectedPlanPriceInfo.map { (cycle, priceInfo) ->
                                // Don't show "/cycle" for welcome offers
                                val perCycleResId =
                                    if (priceInfo.formattedRenewPrice == null) planPerCycleResId(cycle)
                                    else null
                                ViewState.CycleViewInfo(
                                    cycle,
                                    perCycleResId,
                                    planCycleLabelResId(cycle),
                                    priceInfo
                                )
                            },
                            inProgress = state.inProgress,
                            buttonLabelOverride = state.buttonLabelOverride,
                        )
                }
                is CommonUpgradeDialogViewModel.State.LoadError -> {
                    val message = state.messageRes?.let { resources.getString(it) }
                    onError(message, state.error)
                }
                is CommonUpgradeDialogViewModel.State.PlansFallback ->
                    currentViewState.value = ViewState.FallbackFlowReady
                CommonUpgradeDialogViewModel.State.Initializing -> {
                    currentViewState.value = ViewState.Initializing
                }
                is CommonUpgradeDialogViewModel.State.LoadingPlans -> {
                    currentViewState.value = ViewState.LoadingPlans(state.expectedCycleCount, state.buttonLabelOverride)
                }
                CommonUpgradeDialogViewModel.State.UpgradeDisabled ->
                    currentViewState.value = ViewState.UpgradeDisabled
                is CommonUpgradeDialogViewModel.State.PurchaseSuccess -> {
                    // do nothing, will be handled by parent activity
                }
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel.eventPurchaseError.receiveAsFlow()
            .onEach { error -> onError(null, error.billingClientError) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VpnTheme {
                    PaymentPanel(
                        currentViewState.collectAsStateWithLifecycle().value,
                        viewModel.selectedCycle.collectAsStateWithLifecycle().value,
                        ::onPayClicked,
                        ::onUpgradeClicked,
                        ::onErrorButtonClicked,
                        ::onCloseClicked,
                        ::onCycleSelected
                    )
                }
            }
        }
    }

    private fun onPayClicked() {
        val flowType = UpgradeFlowType.ONE_CLICK
        viewModel.onPaymentStarted(flowType)
        viewModel.pay(requireActivity(), flowType)
    }

    private fun onUpgradeClicked() {
        viewModel.onStartFallbackUpgrade()
    }

    private fun onErrorButtonClicked() {
        viewModel.reloadPlans()
    }

    private fun onCloseClicked() {
        requireActivity().finish()
    }

    private fun onCycleSelected(cycle: PlanCycle) {
        viewModel.selectedCycle.value = cycle
    }

    private fun onError(message: String?, throwable: Throwable?, allowReportToSentry: Boolean = true) {
        panelViewState?.update {
            // If prices are already known don't change the panel state.
            if (it is ViewState.Initializing || it is ViewState.LoadingPlans) ViewState.Error else it
        }
        val fragmentView = view
        fragmentView?.errorSnack(
            message = message
                ?: getUserMessage(fragmentView.context, throwable)
                ?: getString(PaymentR.string.payments_general_error)
        ) {
            anchorView = fragmentView
        }
        if (allowReportToSentry && shouldReportToSentry(throwable))
            logToSentry(message ?: throwable?.message, throwable) // Remove this once we know payments are in a good shape.
    }

    private fun getUserMessage(context: Context, throwable: Throwable?): String? =
        when (throwable) {
            is BillingClientError -> null
            else -> throwable?.getUserMessage(context.resources)
        }

    private fun shouldReportToSentry(throwable: Throwable?): Boolean =
        throwable == null || (throwable as? ApiException)?.error !is ApiResult.Error.Connection

    @StringRes
    private fun planPerCycleResId(cycle: PlanCycle): Int = when(cycle) {
        PlanCycle.MONTHLY -> R.string.payment_price_per_month
        PlanCycle.YEARLY -> R.string.payment_price_per_year
        PlanCycle.TWO_YEARS -> R.string.payment_price_per_2years
        PlanCycle.FREE, PlanCycle.OTHER -> throw IllegalArgumentException("Invalid plan cycle")
    }

    @StringRes
    private fun planCycleLabelResId(cycle: PlanCycle): Int = when(cycle) {
        PlanCycle.MONTHLY -> R.string.payment_price_cycle_month_label
        PlanCycle.YEARLY -> R.string.payment_price_cycle_year_label
        PlanCycle.TWO_YEARS -> R.string.payment_price_cycle_2years_label
        PlanCycle.FREE, PlanCycle.OTHER -> throw IllegalArgumentException("Invalid plan cycle")
    }

    private fun logToSentry(errorMessage: String?, throwable: Throwable?) {
        Sentry.captureEvent(SentryEvent(OneClickPaymentError(errorMessage, throwable)))
    }
}

private class OneClickPaymentError(message: String?, cause: Throwable?) : Throwable(message, cause)
