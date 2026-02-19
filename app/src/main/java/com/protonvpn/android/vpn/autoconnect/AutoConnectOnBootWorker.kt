/*
 * Copyright (c) 2026 Proton AG
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

package com.protonvpn.android.vpn.autoconnect

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.VpnService
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.vpn.usecase.GetSmartProtocols
import com.protonvpn.android.notifications.NotificationHelper
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.tv.vpn.createIntentForDefaultProfile
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoConnectOnBootWorker  @AssistedInject constructor(
    @Assisted val context: Context,
    @Assisted params: WorkerParameters,
    private val effectiveUserSettings: EffectiveCurrentUserSettings,
    private val vpnUiStatus: VpnStatusProviderUI,
    private val notificationHelper: dagger.Lazy<NotificationHelper>,
    private val vpnConnectionManager: dagger.Lazy<VpnConnectionManager>,
    private val getSmartProtocols: dagger.Lazy<GetSmartProtocols>,
    private val tvProfileManager: dagger.Lazy<ProfileManager>,
    private val serverManager: dagger.Lazy<ServerManager>,
    private val currentUser: dagger.Lazy<CurrentUser>,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ProtonLogger.logCustom(LogCategory.CONN, "AutoConnectOnBootWorker start")
        val settings = effectiveUserSettings.effectiveSettings.first()
        if (settings.tvAutoConnectOnBoot && !vpnUiStatus.isEstablishingOrConnected) {
            val haveVpnPermission = VpnService.prepare(context) == null
            if (!haveVpnPermission) {
                ProtonLogger.logCustom(LogCategory.CONN, "AutoConnectOnBootWorker: no VPN permission, aborting")
            } else {
                ProtonLogger.logCustom(LogCategory.CONN, "AutoConnectOnBootWorker connecting...")

                // Promote to foreground ASAP - it gives a higher chance for the work to complete before
                // being killed by the system, especially on firestick.
                setForeground(createForegroundInfo())

                connect(settings)
            }
        }
        return Result.success()
    }

    private fun createForegroundInfo() = ForegroundInfo(
        Constants.NOTIFICATION_ID,
        notificationHelper.get().buildNotification(),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
    )

    private suspend fun connect(settings: LocalUserSettings) {
        val profile = tvProfileManager.get().getDefaultOrFastest()
        serverManager.get().ensureLoaded()
        val intent = createIntentForDefaultProfile(
            serverManager.get(),
            currentUser.get(),
            settings.protocol,
            getSmartProtocols.get().invoke(),
            profile
        )
        vpnConnectionManager.get().connectInBackground(
            intent,
            ConnectTrigger.Auto("Auto-connect on boot")
        )
    }

    companion object {
        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<AutoConnectOnBootWorker>()
                // Without additional delay work scheduled by boot receiver will not complete on
                // firestick and get killed by the system.
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()

            workManager
                .enqueueUniqueWork(
                    "vpn_boot_startup",
                    ExistingWorkPolicy.KEEP,
                    request
                )
        }
    }
}