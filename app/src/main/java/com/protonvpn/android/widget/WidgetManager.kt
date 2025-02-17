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

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.widget.Toast
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.protonvpn.android.R
import com.protonvpn.android.ui.storage.UiStateStorage
import com.protonvpn.android.utils.AndroidUtils.registerBroadcastReceiver
import com.protonvpn.android.widget.data.WidgetTracker
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
import javax.inject.Singleton

@Singleton
class WidgetManager @Inject constructor(
    private val scope: CoroutineScope,
    @ApplicationContext private val context: Context,
    widgetTracker: WidgetTracker,
    private val uiStateStorage: UiStateStorage,
    private val workManager: WorkManager,
) {
    private val widgetManager: AppWidgetManager? =
        if (Build.MANUFACTURER.lowercase() in PICKER_UNSUPPORTED_MANUFACTURER_LIST) {
            null
        } else {
            AppWidgetManager.getInstance(context) // This may return null on some devices, e.g. TVs.
        }

    companion object {
        // Some manufacturers override native picker with their own implementation
        // use fallback UI in that case instead of native picker
        // Add manufacturers in lowercase to the list.
        val PICKER_UNSUPPORTED_MANUFACTURER_LIST = listOf("xiaomi")
        val WIDGET_ADDED_ACTION = "intent.action.WIDGET_ADDED";
    }

    val supportsNativeWidgetSelector: Boolean
        get() = widgetManager?.isRequestPinAppWidgetSupported ?: false


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
        scope.launch {
            hasAddedWidget.collect { hasAdded ->
                if (hasAdded == true) {
                    workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                }
            }
        }
        context.registerBroadcastReceiver(IntentFilter(WIDGET_ADDED_ACTION)) {
            Toast.makeText(context, R.string.widget_toast_added_message, Toast.LENGTH_SHORT).show()
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
        val intent = Intent(WIDGET_ADDED_ACTION).apply { setPackage(context.packageName) }
        val successPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        widgetManager?.requestPinAppWidget(
            myWidgetProvider,
            null,
            successPendingIntent
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
