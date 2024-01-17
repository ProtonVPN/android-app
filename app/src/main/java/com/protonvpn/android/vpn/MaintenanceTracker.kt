package com.protonvpn.android.vpn

import android.content.Context
import android.os.SystemClock
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.utils.ReschedulableTask
import com.protonvpn.android.utils.jitterMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Singleton
class MaintenanceTracker(
    val scope: CoroutineScope,
    val appContext: Context,
    private val appConfig: AppConfig,
    private val vpnStateMonitor: VpnStateMonitor,
    private val vpnConnectionErrorHandler: VpnConnectionErrorHandler,
) {

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

    private fun getScheduleDelay(): Long = jitterMs(TimeUnit.MINUTES.toMillis(appConfig.getMaintenanceTrackerDelay()))

    private fun now() = SystemClock.elapsedRealtime()

    private val task = ReschedulableTask(scope, ::now) {
        if (vpnStateMonitor.isConnected && appConfig.isMaintenanceTrackerEnabled()) {
            vpnConnectionErrorHandler.maintenanceCheck()
            scheduleIn(getScheduleDelay())
        }
    }
}
