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
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.base.ui.theme.VpnTheme

class PaymentPanelFragment : Fragment() {

    private val viewModel by activityViewModels<UpgradeDialogViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VpnTheme {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    PaymentPanel(
                        if (state == CommonUpgradeDialogViewModel.State.PlansFallback)
                            ViewState.FallbackFlowReady else ViewState.UpgradeDisabled,
                        null,
                        onStartFallback = viewModel::onStartFallbackUpgrade,
                        onPayClicked = { throw IllegalStateException("Not supported") },
                        onErrorButtonClicked = {},
                        onCloseButtonClicked = { requireActivity().finish() },
                        onCycleSelected = {},
                    )
                }
            }
        }
    }
}
