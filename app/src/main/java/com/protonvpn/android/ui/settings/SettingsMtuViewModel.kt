/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.ui.settings

import androidx.lifecycle.viewModelScope
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.logUiSettingChange
import com.protonvpn.android.logging.Setting
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.ui.SaveableSettingsViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsMtuViewModel @Inject constructor(
    private val userSettingsManager: CurrentUserLocalSettingsManager
) : SaveableSettingsViewModel() {

    private var mtu = "0"

    val eventInvalidMtu = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            mtu = valueInSettings().toString()
        }
    }

    suspend fun getMtu() = valueInSettings().toString()

    fun onMtuTextChanged(text: String) {
        mtu = text
    }

    override fun saveChanges() {
        ProtonLogger.logUiSettingChange(Setting.DEFAULT_MTU, "settings")
        viewModelScope.launch {
            // At this point the MTU value must be valid.
            userSettingsManager.updateMtuSize(validMtu()!!)
        }
    }

    override suspend fun hasUnsavedChanges(): Boolean =
        mtu != valueInSettings().toString()

    override fun validate(): Boolean {
        val isValid = validMtu() != null
        if (!isValid)
            eventInvalidMtu.tryEmit(Unit)
        return isValid
    }

    private fun validMtu(): Int? =
        mtu.toIntOrNull()?.takeIf { number -> number in MTU_MIN..MTU_MAX }

    private suspend fun valueInSettings(): Int = userSettingsManager.rawCurrentUserSettingsFlow.first().mtuSize

    companion object {
        const val MTU_MAX = 1500
        const val MTU_MIN = 1280
    }
}
