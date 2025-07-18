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
package com.protonvpn.android.vpn.openvpn

import android.content.Intent
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.vpn.CertificateData
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.ConnectionParamsOpenVpn
import com.protonvpn.android.models.vpn.usecase.ComputeAllowedIPs
import com.protonvpn.android.notifications.NotificationHelper
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnectionSync
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.ConnectionParamsUuidServiceHelper
import com.protonvpn.android.vpn.CurrentVpnServiceProvider
import com.protonvpn.android.vpn.VpnConnectionManager
import dagger.hilt.android.AndroidEntryPoint
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.VpnStatus.StateListener
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class OpenVPNWrapperService : OpenVPNService(), StateListener {

    @Inject lateinit var settingsForConnection: SettingsForConnectionSync
    @Inject lateinit var vpnConnectionManager: VpnConnectionManager
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var certificateRepository: CertificateRepository
    @Inject lateinit var currentUser: CurrentUser
    @Inject lateinit var currentVpnServiceProvider: CurrentVpnServiceProvider
    @Inject lateinit var computeAllowedIPs: ComputeAllowedIPs

    private val connectionParamsUuid = ConnectionParamsUuidServiceHelper()

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.initNotificationChannel(applicationContext)
        currentVpnServiceProvider.onVpnServiceCreated(OpenVpnBackend::class, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(Constants.NOTIFICATION_ID, notificationHelper.buildNotification())
        connectionParamsUuid.onStartCommand(intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun getProfile(): VpnProfile? {
        val connectionParams = Storage.load(ConnectionParams::class.java, ConnectionParamsOpenVpn::class.java)
        return connectionParams?.let {
            val userSessionId = currentUser.vpnUserBlocking()?.sessionId
            val certificateResult = runBlocking {
                // In most cases this should access preferences that are already in memory and should be fast.
                userSessionId?.let { certificateRepository.getCertificateWithoutRefresh(it) }
            }
            val certificate = (certificateResult as? CertificateRepository.CertificateResult.Success)?.let {
                CertificateData(it.privateKeyPem, it.certificate)
            }
            val settings = settingsForConnection.getForSync(it.connectIntent)
            connectionParams.openVpnProfile(packageName, settings, certificate, computeAllowedIPs)
        }
    }

    override fun onProcessRestore(): Boolean {
        val connectIntent = ConnectionParams.readIntentFromStore() ?: return false
        return vpnConnectionManager.onRestoreProcess(connectIntent, "service restart")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        ProtonLogger.logCustom(LogCategory.APP, "OpenVPNWrapperService: onTrimMemory level $level")
    }

    override fun onDestroy() {
        vpnConnectionManager.onVpnServiceDestroyed(connectionParamsUuid.last)
        currentVpnServiceProvider.onVpnServiceDestroyed(OpenVpnBackend::class)
        super.onDestroy()
    }
}
