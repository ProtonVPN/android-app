/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.ui.login

import androidx.lifecycle.ViewModel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.logUiSettingChange
import com.protonvpn.android.models.config.Setting
import com.protonvpn.android.models.config.UserData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TroubleshootViewModel @Inject constructor(val userData: UserData) : ViewModel() {

    var dnsOverHttpsEnabled: Boolean
        get() = userData.apiUseDoH
        set(value) {
            ProtonLogger.logUiSettingChange(Setting.API_DOH, "troubleshooting screen")
            userData.apiUseDoH = value
        }
}
