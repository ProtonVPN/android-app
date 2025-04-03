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

import android.annotation.TargetApi
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.core.util.kotlin.DispatcherProvider
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class WidgetAdoptionUiType {
    None,
    AddWidgetButton, // Application can trigger pin widget directly.
    Instructions // Can't trigger pin widget, show instructions.
}

@Singleton
class WidgetManager @Inject constructor(
    private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    widgetTracker: WidgetTracker,
    private val uiStateStorage: UiStateStorage,
    private val workManager: WorkManager,
) {
    // This may be null on some devices, e.g. TVs.
    private val widgetManager: AppWidgetManager? = AppWidgetManager.getInstance(context)

    companion object {
        // Some manufacturers override native picker with their own implementation
        // use fallback UI in that case instead of native picker
        // Add manufacturers in lowercase to the list.
        val PICKER_UNSUPPORTED_MANUFACTURER_LIST = listOf("xiaomi")
        val WIDGET_ADDED_ACTION = "intent.action.WIDGET_ADDED";
    }


    private val hasAddedWidget: StateFlow<Boolean?> = widgetTracker.haveWidgets

    val showWidgetAdoptionFlow: Flow<WidgetAdoptionUiType> = combine(
        uiStateStorage.state.map { it.shouldShowWidgetAdoption ?: false },
        hasAddedWidget
    ) { shouldShowWidgetAdoption, hasAddedWidget ->
        if (shouldShowWidgetAdoption && hasAddedWidget != true) {
            if (supportsNativeWidgetSelector()) {
                WidgetAdoptionUiType.AddWidgetButton
            } else {
                WidgetAdoptionUiType.Instructions
            }
        } else {
            WidgetAdoptionUiType.None
        }
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

    @TargetApi(26)
    suspend fun supportsNativeWidgetSelector(): Boolean =
        if (Build.VERSION.SDK_INT < 26 || Build.MANUFACTURER.lowercase() in PICKER_UNSUPPORTED_MANUFACTURER_LIST) {
            false
        } else {
            withContext(dispatcherProvider.Io) {
                widgetManager?.isRequestPinAppWidgetSupported ?: false
            }
        }

    @RequiresApi(26)
    fun addWidget() {
        openNativeWidgetSelector()
        onWidgetAdoptionShown()
    }

    fun onWidgetAdoptionShown() {
        scope.launch {
            uiStateStorage.update { it.copy(shouldShowWidgetAdoption = false) }
        }
    }

    @RequiresApi(26)
    fun openNativeWidgetSelector() {
        val myWidgetProvider = ComponentName(context, ProtonVpnWidgetReceiver::class.java)
        val intent = Intent(WIDGET_ADDED_ACTION).apply { setPackage(context.packageName) }
        val successPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            widgetManager?.requestPinAppWidget(
                myWidgetProvider,
                null,
                successPendingIntent
            )
        } catch (_: IllegalStateException) {
            // The app is not in foreground - this can happen rarely if the app goes to background right after user
            // taps the button.
        }
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
