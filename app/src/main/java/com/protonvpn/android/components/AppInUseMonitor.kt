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

package com.protonvpn.android.components

import android.app.Activity
import android.app.Application
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.DefaultActivityLifecycleCallbacks
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInUseMonitor @Inject constructor(
    mainScope: CoroutineScope,
    private val app: Application,
    private val vpnStateMonitor: VpnStateMonitor
) {

    private val _isInUseFlow = MutableStateFlow(false)
    val isInUseFlow: StateFlow<Boolean> get() = _isInUseFlow
    val isInUse: Boolean get() = isInUseFlow.value

    init {
        // App is in use when:
        // - the UI is shown,
        val lifecycleCallbacks = object : DefaultActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                onAppInUse()
                app.unregisterActivityLifecycleCallbacks(this)
            }
        }
        app.registerActivityLifecycleCallbacks(lifecycleCallbacks)

        // - a VPN connection is used.
        mainScope.launch {
            vpnStateMonitor.status.first { it.state != VpnState.Disabled }
            onAppInUse()
        }
    }

    private fun onAppInUse() {
        ProtonLogger.logCustom(LogCategory.APP, "app is in use")
        _isInUseFlow.value = true
    }
}
