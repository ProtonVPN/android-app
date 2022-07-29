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

import android.net.VpnService
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
@SuppressWarnings("UseDataClass")
class CurrentVpnServiceProvider @Inject constructor() {
    private val activeVpnServices = HashMap<KClass<out VpnBackend>, VpnService>()
    var activeVpnBackend: KClass<out VpnBackend>? = null

    fun onVpnServiceCreated(backendClass: KClass<out VpnBackend>, vpnService: VpnService) {
        activeVpnServices[backendClass] = vpnService
        Log.d("CurrentVpnService", "added service for $backendClass")
    }

    fun onVpnServiceDestroyed(backendClass: KClass<out VpnBackend>) {
        activeVpnServices.remove(backendClass)
        Log.d("CurrentVpnService", "removed service for $backendClass")
    }

    fun getCurrentVpnService(): VpnService? = activeVpnServices[activeVpnBackend]
}
