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

package com.protonvpn.android.vpn

import com.protonvpn.android.di.AppComponent
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.ProtonLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.proton.core.util.kotlin.DispatcherProvider
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnLogCapture(appComponent: AppComponent, val monoClock: () -> Long) {

    @Inject lateinit var mainScope: CoroutineScope
    @Inject lateinit var dispatcherProvider: DispatcherProvider

    init {
        appComponent.inject(this)
    }

    fun startCapture() {
        mainScope.launch(dispatcherProvider.Io) {
            captureCharonWireguardLogs()
        }
    }

    private suspend fun captureCharonWireguardLogs() {
        do {
            val start = monoClock()
            try {
                val process = Runtime.getRuntime().exec(
                    "logcat -s WireGuard/GoBackend/${Constants.WIREGUARD_TUNNEL_NAME}:* charon:* -T 1 -v raw"
                )
                BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                    lines.forEach {
                        ProtonLogger.log(it)
                    }
                }
                ProtonLogger.log("Logcat streaming ended")
            } catch (e: IOException) {
                ProtonLogger.log("Log capturing from logcat failed: ${e.message}")
            }
            // Avoid busy loop if capture fails early
            if (monoClock() - start < TimeUnit.MINUTES.toMillis(5))
                delay(TimeUnit.MINUTES.toMillis(1))
            ProtonLogger.log("Restarting logcat capture")
        } while (true)
    }
}
