/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.android.redesign.settings.ui.nav

import androidx.hilt.navigation.compose.hiltViewModel
import com.protonvpn.android.redesign.app.ui.nav.RootNav
import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.Screen
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel
import com.protonvpn.android.redesign.settings.ui.SubSettingsRoute

object SubSettingsScreen : Screen<SubSettingsScreen.Type, RootNav>("subSettingsScreen") {

    enum class Type {
        Account, Advanced, DefaultConnection, NatType, NetShield, Protocol, SplitTunneling, VpnAccelerator
    }

    fun SafeNavGraphBuilder<RootNav>.subSettings(
        onClose: () -> Unit,
        onNavigateToSubSetting: (Type) -> Unit,
    ) = addToGraph(this) { entry ->
        val type = getArgs<Type>(entry)
        val viewModel = hiltViewModel<SettingsViewModel>()
        SubSettingsRoute(viewModel, type, onClose, onNavigateToSubSetting)
    }
}
