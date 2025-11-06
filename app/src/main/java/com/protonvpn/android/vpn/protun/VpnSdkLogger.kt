/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.vpn.protun

import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import dagger.Reusable
import me.proton.vpn.sdk.api.Logger
import uniffi.protun.LogLevel
import javax.inject.Inject

@Reusable
class VpnSdkLogger @Inject constructor(): Logger {
    override fun log(level: LogLevel, message: String) {
        when (level) {
            LogLevel.TRACE -> ProtonLogger.logCustom(com.protonvpn.android.logging.LogLevel.TRACE, LogCategory.PROTOCOL, message)
            LogLevel.DEBUG -> ProtonLogger.logCustom(com.protonvpn.android.logging.LogLevel.DEBUG, LogCategory.PROTOCOL, message)
            LogLevel.INFO -> ProtonLogger.logCustom(com.protonvpn.android.logging.LogLevel.INFO, LogCategory.PROTOCOL, message)
            LogLevel.WARN -> ProtonLogger.logCustom(com.protonvpn.android.logging.LogLevel.WARN, LogCategory.PROTOCOL, message)
            LogLevel.ERROR -> ProtonLogger.logCustom(com.protonvpn.android.logging.LogLevel.ERROR, LogCategory.PROTOCOL, message)
        }
    }
}