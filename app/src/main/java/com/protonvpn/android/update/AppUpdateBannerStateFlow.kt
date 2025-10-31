/*
 * Copyright (c) 2025. Proton AG
 *
 *  This file is part of ProtonVPN.
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

package com.protonvpn.android.update

import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.isConnectedOrConnecting
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

sealed interface AppUpdateBannerState {
    object Hidden : AppUpdateBannerState
    class Shown(
        val showVpnConnectedWarning: Boolean,
        val appUpdateInfo: AppUpdateInfo
    ) : AppUpdateBannerState
}

interface AppUpdateBannerStateFlow : Flow<AppUpdateBannerState>

@Reusable
class AppUpdateBannerStateFlowImpl @Inject constructor(
    appUpdateManager: AppUpdateManager,
    vpnStatusProviderUI: VpnStatusProviderUI,
    isAppUpdateBannerFeatureFlagEnabled: IsAppUpdateBannerFeatureFlagEnabled,
) : AppUpdateBannerStateFlow {

    private val flow = isAppUpdateBannerFeatureFlagEnabled.observe()
        .flatMapLatest{ isEnabled ->
            if (isEnabled) {
                updateFlow
            } else {
                flowOf(AppUpdateBannerState.Hidden)
            }
        }

    private val updateFlow = combine(
        appUpdateManager.checkForUpdateFlow,
        vpnStatusProviderUI.status.map { it.state.isConnectedOrConnecting() },
    ) { appUpdate, isConnectedOrConnecting ->
        if (appUpdate != null) {
            AppUpdateBannerState.Shown(
                showVpnConnectedWarning = isConnectedOrConnecting,
                appUpdateInfo = appUpdate
            )
        } else {
            AppUpdateBannerState.Hidden
        }
    }

    override suspend fun collect(collector: FlowCollector<AppUpdateBannerState>) {
        flow.collect(collector)
    }

}