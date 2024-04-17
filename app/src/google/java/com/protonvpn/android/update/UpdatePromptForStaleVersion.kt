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

import android.content.Context
import androidx.activity.ComponentActivity
import com.google.android.play.core.appupdate.AppUpdateInfo as GoogleAppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import me.proton.core.featureflag.domain.ExperimentalProtonFeatureFlag
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureId
import java.util.concurrent.CancellationException

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

private const val UPDATE_PROMPT_STALENESS_DAYS = 90
private val PROMPT_INTERVALS_IN_DAYS = listOf(21, 13, 8, 5, 3, 2)
private const val UPDATE_TYPE = AppUpdateType.IMMEDIATE

data class AppUpdateInfo(
    val stalenessDays: Int,
    val updateToken: GoogleAppUpdateInfo
)

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext appContext: Context
) {
    private val updateManager by lazy { AppUpdateManagerFactory.create(appContext) }

    suspend fun checkForUpdate(): AppUpdateInfo? =
        try {
            val updateInfo: GoogleAppUpdateInfo = updateManager.requestAppUpdateInfo()
            logUpdateInfo(updateInfo)
            val updateStalenessDays = updateInfo.clientVersionStalenessDays()

            if (updateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && updateStalenessDays != null
                && updateInfo.isUpdateTypeAllowed(UPDATE_TYPE)
            ) {
                AppUpdateInfo(updateStalenessDays, updateInfo)
            } else {
                null
            }
        } catch(e: CancellationException) {
            throw e
        } catch(e: Exception) {
            val message = "Unable to obtain in-app update info $e"
            ProtonLogger.logCustom(LogLevel.WARN, LogCategory.APP_UPDATE, message)
            null
        }

    fun launchUpdateFlow(activity: ComponentActivity, updateInfo: AppUpdateInfo) {
        updateManager.startUpdateFlow(updateInfo.updateToken, activity, AppUpdateOptions.defaultOptions(UPDATE_TYPE))
    }

    private fun logUpdateInfo(updateInfo: GoogleAppUpdateInfo) {
        if (updateInfo.updateAvailability() != UpdateAvailability.UPDATE_NOT_AVAILABLE) {
            val logInfo = with (updateInfo) {
                "availability: ${updateAvailability()}, staleness: ${clientVersionStalenessDays()}, " +
                    "type ${UPDATE_TYPE} allowed: ${isUpdateTypeAllowed(UPDATE_TYPE)})"
            }
            ProtonLogger.logCustom(LogCategory.APP_UPDATE, "in-app update: $logInfo")
        }
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
            updateInfo != null && updateInfo.stalenessDays >= UPDATE_PROMPT_STALENESS_DAYS && isNextPromptDue()
        }
    }

    fun launchUpdateFlow(activity: ComponentActivity, updateInfo: AppUpdateInfo) {
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
        if (updateInfo == null || updateInfo.stalenessDays.days < timeSinceLastPrompt) {
            appFeaturesPrefs.lastUpdatePromptTimestamp = 0L
            appFeaturesPrefs.lastUpdatePromptTryCount = 0
        }
    }
}
