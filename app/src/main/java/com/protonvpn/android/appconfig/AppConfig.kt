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

import android.os.SystemClock
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.bugreport.DynamicReportModel
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.ReschedulableTask
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.jitterMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.proton.core.network.domain.ApiResult
import java.util.concurrent.TimeUnit

class AppConfig(
    private val scope: CoroutineScope,
    private val api: ProtonApiRetroFit,
    private val currentUser: CurrentUser,
    private val userPlanManager: UserPlanManager
) {

    private var appConfigResponseObservable: MutableLiveData<AppConfigResponse>

    val dynamicReportModelObservable = MutableLiveData<DynamicReportModel>(
        Storage.load<DynamicReportModel>(
            DynamicReportModel::class.java
        ) { DynamicReportModel(DynamicReportModel.defaultCategories) })

    val apiNotificationsResponseObservable = MutableLiveData<ApiNotificationsResponse>(
            Storage.load<ApiNotificationsResponse>(
                    ApiNotificationsResponse::class.java, ApiNotificationsResponse(emptyArray())))

    private val appConfigResponse get() = appConfigResponseObservable.value!!

    private var updateTask = ReschedulableTask(scope, SystemClock::elapsedRealtime) {
        updateInternal()
    }

    init {
        appConfigResponseObservable = MutableLiveData(Storage.load(AppConfigResponse::class.java, getDefaultConfig()))
        updateTask.scheduleIn(0)

        scope.launch {
            userPlanManager.planChangeFlow.collect {
                updateInternal()
            }
        }
    }

    fun update() {
        scope.launch {
            updateInternal()
        }
    }

    fun getMaintenanceTrackerDelay(): Long = maxOf(Constants.MINIMUM_MAINTENANCE_CHECK_MINUTES,
        appConfigResponse.underMaintenanceDetectionDelay)

    fun isMaintenanceTrackerEnabled(): Boolean = appConfigResponse.featureFlags.maintenanceTrackerEnabled

    fun getOpenVPNPorts(): DefaultPorts = getDefaultPortsConfig().getOpenVPNPorts()

    fun getWireguardPorts(): DefaultPorts = getDefaultPortsConfig().getWireguardPorts()

    private fun getDefaultPortsConfig() : DefaultPortsConfig = appConfigResponse.defaultPortsConfig ?: DefaultPortsConfig.defaultConfig

    fun getSmartProtocolConfig(): SmartProtocolConfig {
        val smartConfig = appConfigResponse.smartProtocolConfig
        return smartConfig ?: getDefaultConfig().smartProtocolConfig!!
    }

    fun getFeatureFlags(): FeatureFlags = appConfigResponse.featureFlags

    fun getRatingConfig(): RatingConfig = appConfigResponse.ratingConfig ?: getDefaultRatingConfig()

    fun getLiveConfig(): LiveData<AppConfigResponse> = appConfigResponseObservable

    private suspend fun updateInternal() {
        val result = api.getAppConfig()
        val dynamicReportModel = api.getDynamicReportConfig()
        dynamicReportModel.valueOrNull?.let {
            Storage.save(it)
            dynamicReportModelObservable.value = it
        }
        result.valueOrNull?.let { config ->
            Storage.save(config)
            appConfigResponseObservable.value = config
            if (currentUser.isLoggedIn()) {
                val notificationsResponse = if (config.featureFlags.pollApiNotifications)
                    api.getApiNotifications().valueOrNull
                else
                    ApiNotificationsResponse(emptyArray())
                notificationsResponse?.let {
                    apiNotificationsResponseObservable.value = it
                    Storage.save(apiNotificationsResponseObservable.value)
                }
            }
        }
        updateTask.scheduleIn(jitterMs(if (result is ApiResult.Error.Connection) UPDATE_DELAY_FAIL else UPDATE_DELAY))
    }

    private fun getDefaultConfig(): AppConfigResponse {
        val defaultPorts = DefaultPortsConfig.defaultConfig
        val defaultFeatureFlags = FeatureFlags()
        val defaultSmartProtocolConfig = SmartProtocolConfig(
            ikeV2Enabled = true,
            openVPNEnabled = true,
            wireguardEnabled = false
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
        private val UPDATE_DELAY = TimeUnit.DAYS.toMillis(1)
        private val UPDATE_DELAY_FAIL = TimeUnit.HOURS.toMillis(3)
    }
}
