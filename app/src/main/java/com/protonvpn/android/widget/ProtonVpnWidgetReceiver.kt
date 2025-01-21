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

package com.protonvpn.android.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.protonvpn.android.widget.data.WidgetTracker
import com.protonvpn.android.widget.ui.ProtonVpnGlanceWidget
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

abstract class TrackedWidgetReceiver(val receiverId: String) : GlanceAppWidgetReceiver() {

    @Inject
    lateinit var widgetTracker: WidgetTracker

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        widgetTracker.onUpdated(receiverId, appWidgetIds.toSet())
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        widgetTracker.onDeleted(receiverId, appWidgetIds.toSet())
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        widgetTracker.onCleared(receiverId)
    }

    override fun onRestored(
        context: Context?,
        oldWidgetIds: IntArray?,
        newWidgetIds: IntArray?
    ) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        widgetTracker.onRestored(receiverId, oldWidgetIds?.toSet(), newWidgetIds?.toSet())
    }

    override val glanceAppWidget: GlanceAppWidget = ProtonVpnGlanceWidget()
}

@AndroidEntryPoint
class ProtonVpnWidgetReceiver : TrackedWidgetReceiver("small")

@AndroidEntryPoint
class ProtonVpnWidgetMaterialReceiver : TrackedWidgetReceiver("small_material")

@AndroidEntryPoint
class ProtonVpnWidgetBigReceiver : TrackedWidgetReceiver("big")

@AndroidEntryPoint
class ProtonVpnWidgetMaterialBigReceiver : TrackedWidgetReceiver("big_material")

fun hasMaterialYouTheme(receiver: ComponentName) =
    receiver.className in arrayOf(
        ProtonVpnWidgetMaterialReceiver::class.java.name,
        ProtonVpnWidgetMaterialBigReceiver::class.java.name
    )
