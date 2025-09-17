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

package com.protonvpn.android.profiles.ui

import android.content.Context
import com.protonvpn.android.components.InstalledAppsProvider
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.ui.settings.LabeledItem
import com.protonvpn.android.ui.settings.getAppMetaData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AutoOpenAppInfoHelper @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val installedAppsProvider: InstalledAppsProvider,
    private val dispatchers: VpnDispatcherProvider,
    private val isTvCheck: IsTvCheck,
) {
    suspend fun getLaunchableAppsInfo(iconSizePx: Int) : List<LabeledItem> {
        val apps = installedAppsProvider.getInstalledInternetApps(true, isTvCheck())
        return installedAppsProvider.getAppInfos(iconSizePx, apps).map {
            LabeledItem(
                id = it.packageName,
                label = it.name,
                iconDrawable = it.icon.mutate().apply { setBounds(0, 0, iconSizePx, iconSizePx) }
            )
        }
    }

    suspend fun getAppInfo(packageName: String) : LabeledItem? =
        withContext(dispatchers.Io) {
            appContext.getAppMetaData(packageName)?.let {
                LabeledItem(packageName, it.label, iconDrawable = it.iconDrawable)
            }
        }
}