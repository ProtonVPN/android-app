/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.appconfig.globalsettings

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.utils.flatMapLatestNotNull
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.isRetryable
import me.proton.core.network.domain.session.SessionId
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.extension.isCredentialLess
import me.proton.core.usersettings.domain.usecase.GetUserSettings
import me.proton.core.util.kotlin.DispatcherProvider
import me.proton.core.util.kotlin.runCatchingCheckedExceptions
import javax.inject.Inject
import javax.inject.Singleton

interface GlobalSettingUpdateScheduler {
    fun updateRemoteTelemetry(isEnabled: Boolean)
}

@Reusable
class NoopGlobalSettingsUpdateScheduler @Inject constructor(): GlobalSettingUpdateScheduler {
    override fun updateRemoteTelemetry(isEnabled: Boolean) = Unit
}

@HiltWorker
class GlobalSettingsUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val globalSettingsManager: GlobalSettingsManager,
    private val dispatcherProvider: DispatcherProvider
) : CoroutineWorker(context, params) {

    @Reusable
    class Scheduler @Inject constructor(
        @ApplicationContext private val appContext: Context
    ) : GlobalSettingUpdateScheduler {
        override fun updateRemoteTelemetry(isEnabled: Boolean) {
            val input = Data.Builder().putBoolean(KEY_TELEMETRY_ENABLED, isEnabled).build()
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val workRequest = OneTimeWorkRequestBuilder<GlobalSettingsUpdateWorker>()
                .setConstraints(constraints)
                .setInputData(input)
                .build()
            WorkManager.getInstance(appContext)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, workRequest)
        }
    }

    override suspend fun doWork(): Result =
        if (inputData.hasKeyWithValueOfType(KEY_TELEMETRY_ENABLED, java.lang.Boolean::class.java)) {
            // When more settings are added this will become more complext
            val isEnabled = inputData.getBoolean(KEY_TELEMETRY_ENABLED, false)
            val result = withContext(dispatcherProvider.Main) {
                globalSettingsManager.uploadGlobalTelemetrySetting(isEnabled)
            }
            if (result) Result.success() else Result.failure()
        } else {
            Result.success()
        }

    companion object {
        private const val UNIQUE_WORK_NAME = "GlobalSettingsUpdateWorker"
        private const val KEY_TELEMETRY_ENABLED = "telemetry_enabled"
    }
}

@Singleton
class GlobalSettingsManager @Inject constructor(
    private val mainScope: CoroutineScope,
    currentUser: CurrentUser,
    private val api: ProtonApiRetroFit,
    private val prefs: GlobalSettingsPrefs,
    private val userLocalSettingsManager: CurrentUserLocalSettingsManager,
    private val isTv: IsTvCheck,
    private val globalSettingsUpdateScheduler: GlobalSettingUpdateScheduler,
    private val getUserSettings: GetUserSettings,
    private val userManager: UserManager,
) {
    init {
        // The local cache of global telemetry flag should also be per-user (VPNAND-1381)
        currentUser.vpnUserFlow.flatMapLatestNotNull { vpnUser ->
            prefs.telemetryEnabledFlow
                .drop(1)
                .distinctUntilChanged()
                .onEach { isEnabled ->
                    if (!isEnabled) {
                        userLocalSettingsManager.getRawUserSettingsStore(vpnUser)
                            .updateData { it.copy(telemetry = false) }
                    }
                }
        }.launchIn(mainScope)

        currentUser.jointUserFlow.flatMapLatestNotNull { (accountUser, vpnUser) ->
            userLocalSettingsManager.getRawUserSettingsStore(vpnUser).data
                .map { it.telemetry }
                .drop(1)
                .distinctUntilChanged()
                .onEach { isEnabled ->
                    if (isEnabled && !accountUser.isCredentialLess())
                        enableGlobalTelemetry()
                }
        }.launchIn(mainScope)
    }

    suspend fun refresh(userId: UserId) {
        // getUser() calls the backend on rare occasion and may throw ApiException :(
        val user = runCatchingCheckedExceptions { userManager.getUser(userId) }.getOrNull()
        if (!isTv() && user != null && !user.isCredentialLess()) { // VPNAND-1185
            runCatchingCheckedExceptions {
                getUserSettings(userId, refresh = true)
            }.onSuccess {
                applyChange(it.telemetry)
            }
        }
    }

    suspend fun uploadGlobalTelemetrySetting(isEnabled: Boolean): Boolean {
        val result = api.putTelemetryGlobalSetting(isEnabled)
        if (result is ApiResult.Success) {
            applyChange(result.value.userSettings.telemetryEnabled)
        }
        return result is ApiResult.Success || !result.isRetryable()
    }

    private fun enableGlobalTelemetry() {
        prefs.telemetryEnabled = true
        globalSettingsUpdateScheduler.updateRemoteTelemetry(true)
    }

    private fun applyChange(telemetryEnabled: Boolean?) {
        prefs.telemetryEnabled = telemetryEnabled ?: false
    }
}
