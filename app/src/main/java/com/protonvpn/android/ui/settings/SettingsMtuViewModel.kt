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

import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.logUiSettingChange
import com.protonvpn.android.models.config.Setting
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.ui.SaveableSettingsViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject

@HiltViewModel
class SettingsMtuViewModel @Inject constructor(
    private val userData: UserData
) : SaveableSettingsViewModel() {

    var mtu = userData.mtuSize.toString()
        private set

    val eventInvalidMtu = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun onMtuTextChanged(text: String) {
        mtu = text
    }

    override fun saveChanges() {
        ProtonLogger.logUiSettingChange(Setting.DEFAULT_MTU, "settings")
        // At this point the MTU value must be valid.
        userData.mtuSize = validMtu()!!
    }

    override fun hasUnsavedChanges(): Boolean =
        mtu != userData.mtuSize.toString()

    override fun validate(): Boolean {
        val isValid = validMtu() != null
        if (!isValid)
            eventInvalidMtu.tryEmit(Unit)
        return isValid
    }

    private fun validMtu(): Int? =
        mtu.toIntOrNull()?.takeIf { number -> number in MTU_MIN..MTU_MAX }

    companion object {
        const val MTU_MAX = 1500
        const val MTU_MIN = 1280
    }
}
