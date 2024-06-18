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

package com.protonvpn.android.ui.settings

import com.protonvpn.android.components.InstalledAppsProvider
import com.protonvpn.android.utils.ViewUtils.toPx
import com.protonvpn.android.utils.sortedByLocaleAware
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.proton.core.util.kotlin.DispatcherProvider

private const val APP_ICON_SIZE_DP = 24

class SplitTunnelingAppsViewModelHelper(
    viewModelScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val installedAppsProvider: InstalledAppsProvider,
    private val selectedPackages: Flow<Set<String>>,
    private val forTv: Boolean,
) {

    sealed class SystemAppsState {
        class NotLoaded(val packageNames: List<String>) : SystemAppsState()
        class Loading(val packageNames: List<String>) : SystemAppsState()
        class Content(val apps: List<LabeledItem>) : SystemAppsState()
    }

    sealed class ViewState {
        object Loading : ViewState()
        data class Content(
            val selectedApps: List<LabeledItem>,
            val availableRegularApps: List<LabeledItem>,
            val availableSystemApps: SystemAppsState
        ) : ViewState()
    }

    private val shouldLoadSystemApps = MutableStateFlow(false)

    private val regularAppPackages = flow {
        emit(installedAppsProvider.getInstalledInternetApps(true, forTv))
    }.shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    private val regularApps = regularAppPackages.map { packageNames ->
        loadApps(packageNames)
    }.shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    private val systemAppPackages = flow {
        emit(installedAppsProvider.getInstalledInternetApps(false, forTv))
    }.shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    private val selectedNonRegularApps = combine(
        selectedPackages, regularAppPackages
    ) { selected, regular ->
        val loadedSystemApps = systemAppsState.first().getApps()
        val packageNames = selected - regular
        val packagesToLoad = packageNames.filterNot { loadedSystemApps.containsWithId(it) }

        // Prefer already loaded
        loadedSystemApps.filter { it.id in packageNames } + loadApps(packagesToLoad)
    }.shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val systemAppsState: Flow<SystemAppsState> = shouldLoadSystemApps.flatMapLatest { load ->
        if (load) {
            flow<SystemAppsState> {
                val packageNames = systemAppPackages.first()
                emit(SystemAppsState.Loading(packageNames))
                emit(SystemAppsState.Content(loadApps(packageNames)))
            }
        } else {
            systemAppPackages.map { SystemAppsState.NotLoaded(it) }
        }
    }.shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    val viewState: StateFlow<ViewState> = combine(
        regularApps,
        systemAppsState,
        selectedPackages,
        selectedNonRegularApps
    ) { regularApps, systemAppsState, selectedPackages, selectedNonRegularApps ->
        val allAppsByPackage =
            (regularApps.toSet() + systemAppsState.getApps() + selectedNonRegularApps).associateBy { it.id }
        val selectedApps = selectedPackages.mapNotNullTo(mutableSetOf()) { packageName ->
            allAppsByPackage.getOrDefault(packageName, null)
        }
        val availableRegularApps = regularApps.filterNot { selectedApps.containsWithId(it.id) }
        val availableSystemAppsState = when (systemAppsState) {
            is SystemAppsState.Loading ->
                SystemAppsState.Loading(systemAppsState.packageNames.filterNot { selectedPackages.contains(it) })
            is SystemAppsState.NotLoaded ->
                SystemAppsState.NotLoaded(systemAppsState.packageNames.filterNot { selectedPackages.contains(it) })
            is SystemAppsState.Content ->
                SystemAppsState.Content(
                    systemAppsState.apps.filterNot { selectedApps.containsWithId(it.id) }
                        .sortedByLocaleAware { it.label }
                )
        }
        ViewState.Content(
            selectedApps.toList().sortedByLocaleAware { it.label },
            availableRegularApps.toList().sortedByLocaleAware { it.label },
            availableSystemAppsState
        )
    }
        .flowOn(dispatcherProvider.Comp)
        .stateIn(viewModelScope, SharingStarted.Lazily, ViewState.Loading)

    fun toggleLoadSystemApps() {
        shouldLoadSystemApps.update { !it }
    }

    private suspend fun loadApps(packageNames: List<String>): List<LabeledItem> {
        if (packageNames.isEmpty()) return emptyList()

        val iconSizePx = APP_ICON_SIZE_DP.toPx()
        return installedAppsProvider.getAppInfos(iconSizePx, packageNames).map {
            LabeledItem(
                id = it.packageName,
                label = it.name,
                iconDrawable = it.icon.mutate().apply { setBounds(0, 0, iconSizePx, iconSizePx) }
            )
        }
    }

    private fun SystemAppsState.getApps(): List<LabeledItem> =
        if (this is SystemAppsState.Content) {
            apps
        } else {
            emptyList()
        }

    private fun Iterable<LabeledItem>.containsWithId(id: String): Boolean {
        return find { it.id == id } != null
    }
}
