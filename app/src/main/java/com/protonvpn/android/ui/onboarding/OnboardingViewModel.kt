/*
 * Copyright (c) 2021 Proton Technologies AG
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

package com.protonvpn.android.ui.onboarding

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.ui.vpn.VpnPermissionActivityDelegate
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.displayText
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnPermissionDelegate
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import me.proton.core.network.domain.ApiResult
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext val app: Context,
    private val serverManager: ServerManager,
    private val serverListUpdater: ServerListUpdater,
    private val vpnConnectionManager: VpnConnectionManager,
    private val vpnStateMonitor: VpnStateMonitor
) : ViewModel() {

    fun init() {
        Storage.saveString(OnboardingPreferences.ONBOARDING_USER_ID, null)
    }

    private val freeServers = viewModelScope.async(start = CoroutineStart.LAZY) {
        serverManager.getVpnCountries().flatMap { country ->
            country.serverList.filter { it.isFreeServer }
        }
    }

    suspend fun countriesCount() =
        freeServers.await().map { it.exitCountry }.distinct().count().takeIf { it != 0 } ?: COUNTRIES_COUNT
    suspend fun serversCount() = freeServers.await().count().takeIf { it != 0 } ?: SERVERS_COUNT

    data class Error(val html: String?, @StringRes val res: Int = R.string.something_went_wrong)

    suspend fun connect(activity: OnboardingActivity): Error? {
        val delegate = VpnPermissionActivityDelegate(activity)
        val intent = vpnConnectionManager.prepare(delegate.getContext())
        return if (delegate.suspendForPermissions(intent))
            connectInternal(activity)
        else
            Error(null, 0)
    }

    private suspend fun connectInternal(vpnPermissionDelegate: VpnPermissionDelegate): Error? {
        if (serverManager.isOutdated) {
            val result = serverListUpdater.updateServerList()
            if (result is ApiResult.Error)
                return Error(result.displayText())
        }
        val profile = serverManager.defaultAvailableConnection
        vpnConnectionManager.connect(vpnPermissionDelegate, profile, "onboarding")
        val state = withTimeoutOrNull(VPN_CONNECTION_WAIT_MS) {
            vpnStateMonitor.status.map { it.state }.first { it == VpnState.Connected || it is VpnState.Error }
        }
        if (state == VpnState.Connected)
            return null

        vpnConnectionManager.disconnect("onboarding connection failed")
        return if (state is VpnState.Error)
            Error(state.type.mapToErrorMessage(app, state.description))
        else
            Error(null, R.string.something_went_wrong)
    }

    companion object {
        private const val COUNTRIES_COUNT = 3
        private const val SERVERS_COUNT = 23
        private val VPN_CONNECTION_WAIT_MS = TimeUnit.SECONDS.toMillis(15)
    }
}
