/*
 * Copyright (c) 2025. Proton AG
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

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class UpgradeActivityHelper(
    private val activity: ComponentActivity,
    private val afterPaymentSuccess: (newPlanName: String) -> Unit = {}
) {

    fun onCreate(viewModel: UpgradeDialogViewModel) {
        viewModel.state
            .flowWithLifecycle(activity.lifecycle)
            .onEach(::onStateUpdate)
            .launchIn(activity.lifecycleScope)
    }

    private fun onStateUpdate(state: CommonUpgradeDialogViewModel.State) {
        when (state) {
            CommonUpgradeDialogViewModel.State.Initializing -> {}
            CommonUpgradeDialogViewModel.State.UpgradeDisabled -> {}
            is CommonUpgradeDialogViewModel.State.LoadingPlans -> {}
            is CommonUpgradeDialogViewModel.State.LoadError -> {}
            is CommonUpgradeDialogViewModel.State.PurchaseReady -> {}
            CommonUpgradeDialogViewModel.State.PlansFallback -> {}
            is CommonUpgradeDialogViewModel.State.PurchaseSuccess -> {
                onPaymentSuccess(state.newPlanName)
            }
        }
    }

    private fun onPaymentSuccess(newPlanName: String) {
        activity.setResult(Activity.RESULT_OK)
        activity.finish()
        afterPaymentSuccess(newPlanName)
    }
}
