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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    val serverManager: ServerManager,
    val currentUser: CurrentUser,
    val vpnStatusProviderUI: VpnStatusProviderUI
) : ViewModel() {

    data class ProfileItem(
        val profile: Profile,
        val server: Server?,
        val isConnected: Boolean,
        val hasAccess: Boolean
    )

    private val profiles = combine(
        serverManager.profiles,
        serverManager.serverListVersion,
        vpnStatusProviderUI.status,
        currentUser.vpnUserFlow
    ) { allProfiles, _, _, vpnUser ->
        allProfiles.map {
            val server = serverManager.getServerForProfile(it, vpnUser)
            ProfileItem(it, server, isConnected = isConnectedTo(it), hasAccess = vpnUser.hasAccessToServer(server))
        }
    }.shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    val preBakedProfiles: Flow<List<ProfileItem>> = profiles.map { allProfiles ->
        allProfiles.filter { it.profile.isPreBakedProfile }
    }
    val userCreatedProfiles = profiles.map { allProfiles ->
        allProfiles.filter { it.profile.isPreBakedProfile.not() }
    }

    private fun isConnectedTo(profile: Profile) = vpnStatusProviderUI.isConnectedTo(profile)
}
