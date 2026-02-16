/*
 * Copyright (c) 2021. Proton AG
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
import android.icu.util.TimeZone
import android.os.Build
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.utils.SentryIntegration
import com.protonvpn.android.vpn.ConnectivityMonitor
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A bridge to use CurrentStateLogger with Hilt-injected dependencies in ProtonLogger.
 *
 * ProtonLogger is a global object and cannot have Hilt-injected dependencies (especially that
 * dependencies are recreated for each test). This acts as a bridge to request CurrentStateLogger
 * from Hilt on each call to logCurrentState().
 */
class CurrentStateLoggerGlobal(private val appContext: Context) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HiltHelper {
        fun currentStateLogger(): CurrentStateLogger
    }

    fun logCurrentState() {
        EntryPoints.get(appContext, HiltHelper::class.java).currentStateLogger().logCurrentState()
    }
}

@Singleton
class CurrentStateLogger @Inject constructor(
    private val mainScope: CoroutineScope,
    private val vpnStateMonitor: dagger.Lazy<VpnStateMonitor>,
    private val connectivityMonitor: dagger.Lazy<ConnectivityMonitor>,
    private val currentUser: dagger.Lazy<CurrentUser>,
    private val effectiveUserSettings: dagger.Lazy<EffectiveCurrentUserSettings>,
    private val powerStateLogger: dagger.Lazy<PowerStateLogger>,
    private val settingChangesLogger: dagger.Lazy<SettingChangesLogger>
) {
    fun logCurrentState(delayMs: Long = 0) {
        mainScope.launch(mainScope.coroutineContext) {
            delay(delayMs)
            val vpnUser = currentUser.get().vpnUser()
            val settings = effectiveUserSettings.get().effectiveSettings.first()
            val settingsText = settingChangesLogger.get().getCurrentSettingsForLog(settings)
            ProtonLogger.log(UserPlanCurrent, vpnUser?.toLog() ?: "no user logged in")
            ProtonLogger.log(NetworkCurrent, connectivityMonitor.get().getCurrentStateForLog())
            ProtonLogger.log(ConnCurrentState, vpnStateMonitor.get().state.toString())
            ProtonLogger.log(OsPowerCurrent, powerStateLogger.get().getStatusString())
            ProtonLogger.log(SettingsCurrent, "\n$settingsText")
            ProtonLogger.logCustom(LogCategory.APP, timezoneInfo())
            ProtonLogger.logCustom(LogCategory.APP, "Sentry ID: ${SentryIntegration.getInstallationId()}")
            ProtonLogger.logCustom(LogCategory.APP,
                "Device: ${Build.MANUFACTURER} ${Build.MODEL} ${Build.DISPLAY} (API ${Build.VERSION.SDK_INT})")
        }
    }

    private fun timezoneInfo(): String {
        val timezone = TimeZone.getDefault()
        val timezoneCanonicalId = TimeZone.getCanonicalID(timezone.id)
        val timezoneCurrentOffsetMinutes =
            TimeUnit.MILLISECONDS.toMinutes(timezone.getOffset(System.currentTimeMillis()).toLong())
        return "Timezone: $timezoneCanonicalId $timezoneCurrentOffsetMinutes"
    }
}
