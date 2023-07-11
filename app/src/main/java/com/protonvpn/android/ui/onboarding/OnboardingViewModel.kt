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
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.components.suspendForPermissions
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.ui.vpn.VpnUiActivityDelegate
import com.protonvpn.android.utils.SentryIntegration
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.displayText
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnPermissionDelegate
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.proton.core.network.domain.ApiResult
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext val app: Context,
    private val mainScope: CoroutineScope,
    private val appConfig: AppConfig,
    private val userLocalSettingsManager: CurrentUserLocalSettingsManager,
    private val serverManager: ServerManager,
    private val serverListUpdater: ServerListUpdater,
    private val vpnPermissionDelegate: VpnPermissionDelegate,
    private val vpnConnectionManager: VpnConnectionManager,
    private val vpnStatusProviderUI: VpnStatusProviderUI
) : ViewModel() {

    private val freeServers = viewModelScope.async(start = CoroutineStart.LAZY) {
        serverManager.getVpnCountries().flatMap { country ->
            country.serverList.filter { it.isFreeServer }
        }
    }

    val showTelemetryPrompt get() = appConfig.getFeatureFlags().telemetry

    var telemetryEnabledSwitch: Boolean = true
    var crashReportingSwitch: Boolean = true

    suspend fun countriesCount() =
        freeServers.await().map { it.exitCountry }.distinct().count().takeIf { it != 0 } ?: COUNTRIES_COUNT
    suspend fun serversCount() = freeServers.await().count().takeIf { it != 0 } ?: SERVERS_COUNT

    data class Error(val html: String?, @StringRes val res: Int = R.string.something_went_wrong)

    fun applyTelemetryChoice() {
        mainScope.launch {
            SentryIntegration.setEnabled(crashReportingSwitch)
            userLocalSettingsManager.updateTelemetry(telemetryEnabledSwitch)
        }
    }

    suspend fun connect(activity: ComponentActivity, delegate: VpnUiActivityDelegate): Error? {
        val intent = vpnPermissionDelegate.prepareVpnPermission()
        val profile = serverManager.defaultAvailableConnection
        return if (activity.suspendForPermissions(intent))
            connectInternal(delegate, profile)
        else {
            delegate.onPermissionDenied(profile)
            Error(null, 0)
        }
    }

    private suspend fun connectInternal(vpnUiDelegate: VpnUiDelegate, profile: Profile): Error? {
        if (serverManager.isOutdated) {
            val result = serverListUpdater.updateServerList()
            if (result is ApiResult.Error)
                return Error(result.displayText())
        }
        vpnConnectionManager.connect(vpnUiDelegate, profile, ConnectTrigger.Onboarding("onboarding"))
        val state = withTimeoutOrNull(VPN_CONNECTION_WAIT_MS) {
            vpnStatusProviderUI.status.map { it.state }.first { it == VpnState.Connected || it is VpnState.Error }
        }
        if (state == VpnState.Connected)
            return null

        vpnConnectionManager.disconnect(DisconnectTrigger.Onboarding("onboarding connection failed"))
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
