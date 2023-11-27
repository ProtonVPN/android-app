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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.proton.core.payment.domain.entity.GooglePurchaseToken
import me.proton.core.payment.domain.entity.PaymentType
import me.proton.core.payment.domain.entity.SubscriptionManagement
import me.proton.core.payment.presentation.entity.BillingInput
import me.proton.core.payment.presentation.viewmodel.BillingCommonViewModel
import me.proton.core.payment.presentation.viewmodel.BillingCommonViewModel.Companion.buildPlansList
import me.proton.core.payment.presentation.viewmodel.BillingViewModel
import me.proton.core.paymentiap.presentation.ui.BaseBillingIAPFragment
import me.proton.core.plan.presentation.entity.PlanCycle
import me.proton.core.presentation.utils.errorSnack
import me.proton.core.presentation.utils.getUserMessage

@AndroidEntryPoint
class PaymentPanelFragment : BaseBillingIAPFragment(0) {

    private val viewModel by activityViewModels<UpgradeDialogViewModel>()
    private val billingViewModel: BillingViewModel by viewModels({ requireActivity() })

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
                            state.plan.name,
                            planCycleResId(state.plan.cycle),
                            state.formattedPrice,
                            state.inProgress
                        )
                }
                is CommonUpgradeDialogViewModel.State.PlanLoaded -> {
                    queryGooglePlan(state.plan.id)
                }
                is CommonUpgradeDialogViewModel.State.LoadError ->
                    onError(state.error.getUserMessage(resources), state.error)
                is CommonUpgradeDialogViewModel.State.PlansFallback ->
                    currentViewState.value = ViewState.FallbackFlowReady
                CommonUpgradeDialogViewModel.State.Initializing -> {
                    currentViewState.value = ViewState.Initializing
                }
                CommonUpgradeDialogViewModel.State.LoadingPlans -> {
                    currentViewState.value = ViewState.LoadingPlans
                }
                CommonUpgradeDialogViewModel.State.UpgradeDisabled ->
                    currentViewState.value = ViewState.UpgradeDisabled
                is CommonUpgradeDialogViewModel.State.PurchaseSuccess -> {
                    // do nothing, will be handled by parent activity
                }
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)

        billingViewModel.subscriptionResult.onEach { state ->
            when (state) {
                is BillingCommonViewModel.State.Error.General -> {
                    onError(state.error.getUserMessage(resources), state.error)
                }
                BillingCommonViewModel.State.Error.SignUpWithPaymentMethodUnsupported -> {
                    onError(getString(R.string.payments_error_signup_paymentmethod), null)
                }
                BillingCommonViewModel.State.Idle,
                BillingCommonViewModel.State.Processing,
                is BillingCommonViewModel.State.Incomplete.TokenApprovalNeeded, // This can only happen for credit card
                is BillingCommonViewModel.State.Success.SignUpTokenReady,
                is BillingCommonViewModel.State.Success.SubscriptionPlanValidated,
                is BillingCommonViewModel.State.Success.TokenCreated -> {
                }
                is BillingCommonViewModel.State.Success.SubscriptionCreated -> {
                    viewModel.onPurchaseSuccess()
                }
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VpnTheme {
                    PaymentPanel(
                        currentViewState.collectAsStateWithLifecycle().value,
                        ::onPayClicked,
                        ::onUpgradeClicked,
                        ::onErrorButtonClicked,
                        ::onCloseClicked,
                    )
                }
            }
        }
    }

    override fun onPriceAvailable(amount: Long, currency: String, formattedPriceAndCurrency: String) {
        viewModel.onPriceAvailable(formattedPriceAndCurrency)
    }

    override fun onPurchaseSuccess(
        productId: String,
        purchaseToken: GooglePurchaseToken,
        orderId: String,
        customerId: String,
        billingInput: BillingInput
    ) {
        billingViewModel.subscribe(
            userId = billingInput.user,
            planNames = billingInput.existingPlans.buildPlansList(
                billingInput.plan.name, billingInput.plan.services, billingInput.plan.type),
            codes = billingInput.codes,
            currency = billingInput.plan.currency,
            cycle = billingInput.plan.subscriptionCycle,
            paymentType = PaymentType.GoogleIAP(
                productId = productId,
                purchaseToken = purchaseToken,
                orderId = orderId,
                packageName = requireContext().packageName,
                customerId = customerId
            ),
            subscriptionManagement = SubscriptionManagement.GOOGLE_MANAGED
        )
    }

    private fun onPayClicked() {
        viewLifecycleOwner.lifecycleScope.launch {
            val billingInput = viewModel.getBillingInput(resources)
            if (billingInput != null) {
                viewModel.onPaymentStarted()
                pay(billingInput)
            } else {
                onError(null, null)
            }
        }
    }

    private fun onUpgradeClicked() {
        viewModel.onStartFallbackUpgrade()
    }

    private fun onErrorButtonClicked() {
        viewModel.loadPlans()
    }

    private fun onCloseClicked() {
        requireActivity().finish()
    }

    private fun onError(message: String?, throwable: Throwable?) {
        panelViewState?.update {
            // If prices are already known don't change the panel state.
            if (it is ViewState.Initializing || it is ViewState.LoadingPlans) ViewState.Error else it
        }
        val fragmentView = view
        fragmentView?.errorSnack(message = message ?: getString(R.string.payments_general_error)) {
            anchorView = fragmentView
        }
        logToSentry(message, throwable) // Remove this once we know payments are in a good shape.
        viewModel.onErrorInFragment()
    }

    override fun onError(@StringRes errorRes: Int) {
        onError(getString(errorRes), null)
    }

    override fun onUserCanceled() {
        viewModel.onUserCancelled()
    }

    @StringRes
    private fun planCycleResId(cycle: PlanCycle): Int = when(cycle) {
        PlanCycle.MONTHLY -> R.string.payment_price_per_month
        PlanCycle.YEARLY -> R.string.payment_price_per_year
        PlanCycle.TWO_YEARS -> R.string.payment_price_per_2years
        PlanCycle.FREE, PlanCycle.OTHER -> throw IllegalArgumentException("Invalid plan cycle")
    }

    private fun logToSentry(errorMessage: String?, throwable: Throwable?) {
        Sentry.captureEvent(SentryEvent(OneClickPaymentError(errorMessage, throwable)).apply {
            // Group events separately for each error message.
            fingerprints = listOf("{{ default }}", errorMessage)
        })
    }
}

private class OneClickPaymentError(message: String?, cause: Throwable?) : Throwable(message, cause)
