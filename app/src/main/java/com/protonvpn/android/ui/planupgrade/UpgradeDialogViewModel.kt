/*
 * Copyright (c) 2021 Proton Technologies AG
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

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.utils.ServerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.plan.presentation.PlansOrchestrator
import me.proton.core.plan.presentation.onUpgradeResult
import javax.inject.Inject

@HiltViewModel
class UpgradeDialogViewModel @Inject constructor(
    private val currentUser: CurrentUser,
    private val authOrchestrator: AuthOrchestrator,
    private val plansOrchestrator: PlansOrchestrator,
    private val isInAppUpgradeAllowed: IsInAppUpgradeAllowedUseCase,
    private val serverManager: ServerManager,
    private val upgradeTelemetry: UpgradeTelemetry
) : ViewModel() {

    sealed interface State {
        object Init : State
        object Fail : State
        data class Success(val newPlan: String) : State
    }
    val state = MutableStateFlow<State>(State.Init)

    fun reportUpgradeFlowStart(upgradeSource: UpgradeSource) {
        upgradeTelemetry.onUpgradeFlowStarted(upgradeSource)
    }

    fun setupOrchestrators(activity: ComponentActivity) {
        authOrchestrator.register(activity)
        plansOrchestrator.register(activity)

        plansOrchestrator.onUpgradeResult { result ->
            viewModelScope.launch {
                state.value = if (result != null && result.billingResult.subscriptionCreated) {
                    State.Success(result.planId)
                } else {
                    State.Fail
                }
            }
        }
    }

    suspend fun planUpgrade() {
        currentUser.vpnUser()?.userId?.let { userId ->
            upgradeTelemetry.onUpgradeAttempt()
            plansOrchestrator.startUpgradeWorkflow(userId)
        }
    }

    fun showUpgrade() = isInAppUpgradeAllowed()

    fun countriesCount() = serverManager.getVpnCountries().size
}
