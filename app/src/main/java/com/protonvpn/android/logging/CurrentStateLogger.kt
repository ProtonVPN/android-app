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
    private val vpnStateMonitor: VpnStateMonitor,
    private val connectivityMonitor: ConnectivityMonitor,
    private val currentUser: CurrentUser,
    private val effectiveUserSettings: EffectiveCurrentUserSettings,
    private val powerStateLogger: PowerStateLogger,
    private val settingChangesLogger: SettingChangesLogger
) {
    fun logCurrentState() {
        mainScope.launch(mainScope.coroutineContext) {
            val vpnUser = currentUser.vpnUser()
            val settings = effectiveUserSettings.effectiveSettings.first()
            val settingsText = settingChangesLogger.getCurrentSettingsForLog(settings)
            ProtonLogger.log(UserPlanCurrent, vpnUser?.toLog() ?: "no user logged in")
            ProtonLogger.log(NetworkCurrent, connectivityMonitor.getCurrentStateForLog())
            ProtonLogger.log(ConnCurrentState, vpnStateMonitor.state.toString())
            ProtonLogger.log(OsPowerCurrent, powerStateLogger.getStatusString())
            ProtonLogger.log(SettingsCurrent, "\n$settingsText")
            ProtonLogger.logCustom(LogCategory.APP, timezoneInfo())
            ProtonLogger.logCustom(LogCategory.APP, "Sentry ID: ${SentryIntegration.getInstallationId()}")
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
