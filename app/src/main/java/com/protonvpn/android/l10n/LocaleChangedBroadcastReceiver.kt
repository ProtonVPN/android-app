/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.l10n

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.notifications.NotificationChannels
import com.protonvpn.android.promooffers.data.ApiNotificationManager
import com.protonvpn.android.servers.UpdateServerTranslations
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

private val ApiUpdatesDelay = 15.seconds

@AndroidEntryPoint
class LocaleChangedBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationChannels: NotificationChannels

    // This receiver is called in a separate, lightweight process.
    // Use as little dependencies as possible.

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCALE_CHANGED) return

        notificationChannels.updateTranslations(context)

        // Delay the API updates a little bit, locale change notifies multiple apps and the device
        // is busy.
        scheduleDelayedApiUpdates(context)
    }

    private fun scheduleDelayedApiUpdates(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<LocaleChangedApiUpdateWorker>()
            .setConstraints(constraints)
            .setInitialDelay(ApiUpdatesDelay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            LocaleChangedApiUpdateWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }
}

@HiltWorker
class LocaleChangedApiUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dispatcherProvider: VpnDispatcherProvider,
    private val updateServerTranslations: dagger.Lazy<UpdateServerTranslations>,
    private val apiNotificationManager: dagger.Lazy<ApiNotificationManager>,
): CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        withContext(dispatcherProvider.Main) {
            updateServerTranslations.get().forceUpdate()
            apiNotificationManager.get().forceUpdate()
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "LocaleChangedApiUpdateWorker"
    }
}