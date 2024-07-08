/*
 * Copyright (c) 2024 Proton AG
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
package com.protonvpn.android.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.protonvpn.android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppIconManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    fun setNewAppIcon(desiredAppIcon: CustomAppIconData) {
        getCurrentIconData().let {
            appContext.packageManager.setComponentEnabledSetting(it.getComponentName(appContext), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        appContext.packageManager.setComponentEnabledSetting(desiredAppIcon.getComponentName(appContext), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }

    fun getCurrentIconData(): CustomAppIconData {
        val activeIcon = enumValues<CustomAppIconData>().firstOrNull {
            appContext.packageManager.getComponentEnabledSetting(it.getComponentName(appContext)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        return activeIcon ?: CustomAppIconData.DEFAULT
    }
}

enum class CustomAppIconData(
    private val componentName: String,
    @DrawableRes val iconPreviewResId: Int,
    @StringRes val labelResId: Int,
    val category: IconCategory
) {
    DEFAULT(".RoutingActivity", R.mipmap.ic_launcher, R.string.app_name, IconCategory.ProtonVPN),
    DARK(".RoutingDark", R.mipmap.ic_launcher_dark, R.string.app_icon_name_dark, IconCategory.ProtonVPN),
    RETRO(".RoutingRetro", R.mipmap.ic_launcher_retro, R.string.app_icon_name_retro, IconCategory.ProtonVPN),
    WEATHER(".RoutingWeather", R.mipmap.ic_launcher_weather, R.string.app_icon_name_weather, IconCategory.Discreet),
    NOTES(".RoutingNotes", R.mipmap.ic_launcher_notes, R.string.app_icon_name_notes, IconCategory.Discreet),
    CALCULATOR(".RoutingCalculator", R.mipmap.ic_launcher_calculator, R.string.app_icon_name_calculator, IconCategory.Discreet);

    fun getComponentName(context: Context): ComponentName {
        val applicationContext = context.applicationContext
        return ComponentName(applicationContext, applicationContext.packageName + componentName)
    }
    enum class IconCategory {
        ProtonVPN,
        Discreet
    }
}