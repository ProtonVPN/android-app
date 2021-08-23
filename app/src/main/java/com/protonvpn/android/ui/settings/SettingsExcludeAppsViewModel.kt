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

package com.protonvpn.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import com.protonvpn.android.components.InstalledAppsProvider
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.ViewUtils.toPx
import com.protonvpn.android.utils.sortedByLocaleAware
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import me.proton.core.util.kotlin.DispatcherProvider
import javax.inject.Inject

class SettingsExcludeAppsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val installedAppsProvider: InstalledAppsProvider,
    private val userData: UserData
) : ViewModel() {

    sealed class ViewState {
        object Loading : ViewState()
        data class Content(
            val selectedApps: List<LabeledItem>,
            val availableApps: List<LabeledItem>
        ) : ViewState()
    }

    private val allApps: Flow<Map<String, LabeledItem>> = flow {
        val items = installedAppsProvider.getInstalledInternetApps().map {
            LabeledItem(
                id = it.packageName,
                label = it.name,
                iconDrawable = it.icon.mutate().apply { setBounds(0, 0, 24.toPx(), 24.toPx()) }
            )
        }
        emit(items.associateBy { it.id })
    }

    val viewState: Flow<ViewState> = combine(
        allApps,
        userData.splitTunnelAppsLiveData.asFlow()
    ) { all, selectedPackages ->
        val selectedApps = selectedPackages.mapNotNullTo(mutableSetOf()) { packageName ->
            all.getOrDefault(packageName, null)
        }
        val availableApps = all.values.filterNot { selectedApps.contains(it) }
        ViewState.Content(
            selectedApps.toList().sortedByLocaleAware { it.label },
            availableApps.toList().sortedByLocaleAware { it.label }
        ) as ViewState
    }
        .flowOn(dispatcherProvider.Comp)
        .onStart { emit(ViewState.Loading) }

    fun addAppToExcluded(item: LabeledItem) = userData.addAppToSplitTunnel(item.id)
    fun removeAppFromExcluded(item: LabeledItem) = userData.removeAppFromSplitTunnel(item.id)
}
