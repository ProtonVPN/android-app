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

import androidx.glance.action.Action
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState

enum class WidgetVpnStatus {
    Connected, Connecting, Disconnected, WaitingForNetwork
}

data class WidgetRecent(
    val action: Action,
    val connectIntentViewState: ConnectIntentViewState
)

sealed interface WidgetViewState {
    val launchMainActivityAction: Action

    data class NeedLogin(
        override val launchMainActivityAction: Action
    ) : WidgetViewState

    data class LoggedIn(
        val connectCard: ConnectIntentViewState,
        val connectCardAction: Action,
        val vpnStatus: WidgetVpnStatus,
        private val recents: List<WidgetRecent>,
        override val launchMainActivityAction: Action,
    ) : WidgetViewState {
        // Recents that are avoiding duplication with connect card (can happen when we're connected to pinned recent).
        fun recentsWithoutPinnedConnectCard(): List<WidgetRecent> = recents.filter { it.connectIntentViewState != connectCard }
    }

    data class NoServersAvailable(override val launchMainActivityAction: Action) : WidgetViewState

}
