/*
 * Copyright (c) 2025. Proton AG
 *
 *  This file is part of ProtonVPN.
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

package com.protonvpn.android.update

import android.app.Activity
import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.AppUpdateResult
import com.google.android.play.core.ktx.requestUpdateFlow
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

data class GoogleAppUpdateInfo(
    val updateToken: com.google.android.play.core.appupdate.AppUpdateInfo,
): AppUpdateInfo(
    stalenessDays = updateToken.clientVersionStalenessDays() ?: 0,
    availableVersionCode = updateToken.availableVersionCode(),
)

@Singleton
class AppUpdateManagerImpl @Inject constructor(
    @ApplicationContext appContext: Context,
    mainScope: CoroutineScope,
) : AppUpdateManager() {
    private val updateManager by lazy { AppUpdateManagerFactory.create(appContext) }

    private val checkForUpdateFlowInternal = updateManager.requestUpdateFlow()
        .map { update ->
            when(update) {
                is AppUpdateResult.Available -> {
                    val updateInfo = update.updateInfo
                    val updateAvailability = updateInfo.updateAvailability()

                    if (updateAvailability == UpdateAvailability.UPDATE_AVAILABLE ||
                        updateAvailability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                    ) {
                        logUpdateInfo(updateInfo)
                        GoogleAppUpdateInfo(updateInfo)
                    } else {
                        null
                    }
                }

                is AppUpdateResult.Downloaded -> null
                is AppUpdateResult.InProgress -> null
                AppUpdateResult.NotAvailable -> null
            }
        }.catch { e ->
            when (e) {
                is CancellationException -> throw e
                else -> {
                    val message = "Unable to obtain in-app update info $e"
                    ProtonLogger.logCustom(LogLevel.WARN, LogCategory.APP_UPDATE, message)
                    null
                }
            }
        }

    override val checkForUpdateFlow = checkForUpdateFlowInternal
        .onStart { emit(null) } // Emit a value immediately, don't delay the observers' "combine".
        .shareIn(mainScope, SharingStarted.WhileSubscribed(15_000), replay = 1)

    override suspend fun checkForUpdate(): AppUpdateInfo? = checkForUpdateFlowInternal.firstOrNull()

    override fun launchUpdateFlow(activity: Activity, updateInfo: AppUpdateInfo) {
        val updateToken = (updateInfo as GoogleAppUpdateInfo).updateToken
        updateManager.startUpdateFlow(
            updateToken,
            activity,
            AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE)
        )
    }

    private fun logUpdateInfo(updateInfo: com.google.android.play.core.appupdate.AppUpdateInfo) {
        val logInfo = with (updateInfo) {
            "availability: ${updateAvailability()}, staleness: ${clientVersionStalenessDays()}"
        }
        ProtonLogger.logCustom(LogCategory.APP_UPDATE, "in-app update: $logInfo")
    }
}