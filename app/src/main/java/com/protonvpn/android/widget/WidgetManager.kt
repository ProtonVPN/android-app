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
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@Reusable
class WidgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val widgetManager = AppWidgetManager.getInstance(context)

    val supportsNativeWidgetSelector: Boolean
        get() = widgetManager.isRequestPinAppWidgetSupported

    fun openNativeWidgetSelector() {
        val myWidgetProvider = ComponentName(context, ProtonVpnWidgetReceiver::class.java)
        widgetManager.requestPinAppWidget(
            myWidgetProvider,
            null,
            null
        )
    }
}
