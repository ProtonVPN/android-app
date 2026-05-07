/*
 * Copyright (c) 2020 Proton AG
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
import com.protonvpn.android.UpdateMigration
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.globalsettings.GlobalSettingsManager
import com.protonvpn.android.appconfig.periodicupdates.IsInForeground
import com.protonvpn.android.appconfig.periodicupdates.IsLoggedIn
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateSpec
import com.protonvpn.android.appconfig.periodicupdates.registerApiCall
import com.protonvpn.android.auth.usecase.GetActiveAuthenticatedAccount
import com.protonvpn.android.models.config.bugreport.DynamicReportModel
import com.protonvpn.android.bugreport.BugReportConfigStore
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.runCatchingCheckedExceptions
import com.protonvpn.android.vpn.ProtocolSelection
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.domain.entity.UserId
import me.proton.core.featureflag.domain.usecase.FetchUnleashTogglesRemote
import me.proton.core.network.domain.ApiException
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.session.SessionProvider
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Reusable
class GetFeatureFlags @VisibleForTesting constructor(
    featureFlagsFlow: Flow<FeatureFlags>
) : Flow<FeatureFlags> by featureFlagsFlow {
    @Inject
    constructor(appConfig: AppConfig) : this(
        appConfig.appConfigFlow.map { it.featureFlags }
            .distinctUntilChanged()
    )
}

@Reusable
class GetRatingConfig @VisibleForTesting constructor(
    ratingConfigFlow: Flow<RatingConfig>
) : Flow<RatingConfig> by ratingConfigFlow {
    @Inject
    constructor(appConfig: AppConfig) : this(
        appConfig.appConfigFlow
            .map { it.ratingConfig }
            .distinctUntilChanged()
    )
}

@Singleton
class AppConfig @Inject constructor(
    mainScope: CoroutineScope,
    private val periodicUpdateManager: PeriodicUpdateManager,
    private val appConfigStore: AppConfigStore,
    private val api: ProtonApiRetroFit,
    private val sessionProvider: SessionProvider,
    private val getNetZone: GetNetZone,
    private val globalSettingsManager: GlobalSettingsManager,
    private val fetchFlags: FetchUnleashTogglesRemote,
    private val getActiveAuthenticatedAccount: GetActiveAuthenticatedAccount,
    private val bugReportConfigStore: dagger.Lazy<BugReportConfigStore>,
    userPlanManager: UserPlanManager,
    @IsLoggedIn loggedIn: Flow<Boolean>,
    @IsInForeground inForeground: Flow<Boolean>,
    updateMigration: UpdateMigration,
) {
    // This value is used when filtering servers, let's have it cached
    private var smartProtocolsCached: List<ProtocolSelection>? = null

    private suspend fun currentUserId(): UserId? = getActiveAuthenticatedAccount()?.userId

    val appConfigUpdateEvent = MutableSharedFlow<AppConfigResponse>(extraBufferCapacity = 1)
    val appConfigFlow = appConfigStore.observe()

    private val appConfigUpdate = periodicUpdateManager.registerApiCall(
        "app_config",
        ::updateInternal,
        { currentUserId() },
        PeriodicUpdateSpec(UPDATE_DELAY_UI, setOf(loggedIn, inForeground)),
        PeriodicUpdateSpec(UPDATE_DELAY, UPDATE_DELAY_FAIL, setOf()),
    )

    private val bugReportUpdate = periodicUpdateManager.registerApiCall(
        "bug_report",
        ::updateBugReportInternal,
        { currentUserId() },
        PeriodicUpdateSpec(BUG_REPORT_UPDATE_DELAY, setOf(loggedIn)),
    )

    init {
        userPlanManager.planChangeFlow
            .onEach { forceUpdate(currentUserId()) }
            .launchIn(mainScope)
        if (updateMigration.isUpdatedVersion) {
            mainScope.launch {
                forceUpdate(currentUserId())
            }
        }
    }

    suspend fun forceUpdate(userId: UserId?): ApiResult<Any> {
        return coroutineScope {
            // This can be called on login, launch in parallel.
            val bugReportJob = launch { periodicUpdateManager.executeNow(bugReportUpdate, userId) }
            periodicUpdateManager.executeNow(appConfigUpdate, userId).also {
                bugReportJob.join()
            }
        }
    }

    suspend fun getMaintenanceTrackerDelay(): Long =
        maxOf(Constants.MINIMUM_MAINTENANCE_CHECK_MINUTES, appConfigFlow.first().underMaintenanceDetectionDelay)

    suspend fun isMaintenanceTrackerEnabled(): Boolean = appConfigFlow.first().featureFlags.maintenanceTrackerEnabled

    suspend fun getWireguardPorts(): DefaultPorts = getDefaultPortsConfig().getWireguardPorts()

    private suspend fun getDefaultPortsConfig(): DefaultPortsConfig =
        appConfigFlow.first().defaultPortsConfig

    suspend fun getSmartProtocolConfig(): SmartProtocolConfig =
        appConfigFlow.first().smartProtocolConfig

    suspend fun getSmartProtocols(): List<ProtocolSelection> = smartProtocolsCached
        ?: getSmartProtocolConfig().getSmartProtocols().apply {
            smartProtocolsCached = this
        }

    suspend fun getFeatureFlags(): FeatureFlags = appConfigFlow.first().featureFlags

    private suspend fun updateBugReportInternal(userId: UserId?): ApiResult<DynamicReportModel> {
        val sessionId = sessionProvider.getSessionId(userId)
        val dynamicReportModel = api.getDynamicReportConfig(sessionId)
        dynamicReportModel.valueOrNull?.let {
            bugReportConfigStore.get().save(it)
        }
        return dynamicReportModel
    }

    private suspend fun updateInternal(userId: UserId?): ApiResult<Any> {
        val sessionId = sessionProvider.getSessionId(userId)
        val result = api.getAppConfig(sessionId, getNetZone())
        if (userId != null && sessionId != null) {
            coroutineScope {
                launch { // Run in parallel, we don't care about the results.
                    globalSettingsManager.refresh(userId)
                }
            }
        }

        val flagsResult = suspend {
            fetchFlags(userId)
            fetchFlags(null)
            ApiResult.Success(Unit)
        }.runCatchingCheckedExceptions {
            (it as? ApiException)?.error
        }

        result.valueOrNull?.let { config ->
            appConfigStore.save(config)
            smartProtocolsCached = null
            appConfigUpdateEvent.tryEmit(config)
        }

        return if (result is ApiResult.Success && flagsResult is ApiResult.Error)
            flagsResult
        else
            result
    }

    companion object {
        private val UPDATE_DELAY = TimeUnit.HOURS.toMillis(12)
        private val UPDATE_DELAY_UI = TimeUnit.HOURS.toMillis(2)
        private val UPDATE_DELAY_FAIL = TimeUnit.HOURS.toMillis(2)
        private val BUG_REPORT_UPDATE_DELAY = TimeUnit.DAYS.toMillis(2)
    }
}