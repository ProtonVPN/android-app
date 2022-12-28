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

package com.protonvpn.android.ui.planupgrade

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.components.suspendForPermissions
import com.protonvpn.android.ui.vpn.VpnUiActivityDelegate
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.displayText
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnPermissionDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.core.network.domain.ApiResult
import javax.inject.Inject

@HiltViewModel
class CongratsPlanViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    private val userPlanManager: UserPlanManager,
    private val serverManager: ServerManager,
    private val vpnPermissionDelegate: VpnPermissionDelegate,
    private val vpnConnectionManager: VpnConnectionManager
) : ViewModel() {

    val state = MutableStateFlow<State>(State.Processing)

    sealed class State {
        object Processing : State()
        object Success : State()
        class Error(val message: String?) : State()
    }

    fun refreshPlan() = viewModelScope.launch {
        val refreshResult = withContext(mainScope.coroutineContext) { userPlanManager.refreshVpnInfo() }
        state.value = if (refreshResult is ApiResult.Error)
            State.Error(refreshResult.displayText())
        else
            State.Success
    }

    suspend fun connectPlus(activity: ComponentActivity, vpnUiDelegate: VpnUiActivityDelegate): Boolean {
        val profile = serverManager.defaultFallbackConnection
        if (activity.suspendForPermissions(vpnPermissionDelegate.prepareVpnPermission())) {
            vpnConnectionManager.connect(vpnUiDelegate, profile, ConnectTrigger.Onboarding("onboarding plus"))
            return true
        } else {
            vpnUiDelegate.onPermissionDenied(profile)
        }
        return false
    }

    val serverCount get() = serverManager.allServerCount
}
