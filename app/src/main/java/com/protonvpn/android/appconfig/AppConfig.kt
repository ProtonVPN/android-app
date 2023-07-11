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

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.MutableLiveData
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.globalsettings.GlobalSettingsManager
import com.protonvpn.android.appconfig.periodicupdates.IsInForeground
import com.protonvpn.android.appconfig.periodicupdates.IsLoggedIn
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateSpec
import com.protonvpn.android.appconfig.periodicupdates.registerApiCall
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.bugreport.DynamicReportModel
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.mapState
import com.protonvpn.android.vpn.ProtocolSelection
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import me.proton.core.network.domain.ApiResult
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Reusable
class GetFeatureFlags @VisibleForTesting constructor(
    featureFlagsFlow: StateFlow<FeatureFlags>
) : StateFlow<FeatureFlags> by featureFlagsFlow {
    @Inject
    constructor(appConfig: AppConfig) : this(
        appConfig.appConfigFlow.mapState { it.featureFlags }
    )
}

@Singleton
class AppConfig @Inject constructor(
    mainScope: CoroutineScope,
    private val periodicUpdateManager: PeriodicUpdateManager,
    private val api: ProtonApiRetroFit,
    private val getNetZone: GetNetZone,
    private val globalSettingsManager: GlobalSettingsManager,
    private val currentUser: CurrentUser,
    userPlanManager: UserPlanManager,
    @IsLoggedIn loggedIn: Flow<Boolean>,
    @IsInForeground inForeground: Flow<Boolean>
) {
    // This value is used when filtering servers, let's have it cached
    private var smartProtocolsCached: List<ProtocolSelection>? = null

    val appConfigUpdateEvent = MutableSharedFlow<AppConfigResponse>(extraBufferCapacity = 1)
    val appConfigFlow = appConfigUpdateEvent.stateIn(
        mainScope,
        started = SharingStarted.Eagerly,
        initialValue = Storage.load(AppConfigResponse::class.java, getDefaultConfig())
    )

    val dynamicReportModelObservable = MutableLiveData(
        Storage.load<DynamicReportModel>(
            DynamicReportModel::class.java
        ) { DynamicReportModel(DynamicReportModel.defaultCategories) }
    )

    private val appConfigResponse get() = appConfigFlow.value
    private val appConfigUpdate = periodicUpdateManager.registerApiCall(
        "app_config",
        ::updateInternal,
        PeriodicUpdateSpec(UPDATE_DELAY_UI, setOf(loggedIn, inForeground)),
        PeriodicUpdateSpec(UPDATE_DELAY, UPDATE_DELAY_FAIL, setOf(loggedIn)),
    )

    init {
        userPlanManager.planChangeFlow
            .onEach { forceUpdate() }
            .launchIn(mainScope)
    }

    suspend fun forceUpdate() = periodicUpdateManager.executeNow(appConfigUpdate)

    fun getMaintenanceTrackerDelay(): Long =
        maxOf(Constants.MINIMUM_MAINTENANCE_CHECK_MINUTES, appConfigResponse.underMaintenanceDetectionDelay)

    fun isMaintenanceTrackerEnabled(): Boolean = appConfigResponse.featureFlags.maintenanceTrackerEnabled

    fun getOpenVPNPorts(): DefaultPorts = getDefaultPortsConfig().getOpenVPNPorts()

    fun getWireguardPorts(): DefaultPorts = getDefaultPortsConfig().getWireguardPorts()

    private fun getDefaultPortsConfig(): DefaultPortsConfig =
        appConfigResponse.defaultPortsConfig ?: DefaultPortsConfig.defaultConfig

    fun getSmartProtocolConfig(): SmartProtocolConfig {
        val smartConfig = appConfigResponse.smartProtocolConfig
        return smartConfig ?: getDefaultConfig().smartProtocolConfig!!
    }

    fun getSmartProtocols(): List<ProtocolSelection> = smartProtocolsCached
        ?: getSmartProtocolConfig().getSmartProtocols().apply {
            smartProtocolsCached = this
        }

    fun getFeatureFlags(): FeatureFlags = appConfigResponse.featureFlags

    fun getRatingConfig(): RatingConfig = appConfigResponse.ratingConfig ?: getDefaultRatingConfig()

    private suspend fun updateInternal(): ApiResult<AppConfigResponse> {
        val result = api.getAppConfig(getNetZone())
        val dynamicReportModel = api.getDynamicReportConfig()
        dynamicReportModel.valueOrNull?.let {
            Storage.save(it)
            dynamicReportModelObservable.value = it
        }
        if (currentUser.isLoggedIn()) {
            globalSettingsManager.refresh()
        }
        result.valueOrNull?.let { config ->
            Storage.save(config)
            smartProtocolsCached = null
            appConfigUpdateEvent.tryEmit(config)
        }
        return result
    }

    private fun getDefaultConfig(): AppConfigResponse {
        val defaultPorts = DefaultPortsConfig.defaultConfig
        val defaultFeatureFlags = FeatureFlags()
        val defaultSmartProtocolConfig = SmartProtocolConfig(
            openVPNEnabled = true,
            wireguardEnabled = true,
            wireguardTcpEnabled = true,
            wireguardTlsEnabled = true,
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
