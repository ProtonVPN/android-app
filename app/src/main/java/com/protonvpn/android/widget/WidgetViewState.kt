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

import android.content.ComponentName
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState

enum class WidgetVpnStatus {
    Connected, Connecting, Disconnected, WaitingForNetwork
}

data class WidgetRecent(
    val recentId: Long?,
    val connectIntentViewState: ConnectIntentViewState
)

sealed interface WidgetViewState {
    val launchActivity: ComponentName

    data class NeedLogin(
        override val launchActivity: ComponentName
    ) : WidgetViewState

    data class LoggedIn(
        val connectCard: ConnectIntentViewState,
        val vpnStatus: WidgetVpnStatus,
        val recents: List<WidgetRecent>,
        override val launchActivity: ComponentName
    ) : WidgetViewState

    {
        val isConnecting get() =
            vpnStatus in listOf(WidgetVpnStatus.Connecting, WidgetVpnStatus.WaitingForNetwork)

        // List of recents to be used without separate quick connect card.
        fun mergedRecents() = (listOf(WidgetRecent(null, connectCard)) + recents).distinct()

        // Recents that are avoiding duplication with connect card (can happen when we're connected to pinned recent).
        fun recentsWithoutPinnedConnectCard(): List<WidgetRecent> = recents.filter { it.connectIntentViewState != connectCard }
    }
}
