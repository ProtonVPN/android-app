/*
 * Copyright (c) 2021 Proton AG
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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.displayText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.proton.core.network.domain.ApiResult
import javax.inject.Inject

private const val FALLBACK_STORAGE_BYTES = 536_870_912_000L

@HiltViewModel
class CongratsPlanViewModel @Inject constructor(
    private val userPlanManager: UserPlanManager,
    private val serverListUpdaterPrefs: ServerListUpdaterPrefs,
    private val currentUser: CurrentUser,
) : ViewModel() {

    val state = MutableStateFlow<State>(State.Processing)

    val countriesCount get() = serverListUpdaterPrefs.vpnCountryCount

    sealed class State {
        object Processing : State()
        object Success : State()
        class Error(val message: String?) : State()
    }

    suspend fun getStorageGBs() =
        // The user should never be null here.
        (currentUser.user()?.maxSpace ?: FALLBACK_STORAGE_BYTES) / 1024 / 1024 / 1024

    fun refreshPlan() = viewModelScope.launch {
        val refreshResult = userPlanManager.refreshVpnInfo()
        state.value = if (refreshResult is ApiResult.Error)
            State.Error(refreshResult.displayText())
        else
            State.Success
    }
}
