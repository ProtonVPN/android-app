/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.ui.home.profiles

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.protonvpn.android.R
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.LiveEvent
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnStateMonitor
import javax.inject.Inject

class ProfilesViewModel @Inject constructor(
    val serverManager: ServerManager,
    val userData: UserData,
    val stateMonitor: VpnStateMonitor
) : ViewModel() {

    val profilesUpdateEvent: LiveEvent get() = serverManager.profilesUpdateEvent

    val profileCount: Int get() = serverManager.savedProfiles.size

    fun getProfile(position: Int): Profile = serverManager.savedProfiles[position]

    fun isConnectedTo(server: Server?) = server != null && stateMonitor.isConnectedTo(server)

    @StringRes
    fun getConnectTextRes(server: Server): Int = if (userData.hasAccessToServer(server))
        R.string.connect else R.string.upgrade
}
