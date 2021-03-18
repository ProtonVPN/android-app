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
package com.protonvpn.android.vpn.ikev2

import android.os.Handler
import android.os.Looper
import java.util.Date
import java.util.Timer
import kotlin.concurrent.schedule

class TimerScheduler {

    private val timer = Timer()
    private val handler = Handler(Looper.getMainLooper())
    private var terminated = false

    fun terminate() {
        terminated = true
        timer.cancel()
        handler.removeCallbacksAndMessages(null)
    }

    fun scheduleRTC(timeMs: Long, action: Runnable) {
        timer.schedule(Date(timeMs)) {
            if (!terminated)
                handler.post(action)
        }
    }
}
