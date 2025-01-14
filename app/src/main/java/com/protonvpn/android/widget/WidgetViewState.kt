/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.widget

import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState

enum class WidgetVpnStatus {
    Connected, Connecting, Disconnected, WaitingForNetwork, Error
}

data class WidgetRecent(
    val recentId: Long?,
    val connectIntentViewState: ConnectIntentViewState
)

sealed interface WidgetViewState {
    object NeedLogin : WidgetViewState
    data class LoggedIn(
        val connectCard: ConnectIntentViewState,
        val vpnStatus: WidgetVpnStatus,
        val recents: List<WidgetRecent>
    ) : WidgetViewState
}
