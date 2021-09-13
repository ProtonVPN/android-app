/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.withContext
import me.proton.core.util.kotlin.DispatcherProvider
import javax.inject.Inject

class InstalledAppsProvider @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val packageManager: PackageManager,
) {
    data class AppInfo(
        val packageName: String,
        val name: String,
        val icon: Drawable
    )

    suspend fun getInstalledInternetApps(): List<AppInfo> =
        withContext(dispatcherProvider.Io) {
            packageManager.getInstalledApplications(
                PackageManager.GET_META_DATA
            ).filter { appInfo ->
                (packageManager.checkPermission(Manifest.permission.INTERNET, appInfo.packageName)
                        == PackageManager.PERMISSION_GRANTED)
            }.map { appInfo ->
                AppInfo(
                    packageName = appInfo.packageName,
                    name = appInfo.loadLabel(packageManager).toString(),
                    icon = appInfo.loadIcon(packageManager)
                )
            }
        }

    /**
     * Maps package names to application labels. Package names that are not found (e.g. the app has
     * been uninstalled) are ignored, so the resulting list can have fewer elements than the input.
     */
    suspend fun getNamesOfInstalledApps(packages: List<String>): List<CharSequence> =
        withContext(dispatcherProvider.Io) {
            packages.mapNotNull { packageName ->
                try {
                    packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager)
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            }
        }
}
