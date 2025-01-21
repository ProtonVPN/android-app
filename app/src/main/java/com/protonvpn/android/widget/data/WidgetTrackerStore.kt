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

import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.WidgetRemoved
import com.protonvpn.android.logging.WidgetUpdate
import com.protonvpn.android.logging.WidgetsRestored
import com.protonvpn.android.userstorage.LocalDataStoreFactory
import com.protonvpn.android.userstorage.StoreProvider
import com.protonvpn.android.utils.mapState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.plus

@Singleton
class WidgetTracker @Inject constructor(
    private val mainScope: CoroutineScope,
    widgetTrackerStoreProvider: WidgetTrackerStoreProvider
) {
    private val store = mainScope.async { widgetTrackerStoreProvider.dataStoreWithSuffix("shared") }
    val widgetCount = flow {
        emitAll(store.await().data.map { currentMap -> currentMap.values.sumOf { it.size } })
    }.stateIn(mainScope, SharingStarted.WhileSubscribed(), null)

    val haveWidgets = widgetCount.mapState { count -> count?.let { it > 0 } }

    fun onUpdated(receiverId: String, widgetIds: Set<Int>) {
        update { currentMap ->
            val widgets = currentMap.getOrDefault(receiverId, emptySet())
            val newWidgets = widgets + widgetIds
            ProtonLogger.log(WidgetUpdate, "$receiverId new count: ${newWidgets.size}")
            currentMap + (receiverId to newWidgets)
        }
    }

    fun onDeleted(receiverId: String, widgetIds: Set<Int>) {
        update { currentMap ->
            val widgets = currentMap.getOrDefault(receiverId, emptySet())
            val newWidgets = widgets - widgetIds
            ProtonLogger.log(WidgetRemoved, "$receiverId new count: ${newWidgets.size}")
            currentMap + (receiverId to widgets - widgetIds)
        }
    }

    fun onCleared(receiverId: String) {
        update { currentMap -> currentMap - receiverId }
        ProtonLogger.log(WidgetRemoved, "widgets cleared for $receiverId")
    }

    fun onRestored(receiverId: String, oldWidgetIds: Set<Int>?, newWidgetIds: Set<Int>?) {
        update { currentMap ->
            val widgets = currentMap.getOrDefault(receiverId, emptySet())
            val newWidgets = widgets - (oldWidgetIds ?: emptySet()) + (newWidgetIds ?: emptySet())
            ProtonLogger.log(WidgetsRestored, "$receiverId new count: ${newWidgets.size}")
            currentMap + (receiverId to newWidgets)
        }
    }

    private fun update(transform: suspend (Map<String,Set<Int>>) -> Map<String,Set<Int>>) =
        mainScope.launch { store.await().updateData(transform) }
}

@Singleton
class WidgetTrackerStoreProvider @Inject constructor(
    factory: LocalDataStoreFactory
) : StoreProvider<Map<String, Set<Int>>>(
    "widget_tracker",
    emptyMap(),
    MapSerializer(String.serializer(), SetSerializer(Int.serializer())),
    factory
)