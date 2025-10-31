/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.update

import android.app.Activity
import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.AppUpdateResult
import com.google.android.play.core.ktx.requestUpdateFlow
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import me.proton.core.featureflag.domain.ExperimentalProtonFeatureFlag
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureId
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

private const val UPDATE_PROMPT_STALENESS_DAYS = 45
private val PROMPT_INTERVALS_IN_DAYS = listOf(21, 13, 8, 5, 3, 2)

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

    override val checkForUpdateFlow = updateManager.requestUpdateFlow()
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
        }.shareIn(mainScope, SharingStarted.WhileSubscribed(15_000), replay = 1)

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

@OptIn(ExperimentalProtonFeatureFlag::class)
@Reusable
class IsUpdatePromptForStaleVersionEnabled @Inject constructor(
    private val currentUser: CurrentUser,
    private val featureFlagManager: FeatureFlagManager
) {
    suspend operator fun invoke(): Boolean =
        featureFlagManager.getValue(currentUser.user()?.userId, FeatureId("InAppUpdateForStaleVersionsEnabled"))
}

@Singleton
class UpdatePromptForStaleVersion @Inject constructor(
    private val appUpdateManager: AppUpdateManager,
    private val appFeaturesPrefs: AppFeaturesPrefs,
    private val isFeatureEnabled: IsUpdatePromptForStaleVersionEnabled,
    @WallClock private val clock: () -> Long,
) {

    suspend fun getUpdatePrompt(): AppUpdateInfo? {
        if (!isFeatureEnabled()) return null

        val updateInfo = appUpdateManager.checkForUpdate()
        resetPromptStateIfNeeded(updateInfo)

        return updateInfo.takeIf {
            updateInfo != null &&
                    updateInfo.stalenessDays >= UPDATE_PROMPT_STALENESS_DAYS && isNextPromptDue()
        }
    }

    fun launchUpdateFlow(activity: Activity, updateInfo: AppUpdateInfo) {
        ProtonLogger.logCustom(LogCategory.APP_UPDATE, "In-app update started")
        appFeaturesPrefs.lastUpdatePromptTimestamp = clock()
        appFeaturesPrefs.lastUpdatePromptTryCount++
        appUpdateManager.launchUpdateFlow(activity, updateInfo)
    }

    private fun isNextPromptDue(): Boolean {
        if (appFeaturesPrefs.lastUpdatePromptTryCount == 0) return true

        val intervalIndex = appFeaturesPrefs.lastUpdatePromptTryCount - 1
        val lastPromptTime = appFeaturesPrefs.lastUpdatePromptTimestamp.milliseconds
        val nextPromptDays = PROMPT_INTERVALS_IN_DAYS.getOrElse(intervalIndex) { PROMPT_INTERVALS_IN_DAYS.last() }
        return lastPromptTime + nextPromptDays.days <= clock().milliseconds
    }

    private fun resetPromptStateIfNeeded(updateInfo: AppUpdateInfo?) {
        val timeSinceLastPrompt = (clock() - appFeaturesPrefs.lastUpdatePromptTimestamp).milliseconds
        val stalenessDays = updateInfo?.stalenessDays?.days
        if (stalenessDays == null || stalenessDays < timeSinceLastPrompt) {
            appFeaturesPrefs.lastUpdatePromptTimestamp = 0L
            appFeaturesPrefs.lastUpdatePromptTryCount = 0
        }
    }
}
