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
package com.protonvpn.android.appconfig

import androidx.lifecycle.MutableLiveData
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.di.ElapsedRealtimeClock
import com.protonvpn.android.models.config.bugreport.DynamicReportModel
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.ReschedulableTask
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.jitterMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.core.network.domain.ApiResult
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppConfig @Inject constructor(
    private val scope: CoroutineScope,
    private val api: ProtonApiRetroFit,
    userPlanManager: UserPlanManager,
    private val getNetZone: GetNetZone,
    @ElapsedRealtimeClock private val now: () -> Long,
) {
    private var lastUpdateAttempt = Long.MIN_VALUE

    val appConfigUpdateEvent = MutableSharedFlow<AppConfigResponse>(extraBufferCapacity = 1)
    val appConfigFlow = appConfigUpdateEvent.stateIn(
        scope,
        started = SharingStarted.Eagerly,
        initialValue = Storage.load(AppConfigResponse::class.java, getDefaultConfig())
    )

    val dynamicReportModelObservable = MutableLiveData<DynamicReportModel>(
        Storage.load<DynamicReportModel>(
            DynamicReportModel::class.java
        ) { DynamicReportModel(DynamicReportModel.defaultCategories) })

    private val appConfigResponse get() = appConfigFlow.value

    private var updateTask = ReschedulableTask(scope, now) {
        updateInternal()
    }

    init {
        updateTask.scheduleIn(0)

        userPlanManager.planChangeFlow.onEach {
            updateInternal()
        }.launchIn(scope)
    }

    suspend fun forceUpdate() = withContext(scope.coroutineContext) {
        updateInternal()
    }

    fun updateFromUI() = scope.launch {
        if (now() - lastUpdateAttempt > UPDATE_DELAY_UI)
            updateTask.scheduleIn(0)
    }

    fun getMaintenanceTrackerDelay(): Long = maxOf(Constants.MINIMUM_MAINTENANCE_CHECK_MINUTES,
        appConfigResponse.underMaintenanceDetectionDelay)

    fun isMaintenanceTrackerEnabled(): Boolean = appConfigResponse.featureFlags.maintenanceTrackerEnabled

    fun getOpenVPNPorts(): DefaultPorts = getDefaultPortsConfig().getOpenVPNPorts()

    fun getWireguardPorts(): DefaultPorts = getDefaultPortsConfig().getWireguardPorts()

    private fun getDefaultPortsConfig(): DefaultPortsConfig =
        appConfigResponse.defaultPortsConfig ?: DefaultPortsConfig.defaultConfig

    fun getSmartProtocolConfig(): SmartProtocolConfig {
        val smartConfig = appConfigResponse.smartProtocolConfig
        return smartConfig ?: getDefaultConfig().smartProtocolConfig!!
    }

    fun getFeatureFlags(): FeatureFlags = appConfigResponse.featureFlags

    fun getRatingConfig(): RatingConfig = appConfigResponse.ratingConfig ?: getDefaultRatingConfig()

    private suspend fun updateInternal() {
        lastUpdateAttempt = now()
        val result = api.getAppConfig(getNetZone())
        val dynamicReportModel = api.getDynamicReportConfig()
        dynamicReportModel.valueOrNull?.let {
            Storage.save(it)
            dynamicReportModelObservable.value = it
        }
        result.valueOrNull?.let { config ->
            Storage.save(config)
            appConfigUpdateEvent.tryEmit(config)
        }
        updateTask.scheduleIn(jitterMs(if (result is ApiResult.Error.Connection) UPDATE_DELAY_FAIL else UPDATE_DELAY))
    }

    private fun getDefaultConfig(): AppConfigResponse {
        val defaultPorts = DefaultPortsConfig.defaultConfig
        val defaultFeatureFlags = FeatureFlags()
        val defaultSmartProtocolConfig = SmartProtocolConfig(
            openVPNEnabled = true,
            wireguardEnabled = true,
            wireguardTcpEnabled = false,
            wireguardTlsEnabled = false,
        )

        return AppConfigResponse(
            defaultPortsConfig = defaultPorts,
            featureFlags = defaultFeatureFlags,
            smartProtocolConfig = defaultSmartProtocolConfig,
            ratingConfig = getDefaultRatingConfig()
        )
    }

    private fun getDefaultRatingConfig(): RatingConfig = RatingConfig(
        eligiblePlans = listOf("plus"),
        successfulConnectionCount = 3,
        daysSinceLastRatingCount = 3,
        daysConnectedCount = 3,
        daysFromFirstConnectionCount = 3
    )

    companion object {
        private val UPDATE_DELAY = TimeUnit.HOURS.toMillis(12)
        private val UPDATE_DELAY_UI = TimeUnit.HOURS.toMillis(2)
        private val UPDATE_DELAY_FAIL = TimeUnit.HOURS.toMillis(2)
    }
}
