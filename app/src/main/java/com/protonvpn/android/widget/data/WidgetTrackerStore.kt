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

package com.protonvpn.android.widget.data

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.WidgetRemoved
import com.protonvpn.android.logging.WidgetUpdate
import com.protonvpn.android.logging.WidgetsRestored
import com.protonvpn.android.userstorage.LocalDataStoreFactory
import com.protonvpn.android.userstorage.StoreProvider
import com.protonvpn.android.utils.mapState
import com.protonvpn.android.widget.WidgetType
import com.protonvpn.android.widget.toWidgetReceiverId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

typealias WidgetReceiverId = String
typealias WidgetId = Int

@Singleton
class WidgetTracker @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val mainScope: CoroutineScope,
    widgetTrackerStoreProvider: WidgetTrackerStoreProvider
) {
    private val manager: AppWidgetManager? by lazy { AppWidgetManager.getInstance(appContext) }

    private val store = mainScope.async { widgetTrackerStoreProvider.dataStoreWithSuffix("shared") }

    fun start() {
        mainScope.launch {
            val firstStart = store.await().data.first().firstStart
            if (firstStart) {
                delay(3000)
                firstTimeSetupRestore()
                store.await().updateData { it.copy(firstStart = false) }
            }
        }
    }

    // If app data was cleared but some widgets were still present we need to restore them.
    private fun firstTimeSetupRestore() {
        manager?.let { manager ->
            val providers = getInstalledProvidersForPackageCompat(manager, appContext.packageName)
            providers.forEach { provider ->
                val receiverId = provider.provider.className.toWidgetReceiverId()
                val widgetIds = manager.getAppWidgetIds(provider.provider)
                if (widgetIds.isNotEmpty() && receiverId != null) {
                    onUpdated(receiverId, widgetIds.toSet())
                }
            }
        }
    }

    private val data = flow {
        emitAll(store.await().data)
    }.stateIn(mainScope, SharingStarted.WhileSubscribed(), null)

    val widgetCount: StateFlow<Int?> = data.mapState { data -> data?.receiverToWidgets?.values?.sumOf { it.size } }

    val haveWidgets: StateFlow<Boolean?> = widgetCount.mapState { count -> count?.let { it > 0 } }

    fun onUpdated(receiverId: WidgetReceiverId, widgetIds: Set<WidgetId>) {
        update { data ->
            val widgets = data.receiverToWidgets.getOrDefault(receiverId, emptySet())
            val newWidgets = widgets + widgetIds
            ProtonLogger.log(WidgetUpdate, "$receiverId new count: ${newWidgets.size}")
            data.copy(receiverToWidgets = data.receiverToWidgets + (receiverId to newWidgets))
        }
    }

    fun onDeleted(receiverId: WidgetReceiverId, widgetIds: Set<WidgetId>) {
        update { data ->
            val widgets = data.receiverToWidgets.getOrDefault(receiverId, emptySet())
            val newWidgets = widgets - widgetIds
            ProtonLogger.log(WidgetRemoved, "$receiverId new count: ${newWidgets.size}")
            data.copy(receiverToWidgets = data.receiverToWidgets + (receiverId to widgets - widgetIds))
        }
    }

    fun onCleared(receiverId: WidgetReceiverId) {
        update { data -> data.copy(data.receiverToWidgets - receiverId) }
        ProtonLogger.log(WidgetRemoved, "widgets cleared for $receiverId")
    }

    fun onRestored(receiverId: String, oldWidgetIds: Set<WidgetId>?, newWidgetIds: Set<WidgetId>?) {
        update { data ->
            val widgets = data.receiverToWidgets.getOrDefault(receiverId, emptySet())
            val newWidgets = widgets - (oldWidgetIds ?: emptySet()) + (newWidgetIds ?: emptySet())
            ProtonLogger.log(WidgetsRestored, "$receiverId new count: ${newWidgets.size}")
            data.copy(receiverToWidgets = data.receiverToWidgets + (receiverId to newWidgets))
        }
    }

    private fun update(transform: suspend (WidgetTrackerData) -> WidgetTrackerData) =
        mainScope.launch { store.await().updateData(transform) }

    suspend fun firstWidgetType(): WidgetType? {
        val firstNonEmptyReceiverId = data.filterNotNull().first().receiverToWidgets.asSequence()
            .filter { it.value.isNotEmpty() }
            .minByOrNull { it.value.min() }
            ?.key
        return WidgetType.getById(firstNonEmptyReceiverId)
    }
}

private fun getInstalledProvidersForPackageCompat(
    appWidgetManager: AppWidgetManager,
    packageName: String
) = if (Build.VERSION.SDK_INT < 26) {
    appWidgetManager.installedProviders.filter { it.provider.packageName == packageName }
} else {
    appWidgetManager.getInstalledProvidersForPackage(packageName, null)
}

@Serializable
data class WidgetTrackerData(
    val receiverToWidgets: Map<WidgetReceiverId, Set<WidgetId>>,
    val firstStart: Boolean = true
)

@Singleton
class WidgetTrackerStoreProvider @Inject constructor(
    factory: LocalDataStoreFactory
) : StoreProvider<WidgetTrackerData>(
    "widget_tracker_store",
    WidgetTrackerData(emptyMap()),
    WidgetTrackerData.serializer(),
    factory
)
