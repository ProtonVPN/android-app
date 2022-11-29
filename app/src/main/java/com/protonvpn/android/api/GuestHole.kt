/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.api

import androidx.activity.ComponentActivity
import androidx.annotation.WorkerThread
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.components.suspendForPermissions
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.notifications.NotificationHelper
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.vpn.VpnUiActivityDelegate
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.FileUtils
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnPermissionDelegate
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnUiDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import me.proton.core.network.domain.serverconnection.DohAlternativesListener
import me.proton.core.util.kotlin.DispatcherProvider
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val GUEST_HOLE_PROTOCOL = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS)

@Singleton
class GuestHole @Inject constructor(
    private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val serverManager: dagger.Lazy<ServerManager>,
    private val vpnMonitor: VpnStateMonitor,
    private val vpnPermissionDelegate: VpnPermissionDelegate,
    private val vpnConnectionManager: dagger.Lazy<VpnConnectionManager>,
    private val notificationHelper: NotificationHelper,
    private val foregroundActivityTracker: ForegroundActivityTracker,
    private val appFeaturesPrefs: AppFeaturesPrefs
) : DohAlternativesListener {

    private var waitingForUnblock = false
    var job: Job? = null

    // Guest hole be kept open until this is empty
    class GuestHoleLocks {
        private val locks = mutableSetOf<String>()
        fun clear() = locks.clear()
        fun locked() = locks.isNotEmpty()
        fun acquire(id: String) = locks.add(id)
        fun release(id: String): Boolean {
            locks.remove(id)
            return locks.isEmpty()
        }
    }
    private val guestHoleLocks = GuestHoleLocks()

    val isGuestHoleActive: Boolean get() = vpnMonitor.connectionProfile?.isGuestHoleProfile == true

    fun acquireNeedGuestHole(id: String) {
        guestHoleLocks.acquire(id)
    }

    suspend fun releaseNeedGuestHole(id: String) {
        if (guestHoleLocks.release(id) && !waitingForUnblock)
            closeGuestHole()
    }

    private fun getGuestHoleServers(): List<Server> {
        val holes = if (serverManager.get().isDownloadedAtLeastOnce) {
            // Get random servers from ServerManager if it was downloaded instead of initialization each time
            serverManager.get().getServersForGuestHole(GUEST_HOLE_SERVER_COUNT, GUEST_HOLE_PROTOCOL)
        } else {
            val servers = FileUtils.getObjectFromAssets(ListSerializer(Server.serializer()), GUEST_HOLE_SERVERS_ASSET)
            servers.shuffled().take(GUEST_HOLE_SERVER_COUNT).apply {
                serverManager.get().setGuestHoleServers(this)
            }
        }

        return appFeaturesPrefs.lastSuccessfulGuestHoleServerId?.let { id ->
            // Start with server that was successful last time
            listOfNotNull(serverManager.get().getServerById(id)) + holes.filter { it.serverId != id }
        } ?: holes
    }

    private suspend fun <T> executeConnected(
        vpnUiDelegate: VpnUiDelegate,
        server: Server,
        block: suspend () -> T
    ): Boolean {
        var connected = vpnMonitor.isConnected
        if (!connected) {
            val vpnStatus = vpnMonitor.status
            connected = withTimeoutOrNull(GUEST_HOLE_SERVER_TIMEOUT) {
                val profile = Profile.getTempProfile(server)
                    .apply {
                        setProtocol(GUEST_HOLE_PROTOCOL)
                        isGuestHoleProfile = true
                    }
                vpnConnectionManager.get().connect(vpnUiDelegate, profile, Constants.REASON_GUEST_HOLE)
                vpnStatus
                    .map { it.state }
                    .first { it is VpnState.Connected || it is VpnState.Error }
            } == VpnState.Connected
        }
        if (connected) {
            block()
        }
        return connected
    }

    @WorkerThread
    override suspend fun onAlternativesUnblock(alternativesBlockCall: suspend () -> Unit) {
        withContext(dispatcherProvider.Main) {
            unblock(alternativesBlockCall)
        }
    }

    override suspend fun onProxiesFailed() {
        withContext(dispatcherProvider.Main) {
            val keepGuestHole = guestHoleLocks.locked()
            logMessage("alternatives failed, keepGuestHole=$keepGuestHole")
            if (keepGuestHole)
                unblock(null)
        }
    }

    private suspend fun unblock(backendCall: (suspend () -> Unit)?) {
        if (!vpnMonitor.isDisabled) {
            logMessage("Ignoring Guest-hole on VPN connection")
            return
        }
        logMessage("Guesthole for DOH")
        coroutineScope {
            job = launch {
                // Do not execute guesthole for calls running in background, due to inability to call permission intent
                val currentActivity =
                    foregroundActivityTracker.foregroundActivity as? ComponentActivity ?: return@launch
                val delegate = GuestHoleVpnUiDelegate(currentActivity)
                val intent = vpnPermissionDelegate.prepareVpnPermission()

                // Ask for permissions and if granted execute original method and return it back to core
                if (currentActivity.suspendForPermissions(intent)) {
                    withTimeoutOrNull(GUEST_HOLE_ATTEMPT_TIMEOUT) {
                        unblockWithPermission(delegate, backendCall)
                    }
                } else {
                    guestHoleLocks.clear()
                    logMessage("Missing permissions")
                }
            }
            job?.join()
        }
    }

    private suspend fun unblockWithPermission(
        delegate: VpnUiActivityDelegate,
        backendCall: (suspend () -> Unit)?
    ) {
        waitingForUnblock = backendCall != null
        val userActionJob = scope.launch {
            merge(vpnMonitor.onDisconnectedByUser, vpnMonitor.onDisconnectedByReconnection).collect {
                job?.let {
                    logMessage("Guesthole canceled due to user action")
                    it.cancelAndJoin()
                }
            }
        }
        try {
            notificationHelper.showInformationNotification(
                R.string.guestHoleNotificationContent,
                notificationId = Constants.NOTIFICATION_GUESTHOLE_ID
            )
            getGuestHoleServers().any { server ->
                executeConnected(delegate, server) {
                    // Add slight delay before retrying original call to avoid network timeout right after connection
                    delay(500)
                    appFeaturesPrefs.lastSuccessfulGuestHoleServerId = server.serverId
                    backendCall?.invoke()
                    logMessage("Successful unblock")
                }
            }
        } finally {
            // This needs to run even when coroutine was cancelled
            withContext(NonCancellable) {
                waitingForUnblock = false
                if (!(isGuestHoleActive && vpnMonitor.isConnected)) {
                    // If Guest Hole failed don't start it for next call
                    guestHoleLocks.clear()
                }
                if (!guestHoleLocks.locked())
                    closeGuestHole()
                userActionJob.cancel()
            }
        }
    }

    private suspend fun closeGuestHole() {
        if (isGuestHoleActive) {
            logMessage("Disconnecting")
            vpnConnectionManager.get().disconnectSync("guest hole call completed")
        }
    }

    private fun logMessage(message: String) {
        ProtonLogger.logCustom(LogCategory.CONN_GUEST_HOLE, message)
    }

    suspend fun <T> runWithGuestHoleFallback(block: suspend () -> T): T {
        val id = UUID.randomUUID().toString()
        acquireNeedGuestHole(id)
        try {
            return block()
        } finally {
            releaseNeedGuestHole(id)
        }
    }

    companion object {

        private const val GUEST_HOLE_SERVER_COUNT = 5
        private const val GUEST_HOLE_SERVER_TIMEOUT = 10_000L
        private const val GUEST_HOLE_ATTEMPT_TIMEOUT = 50_000L
        private const val GUEST_HOLE_SERVERS_ASSET = "GuestHoleServers.json"
    }

    class GuestHoleVpnUiDelegate(activity: ComponentActivity) : VpnUiActivityDelegate(activity) {
        override fun onPermissionDenied(profile: Profile) {}
        override fun showSecureCoreUpgradeDialog() {}
        override fun showPlusUpgradeDialog() {}
        override fun showMaintenanceDialog() {}
        override fun shouldSkipAccessRestrictions() = true
        override fun onProtocolNotSupported() {}
    }
}
