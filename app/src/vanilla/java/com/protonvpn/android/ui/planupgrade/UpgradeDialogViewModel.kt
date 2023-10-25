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

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.telemetry.UpgradeTelemetry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.plan.presentation.PlansOrchestrator
import javax.inject.Inject

@HiltViewModel
class UpgradeDialogViewModel @Inject constructor(
    currentUser: CurrentUser,
    authOrchestrator: AuthOrchestrator,
    plansOrchestrator: PlansOrchestrator,
    isInAppUpgradeAllowed: IsInAppUpgradeAllowedUseCase,
    upgradeTelemetry: UpgradeTelemetry
) : CommonUpgradeDialogViewModel(
    currentUser.userFlow.map { it?.userId },
    authOrchestrator,
    plansOrchestrator,
    isInAppUpgradeAllowed::invoke,
    upgradeTelemetry,
) {
    init {
        state.value = if (isInAppUpgradeAllowed())
            State.PlansFallback
        else
            State.UpgradeDisabled
    }
}
