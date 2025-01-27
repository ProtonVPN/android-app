/*
 * Copyright (c) 2025 Proton AG
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
package com.protonvpn.android.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.protonvpn.android.ui.storage.UiStateStorage
import com.protonvpn.android.widget.data.WidgetTracker
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Reusable
class WidgetManager @Inject constructor(
    private val scope: CoroutineScope,
    @ApplicationContext private val context: Context,
    widgetTracker: WidgetTracker,
    private val uiStateStorage: UiStateStorage,
    private val workManager: WorkManager,
) {
    private val widgetManager = AppWidgetManager.getInstance(context)

    val supportsNativeWidgetSelector: Boolean
        get() = widgetManager.isRequestPinAppWidgetSupported

    val hasAddedWidget = widgetTracker.haveWidgets

    val showWidgetAdoptionFlow: Flow<Boolean> = combine(
        uiStateStorage.state.map { it.shouldShowWidgetAdoption ?: false },
        hasAddedWidget
    ) { shouldShowWidgetAdoption, hasAddedWidget ->
        shouldShowWidgetAdoption && hasAddedWidget != true
    }

    init {
        scope.launch {
            if (uiStateStorage.state.first().shouldShowWidgetAdoption == null) {
                uiStateStorage.update { it.copy(shouldShowWidgetAdoption = false) }
                val workRequest = OneTimeWorkRequestBuilder<WidgetAdoptionWorker>()
                    .setInitialDelay(2, TimeUnit.DAYS)
                    .build()
                workManager
                    .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, workRequest)
            }
        }
    }

    private val adoptWidgetLambda: () -> Unit = {
        openNativeWidgetSelector()
        onWidgetAdoptionShown()
    }

    val widgetAdoptionAddNewAction =
        if (supportsNativeWidgetSelector) adoptWidgetLambda else null

    fun onWidgetAdoptionShown() {
        scope.launch {
            uiStateStorage.update { it.copy(shouldShowWidgetAdoption = false) }
        }
    }

    fun openNativeWidgetSelector() {
        val myWidgetProvider = ComponentName(context, ProtonVpnWidgetReceiver::class.java)
        widgetManager.requestPinAppWidget(
            myWidgetProvider,
            null,
            null
        )
    }
}

private const val UNIQUE_WORK_NAME = "WidgetAdoptionUpdate"

@HiltWorker
class WidgetAdoptionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val uiStorage: UiStateStorage,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        uiStorage.update { it.copy(shouldShowWidgetAdoption = true) }
        return Result.success()
    }
}