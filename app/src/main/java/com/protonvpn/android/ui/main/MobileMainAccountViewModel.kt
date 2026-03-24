/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.ui.main

import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.VpnApiClient
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.auth.AuthFlowStartHelper
import com.protonvpn.android.auth.usecase.HumanVerificationGuestHoleCheck
import com.protonvpn.android.managed.AutoLoginManager
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.auth.presentation.AuthOrchestrator
import javax.inject.Inject

@HiltViewModel
class MobileMainAccountViewModel @Inject constructor(
    api: ProtonApiRetroFit, authOrchestrator: AuthOrchestrator,
    accountManager: AccountManager,
    vpnApiClient: VpnApiClient,
    guestHole: Lazy<GuestHole>,
    humanVerificationGuestHoleCheck: HumanVerificationGuestHoleCheck,
    authFlowTriggerHelper: AuthFlowStartHelper,
    autoLoginManager: AutoLoginManager,
    private val appFeaturesPrefs: AppFeaturesPrefs,
) : AccountViewModel(
    api,
    authOrchestrator,
    accountManager,
    vpnApiClient,
    guestHole,
    humanVerificationGuestHoleCheck,
    authFlowTriggerHelper,
    autoLoginManager,
) {
    override fun onAccountAddSuccess(userId: String) {
        appFeaturesPrefs.showOnboardingUserId = userId
    }
}