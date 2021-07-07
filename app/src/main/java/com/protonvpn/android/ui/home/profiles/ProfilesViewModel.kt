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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

class ProfilesViewModel @Inject constructor(
    val serverManager: ServerManager,
    val userData: UserData,
    val stateMonitor: VpnStateMonitor
) : ViewModel() {

    data class ProfileItem(
        val profile: Profile,
        val isConnected: Boolean,
        val hasAccess: Boolean
    )

    private val profiles = MutableLiveData<List<ProfileItem>>(getProfiles())
    val preBakedProfiles: LiveData<List<ProfileItem>> = Transformations.map(profiles) { allProfiles ->
        allProfiles.filter { it.profile.isPreBakedProfile }
    }
    val userCreatedProfiles = Transformations.map(profiles) { allProfiles ->
        allProfiles.filter { it.profile.isPreBakedProfile.not() }
    }

    init {
        viewModelScope.launch {
            stateMonitor.status.collect { updateProfiles() }
        }
        viewModelScope.launch {
            serverManager.profilesUpdateEvent.collect { updateProfiles() }
        }
    }

    private fun isConnectedTo(server: Server?) = server != null && stateMonitor.isConnectedTo(server)

    private fun hasAccessToServer(server: Server?) = userData.hasAccessToServer(server)

    private fun updateProfiles() {
        profiles.value = getProfiles()
    }

    private fun getProfiles(): List<ProfileItem> =
        serverManager.getSavedProfiles().map {
            val server = it.server
            ProfileItem(it, isConnected = isConnectedTo(server), hasAccess = hasAccessToServer(server))
        }
}
