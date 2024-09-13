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
package com.protonvpn.android.profiles.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.profiles.data.Profile
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileEntity
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.profiles.data.toProfile
import com.protonvpn.android.profiles.data.toProfileEntity
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.redesign.vpn.ConnectIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateEditProfileViewModel @Inject constructor(
    private val profilesDao: ProfilesDao,
    private val currentUser: CurrentUser,
    @WallClock private val wallClock: () -> Long,
) : ViewModel() {

    private var currentProfileId: MutableStateFlow<Long?> = MutableStateFlow(null)
    private val tempProfile: MutableStateFlow<Profile?> = MutableStateFlow(null)

    fun setEditableProfileId(profileId: Long?) {
        currentProfileId.value = profileId
    }

    private val defaultProfileFlow = currentUser.vpnUserFlow.map { vpnUser ->
        vpnUser?.let {
            ProfileEntity(
                userId = it.userId,
                name = "",
                color = ProfileColor.Color1,
                icon = ProfileIcon.Icon2,
                createdAt = wallClock(),
                connectIntentData = ConnectIntent.Default.toData(),
            ).toProfile()
        }
    }

    fun saveTempProfile(profile: Profile) {
        tempProfile.value = profile
    }

    fun saveProfile(profile: Profile) {
        viewModelScope.launch {
            currentUser.vpnUser()?.userId?.let {
                profilesDao.upsert(profile.toProfileEntity(it, wallClock()))
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val profileInitializationFlow: StateFlow<Profile?> = currentProfileId
        .flatMapLatest { id ->
            if (id != null) {
                profilesDao.getProfileByIdFlow(id)
            } else {
                defaultProfileFlow
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

    val currentProfile: StateFlow<Profile?> =
        combine(tempProfile, profileInitializationFlow) { tempProfile, initializedProfile ->
            tempProfile ?: initializedProfile
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

}