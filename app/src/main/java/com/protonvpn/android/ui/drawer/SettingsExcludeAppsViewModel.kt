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

package com.protonvpn.android.ui.drawer

import android.Manifest
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.ViewUtils.toPx
import com.protonvpn.android.utils.sortedByLocaleAware
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import me.proton.core.util.kotlin.DispatcherProvider
import javax.inject.Inject

// TODO: create an "InstalledAppsProvider" that can be mocked in tests?
class SettingsExcludeAppsViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val packageManager: PackageManager,
    private val userData: UserData
) : ViewModel() {

    data class ViewState(
        val selectedApps: List<LabeledItem>,
        val availableApps: List<LabeledItem>
    )

    private val allApps = flow {
        emit(getInstalledInternetAppsSync().associateBy { it.id })
    }.flowOn(dispatcherProvider.Io)

    val viewState = combine(
        allApps,
        userData.splitTunnelAppsLiveData.asFlow()
    ) { all, selectedPackages ->
        val selectedApps = selectedPackages.mapNotNullTo(mutableSetOf()) { packageName ->
            all.getOrDefault(packageName, null)
        }
        val availableApps = all.values.filterNot { selectedApps.contains(it) }
        ViewState(
            selectedApps.toList().sortedByLocaleAware { it.label },
            availableApps.toList().sortedByLocaleAware { it.label }
        )
    }.flowOn(dispatcherProvider.Comp)

    fun addAppToExcluded(item: LabeledItem) = userData.addAppToSplitTunnel(item.id)
    fun removeAppFromExcluded(item: LabeledItem) = userData.removeAppFromSplitTunnel(item.id)

    private fun getInstalledInternetAppsSync() =
        packageManager.getInstalledApplications(
            PackageManager.GET_META_DATA
        ).filter { appInfo ->
            (packageManager.checkPermission(Manifest.permission.INTERNET, appInfo.packageName)
                    == PackageManager.PERMISSION_GRANTED)
        }.map { appInfo ->
            LabeledItem(
                id = appInfo.packageName,
                label = appInfo.loadLabel(packageManager).toString(),
                iconDrawable = appInfo.loadIcon(packageManager).mutate().apply {
                    setBounds(0, 0, 24.toPx(), 24.toPx())
                }
            )
        }
}
