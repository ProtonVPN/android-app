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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.protonvpn.android.profiles.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.profiles.data.Profile
import com.protonvpn.android.profiles.data.ProfileInfo
import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.profiles.usecases.DeleteProfileFromUi
import com.protonvpn.android.redesign.home_screen.ui.ShowcaseRecents
import com.protonvpn.android.redesign.main_screen.ui.ShouldShowcaseRecents
import com.protonvpn.android.redesign.recents.usecases.GetIntentAvailability
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentAvailability
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewStateProfile
import com.protonvpn.android.redesign.vpn.ui.GetConnectIntentViewState
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.ui.storage.UiStateStorage
import com.protonvpn.android.utils.Quadruple
import com.protonvpn.android.utils.flatMapLatestNotNull
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnConnect
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.proton.core.presentation.savedstate.state
import javax.inject.Inject

private const val SELECTED_PROFILE_KEY = "selected_profile_id"

sealed class ProfilesState {
    data object ZeroState : ProfilesState()
    data class ProfilesList(val profiles: List<ProfileViewItem>) : ProfilesState()
}

data class ProfileViewItem(
    val profile: ProfileInfo,
    val isConnected: Boolean,
    val availability: ConnectIntentAvailability,
    val intent: ConnectIntentViewStateProfile,
    val netShieldEnabled: Boolean,
    val protocol: ProtocolSelection,
    val natType: NatType,
    val lanConnections: Boolean,
)

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    savedStateHandle: SavedStateHandle,
    private val profilesDao: ProfilesDao,
    private val deleteProfile: DeleteProfileFromUi,
    currentUser: CurrentUser,
    private val connect: VpnConnect,
    private val shouldShowcaseRecents: ShouldShowcaseRecents,
    vpnStatusProviderUI: VpnStatusProviderUI,
    private val getIntentAvailability: GetIntentAvailability,
    private val getConnectIntentViewState: GetConnectIntentViewState,
    private val effectiveCurrentUserSettings: EffectiveCurrentUserSettings,
    private val uiStateStorage: UiStateStorage,
) : ViewModel() {

    private val connectedProfileIdFlow = vpnStatusProviderUI.uiStatus
        .map { if (it.state == VpnState.Connected) it.connectIntent?.profileId else null }
        .distinctUntilChanged()

    private var selectedProfileId by savedStateHandle.state<Long?>(null, SELECTED_PROFILE_KEY)

    sealed class Dialog {
        data class ServerUnavailable(val profileName: String) : Dialog()
    }
    val showDialog = MutableStateFlow<Dialog?>(null)
    val autoShowInfoSheet = uiStateStorage.state.map { !it.hasShownProfilesInfo }.distinctUntilChanged()

    val selectedProfile = combine(
        currentUser.vpnUserFlow,
        connectedProfileIdFlow,
        savedStateHandle.getStateFlow(SELECTED_PROFILE_KEY, selectedProfileId),
        effectiveCurrentUserSettings.protocol,
    ) { vpnUser, connectedProfileId, profileId, settingsProtocol ->
        Quadruple(vpnUser, connectedProfileId, profileId, settingsProtocol)
    }.flatMapLatest { (vpnUser, connectedProfileId, profileId, settingsProtocol) ->
        if (profileId == null || vpnUser == null)
            flowOf(null)
        else
            profilesDao.getProfileByIdFlow(profileId).map { it?.toItem(vpnUser, connectedProfileId, settingsProtocol) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val state = currentUser.vpnUserFlow.flatMapLatestNotNull { vpnUser ->
        profilesDao.getProfiles(vpnUser.userId).flatMapLatest { profiles ->
            if (profiles.isEmpty())
                flowOf(ProfilesState.ZeroState)
            else
                profileListState(profiles, vpnUser)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private fun profileListState(profiles: List<Profile>, vpnUser: VpnUser) =
        combine(
            connectedProfileIdFlow,
            effectiveCurrentUserSettings.protocol,
        ) { connectedProfileId, settingsProtocol ->
            val items = profiles.map { it.toItem(vpnUser, connectedProfileId, settingsProtocol) }
            ProfilesState.ProfilesList(items)
        }

    init {
        mainScope.launch {
            uiStateStorage.update { it.copy(shouldPromoteProfiles = false) }
        }
    }

    fun onConnect(
        item: ProfileViewItem,
        vpnUiDelegate: VpnUiDelegate,
        navigateToHome: (ShowcaseRecents) -> Unit,
        navigateToUpsell: () -> Unit
    ) {
        selectedProfileId = null
        when (item.availability) {
            ConnectIntentAvailability.SERVER_REMOVED,
            ConnectIntentAvailability.UNAVAILABLE_PROTOCOL,
            ConnectIntentAvailability.AVAILABLE_OFFLINE ->
                showDialog.value = Dialog.ServerUnavailable(item.profile.name)
            ConnectIntentAvailability.UNAVAILABLE_PLAN -> navigateToUpsell()
            ConnectIntentAvailability.ONLINE -> {
                mainScope.launch {
                    val trigger = ConnectTrigger.Profile
                    val connectIntent = if (!item.isConnected)
                        profilesDao.getProfileById(item.profile.id)?.connectIntent
                    else
                        null

                    if (connectIntent != null)
                        connect(vpnUiDelegate, connectIntent, trigger)
                    navigateToHome(connectIntent != null && shouldShowcaseRecents(connectIntent))
                }
            }
        }
    }

    fun dismissDialog() {
        showDialog.value = null
    }

    fun onSelect(item: ProfileViewItem) {
        selectedProfileId = item.profile.id
    }

    fun onProfileClose() {
        selectedProfileId = null
    }

    fun onAutoShowInfoSheet() {
        mainScope.launch {
            uiStateStorage.update { it.copy(hasShownProfilesInfo = true) }
        }
    }

    private suspend fun Profile.toItem(
        vpnUser: VpnUser,
        connectedProfileId: Long?,
        settingsProtocol: ProtocolSelection
    ): ProfileViewItem {
        val isConnected = info.id == connectedProfileId
        val intent = connectIntent
        val availability = when {
            vpnUser.isFreeUser -> ConnectIntentAvailability.UNAVAILABLE_PLAN
            else -> getIntentAvailability(intent, vpnUser, settingsProtocol)
        }
        val intentViewState = getConnectIntentViewState.forProfile(this)
        val netShieldEnabled = intent.settingsOverrides?.netShield == NetShieldProtocol.ENABLED_EXTENDED
        val protocol = intent.settingsOverrides?.protocol ?: settingsProtocol
        val natType = NatType.fromRandomizedNat(intent.settingsOverrides?.randomizedNat == true)
        val lanConnections = intent.settingsOverrides?.lanConnections == true
        return ProfileViewItem(
            info,
            isConnected = isConnected,
            availability = availability,
            intent = intentViewState,
            netShieldEnabled = netShieldEnabled,
            protocol = protocol,
            natType = natType,
            lanConnections = lanConnections,
        )
    }

    fun onProfileDelete(item: ProfileViewItem, undoDurationMs: Long): DeleteProfileFromUi.UndoOperation {
        selectedProfileId = null
        return deleteProfile(item.profile.id, undoDurationMs)
    }
}
