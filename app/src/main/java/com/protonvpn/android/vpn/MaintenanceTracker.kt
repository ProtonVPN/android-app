package com.protonvpn.android.vpn

import android.content.Context
import android.os.SystemClock
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.utils.ReschedulableTask
import com.protonvpn.android.utils.getValue
import com.protonvpn.android.utils.jitterMs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaintenanceTracker @Inject constructor(
    val scope: CoroutineScope,
    @ApplicationContext val appContext: Context,
    lazyAppConfig: dagger.Lazy<AppConfig>,
    private val vpnStateMonitor: VpnStateMonitor,
    private val vpnConnectionErrorHandler: dagger.Lazy<VpnConnectionErrorHandler>,
) {
    private val appConfig by lazyAppConfig

    init {
        scope.launch {
            vpnStateMonitor.status.collect { status ->
                if (status.state == VpnState.Connected) {
                    task.scheduleIn(getScheduleDelay())
                } else if (status.state == VpnState.Disabled) {
                    task.cancelSchedule()
                }
            }
        }
    }

    private suspend fun getScheduleDelay(): Long =
        jitterMs(TimeUnit.MINUTES.toMillis(appConfig.getMaintenanceTrackerDelay()))

    private fun now() = SystemClock.elapsedRealtime()

    private val task = ReschedulableTask(scope, ::now) {
        if (vpnStateMonitor.isConnected && appConfig.isMaintenanceTrackerEnabled()) {
            vpnConnectionErrorHandler.get().maintenanceCheck()
            scheduleIn(getScheduleDelay())
        }
    }
}
