package com.protonvpn.android.vpn.wireguard
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

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import com.protonvpn.android.utils.DebugUtils
import com.wireguard.android.backend.GoBackend

class WireguardContextWrapper(context: Context) : ContextWrapper(context) {
    override fun startService(serviceIntent: Intent?): ComponentName? {
        DebugUtils.debugAssert {
            serviceIntent?.component == ComponentName(applicationContext, GoBackend.VpnService::class.java)
        }
        val ourIntent = Intent(this, WireguardWrapperService::class.java)
        return if (Build.VERSION.SDK_INT >= 26) {
            baseContext.startForegroundService(ourIntent)
        } else {
            baseContext.startService(ourIntent)
        }
    }
}
