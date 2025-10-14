/*
 * Copyright (c) 2024. Proton AG
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
package com.protonvpn.android.redesign.app.ui

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.appconfig.periodicupdates.UpdateState
import com.protonvpn.android.auth.LOGIN_GUEST_HOLE_ID
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.PartialJointUserInfo
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.servers.UpdateServerListFromApi
import com.protonvpn.android.ui.drawer.bugreport.DynamicReportActivity
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.openUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val GUEST_HOLE_ID = "VpnAppViewModel"

@HiltViewModel
class VpnAppViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    serverManager: ServerManager2,
    private val serverListUpdater: ServerListUpdater,
    currentUser: CurrentUser,
    private val userPlanManager: UserPlanManager,
    private val guestHole: GuestHole,
) : ViewModel() {

    private val serversLoaderState = combine(
        serverListUpdater.updateState,
        serverManager.hasAnyCountryFlow,
        serverManager.hasAnyGatewaysFlow,
    ) { updateState, hasCountries, hasGateways ->
        if (isServerListReady(hasCountries, hasGateways)) {
            LoaderState.Loaded
        } else {
            when (updateState) {
                is UpdateState.Idle -> {
                    when (updateState.lastResult) {
                        is UpdateServerListFromApi.Result.Error -> LoaderState.Error.RequestFailed(::updateServerList)
                        UpdateServerListFromApi.Result.Success ->
                            LoaderState.Error.NoCountriesNoGateways(::updateServerList)
                        null ->
                            // Null means that update hasn't started yet. ServerListUpdater starts
                            // the update in response to eventPartialLogin but the asynchronous
                            // processing means that events can be handled in different orders so
                            // emit "Loading" for a seamless UI transition.
                            LoaderState.Loading
                    }
                }

                UpdateState.Updating -> LoaderState.Loading
            }
        }
    }

    private val usersLoaderState = combine(
        userPlanManager.updateState,
        currentUser.partialJointUserFlow,
    ) { updateState, partialUser ->
        if (isUserReady(partialUser)) {
            LoaderState.Loaded
        } else {
            when (updateState) {
                is UpdateState.Idle -> {
                    when (updateState.lastResult) {
                        UserPlanManager.UpdateResult.UpdateError -> LoaderState.Error.RequestFailed(::updateVpnUser)
                        UserPlanManager.UpdateResult.NoConnectionsAssigned ->
                            LoaderState.Error.DisabledByAdmin(::updateVpnUser)
                        null -> LoaderState.Error.RequestFailed(::updateVpnUser)
                        // Set loading because Success should only be reported when a valid user is being set to DB.
                        // The next update emitted by partialJointUserFlow should include VpnUser and switch state to
                        // Loaded.
                        UserPlanManager.UpdateResult.Success -> LoaderState.Loading
                    }
                }

                UpdateState.Updating -> LoaderState.Loading
            }
        }
    }

    val loadingState = combine(usersLoaderState, serversLoaderState) { userState, serversState ->
        if (userState != LoaderState.Loaded) {
            userState
        } else {
            serversState
        }
    }
        .distinctUntilChanged()
        .onEach {
            if (it == LoaderState.Loaded) {
                releaseGuestHolesLocks()
            } else {
                guestHole.acquireNeedGuestHole(GUEST_HOLE_ID)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

    override fun onCleared() {
        viewModelScope.launch(NonCancellable) {
            releaseGuestHolesLocks()
        }
    }

    private suspend fun releaseGuestHolesLocks() {
        guestHole.releaseNeedGuestHole(LOGIN_GUEST_HOLE_ID)
        guestHole.releaseNeedGuestHole(GUEST_HOLE_ID)
    }

    private fun updateServerList() {
        mainScope.launch {
            serverListUpdater.updateServerList(forceFreshUpdate = true)
        }
    }

    private fun updateVpnUser() {
        mainScope.launch {
            userPlanManager.refreshVpnInfo()
        }
    }

    private fun isServerListReady(hasCountries: Boolean, hasGateways: Boolean): Boolean =
        hasCountries || hasGateways

    // Note: this condition is a bit weak.
    private fun isUserReady(partialJointUserInfo: PartialJointUserInfo): Boolean =
        partialJointUserInfo.user != null && partialJointUserInfo.vpnUser != null

    sealed interface LoaderState {

        data object Loading : LoaderState

        data object Loaded : LoaderState

        sealed class Error(
            @StringRes val descriptionResId: Int,
            @StringRes val helpResId: Int?,
            val linkAnnotationAction: ((Context) -> Unit)?,
        ) : LoaderState {
            abstract val retryAction: () -> Unit

            @StringRes
            val titleResId: Int = R.string.no_connections_title

            data class DisabledByAdmin(override val retryAction: () -> Unit) : Error(
                descriptionResId = R.string.no_connections_description_no_servers,
                helpResId = R.string.no_connections_help_follow_instructions,
                linkAnnotationAction = { context -> context.openUrl(url = Constants.URL_ENABLE_VPN_CONNECTION) },
            )

            data class NoCountriesNoGateways(override val retryAction: () -> Unit) : Error(
                descriptionResId = R.string.no_connections_description_no_servers,
                helpResId = null,
                linkAnnotationAction = null,
            )

            // The description is intentionally the same for servers and VPN user.
            data class RequestFailed(override val retryAction: () -> Unit) : Error(
                descriptionResId = R.string.no_connections_description_loading_error,
                helpResId = R.string.no_connections_help_contact_us,
                linkAnnotationAction = { context ->
                    context.startActivity(Intent(context, DynamicReportActivity::class.java))
                },
            )
        }
    }
}
