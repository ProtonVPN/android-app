/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.android.vpn

import com.protonvpn.android.auth.data.hasAccessToServer
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.ServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import me.proton.core.util.kotlin.DispatcherProvider
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("UseDataClass")
@Singleton
class UpdateSettingsOnVpnUserChange @Inject constructor(
    mainScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val currentUser: CurrentUser,
    private val serverManager: ServerManager,
    private val userData: UserData
) {
    init {
        mainScope.launch {
            currentUser.vpnUserFlow.flowOn(dispatcherProvider.Main).collect { vpnUser ->
                if (vpnUser != null) {
                    if (!vpnUser.isUserBasicOrAbove) {
                        userData.setNetShieldProtocol(null)
                        userData.safeModeEnabled = true
                    }
                    if (!vpnUser.isUserPlusOrAbove) {
                        userData.secureCoreEnabled = false
                    }
                    if (!vpnUser.hasAccessToServer(serverManager.defaultConnection.server)) {
                        userData.defaultConnection = null
                    }
                }
            }
        }
    }
}
