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

package com.protonvpn.android.tv.vpn.ui

import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

enum class VpnStatusTvViewState { Unprotected, Connecting, Protected }

@Reusable
class VpnStatusTvFlow @Inject constructor(
    private val vpnStatusProviderUI: VpnStatusProviderUI,
) {
    // Emits null while the VPN state is loading.
    fun observe(): Flow<VpnStatusTvViewState?> = vpnStatusProviderUI.uiStatus
        .map { status -> status.state.toViewState() }
        .distinctUntilChanged()

    private fun VpnState.toViewState(): VpnStatusTvViewState? = when {
        this == VpnState.Loading -> null
        this == VpnState.Connected -> VpnStatusTvViewState.Protected
        // Includes waiting for network and non-final errors.
        isEstablishingConnection -> VpnStatusTvViewState.Connecting
        else -> VpnStatusTvViewState.Unprotected
    }
}
