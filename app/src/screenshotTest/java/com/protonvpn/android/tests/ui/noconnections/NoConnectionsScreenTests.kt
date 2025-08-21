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

package com.protonvpn.android.tests.ui.noconnections

import androidx.compose.runtime.Composable
import com.protonvpn.android.annotations.ProtonVpnTestPreview
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.app.ui.VpnAppViewModel.LoaderState
import com.protonvpn.android.ui.noconnections.NoConnectionsScreen

@ProtonVpnTestPreview
@Composable
fun NoConnectionsScreen_DisabledByAdmin() {
    ProtonVpnPreview(addSurface = false) {
        NoConnectionsScreen(
            state = LoaderState.Error.DisabledByAdmin({}),
            onRefresh = {},
            onLogout = {},
        )
    }
}

@ProtonVpnTestPreview
@Composable
fun NoConnectionsScreen_NoCountriesNoGateways() {
    ProtonVpnPreview(addSurface = false) {
        NoConnectionsScreen(
            state = LoaderState.Error.NoCountriesNoGateways({}),
            onRefresh = {},
            onLogout = {},
        )
    }
}

@ProtonVpnTestPreview
@Composable
fun NoConnectionsScreen_RequestFailed() {
    ProtonVpnPreview(addSurface = false) {
        NoConnectionsScreen(
            state = LoaderState.Error.RequestFailed({}),
            onRefresh = {},
            onLogout = {},
        )
    }
}
