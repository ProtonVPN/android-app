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
import android.content.pm.PackageManager
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
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.MessageDigest
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
    @ApplicationContext private val context: Context,
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
            ProtonLogger.logCustom(LogCategory.APP, "APK signatures hashes: ${signatureHashes()}")
        }
    }

    private fun timezoneInfo(): String {
        val timezone = TimeZone.getDefault()
        val timezoneCanonicalId = TimeZone.getCanonicalID(timezone.id)
        val timezoneCurrentOffsetMinutes =
            TimeUnit.MILLISECONDS.toMinutes(timezone.getOffset(System.currentTimeMillis()).toLong())
        return "Timezone: $timezoneCanonicalId $timezoneCurrentOffsetMinutes"
    }

    private fun signatureHashes(): List<String> =
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            .signatures
            ?.map { signature -> signatureName(sha1(signature.toByteArray())) } ?: emptyList()
}

// To be used only for simple hashing in logging
private fun sha1(bytes: ByteArray) =
    // nosemgrep
    MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

private fun signatureName(hash: String) =
    when (hash) {
        "d8e1ee3ff3a7f6ec46883c898032fe03c23eec20" -> "proton"
        "100f4dec8d194c9985dcd22a7ebd39c91ac9e1ef" -> "fdroid"
        "986388096ce3a46dbe3319b805cf19486c5d359e" -> "amazon"
        "0838993e3ea9c1e3429de8b72e11fb29b2a89bb9" -> "debug"
        else -> "unknown (sha1=$hash)"
    }