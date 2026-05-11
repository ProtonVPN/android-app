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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.base.ui.theme.VpnTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import me.proton.core.network.presentation.util.getUserMessage
import me.proton.core.payment.domain.repository.BillingClientError
import me.proton.core.presentation.utils.errorSnack
import me.proton.core.payment.presentation.R as PaymentR

@AndroidEntryPoint
class PaymentPanelFragment : Fragment() {

    private val viewModel by activityViewModels<UpgradeDialogViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewModel.eventErrorMessage.receiveAsFlow()
            .onEach { (messageRes, message, throwable) -> onError(messageRes, message, throwable) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VpnTheme {
                    PaymentPanel(
                        viewModel.fullPanelState.collectAsStateWithLifecycle().value,
                        ::onCloseClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }

    private fun onCloseClicked() {
        requireActivity().finish()
    }

    private fun onError(messageRes: Int?, message: String?, throwable: Throwable?) {
        val fragmentView = view
        fragmentView?.errorSnack(
            message = message
                ?: messageRes?.let { getString(messageRes) }
                ?: getUserMessage(fragmentView.context, throwable)
                ?: getString(PaymentR.string.payments_general_error)
        ) {
            anchorView = fragmentView
        }
    }

    private fun getUserMessage(context: Context, throwable: Throwable?): String? =
        when (throwable) {
            is BillingClientError -> null
            else -> throwable?.getUserMessage(context.resources)
        }
}
