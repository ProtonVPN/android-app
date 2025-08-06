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

package com.protonvpn.android.redesign.settings.ui

import androidx.lifecycle.ViewModel
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.data.DebugApiPrefs
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.ui.home.ServerListUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class DebugToolsViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    private val guestHole: GuestHole,
    private val currentUser: CurrentUser,
    private val appConfig: AppConfig,
    private val serverListUpdater: ServerListUpdater,
    private val apiNotificationManager: ApiNotificationManager,
    debugApiPrefsNullable: DebugApiPrefs?,
): ViewModel() {

    private val debugApiPrefs = requireNotNull(debugApiPrefsNullable)

    val state = combine(
        debugApiPrefs.netzoneFlow,
        debugApiPrefs.countryFlow,
    ) { netzone, country ->
        DebugToolsState(
            netzone = netzone,
            country = country,
        )
    }

    fun connectGuestHole() {
        mainScope.launch {
            guestHole.onAlternativesUnblock {
                delay(10.seconds)
            }
        }
    }

    fun refreshConfig() {
        mainScope.launch {
            appConfig.forceUpdate(currentUser.vpnUser()?.userId)
            serverListUpdater.updateServerList(forceFreshUpdate = true)
            apiNotificationManager.forceUpdate()
        }
    }

    fun setNetzone(netzone: String) {
        debugApiPrefs.netzone = netzone.takeIf { it.isNotBlank() }
    }

    fun setCountry(country: String) {
        debugApiPrefs.country = country.takeIf { it.isNotBlank() }
    }
}

data class DebugToolsState(
    val netzone: String?,
    val country: String?,
)
