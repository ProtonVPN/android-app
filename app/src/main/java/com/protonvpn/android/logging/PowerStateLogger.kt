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

package com.protonvpn.android.logging

import android.content.Context
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import com.protonvpn.android.utils.AndroidUtils.registerBroadcastReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerStateLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val powerManager: dagger.Lazy<PowerManager>,
    private val batteryManager: dagger.Lazy<BatteryManager?>
) {
    init {
        val filter = IntentFilter().apply {
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(BatteryManager.ACTION_CHARGING)
            addAction(BatteryManager.ACTION_DISCHARGING)
        }
        // Note: the actions from BatteryManager can be sent many seconds after attaching or detaching the power cable.
        context.registerBroadcastReceiver(filter) {
            ProtonLogger.log(OsPowerChanged, getStatusString())
        }
    }

    fun getStatusString(): String {
        val batteryManager = batteryManager.get()
        val powerManager = powerManager.get()
        val packageName = context.packageName
        val batteryStatus = batteryManager?.isCharging ?: "unknown"
        return "charging: $batteryStatus, device idle mode: ${powerManager.isDeviceIdleMode}, " +
            "power save mode: ${powerManager.isPowerSaveMode}, " +
            "ignores optimizations: ${powerManager.isIgnoringBatteryOptimizations(packageName)}"
    }
}
