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
package com.protonvpn.android.redesign.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.ui.home.ServerListUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerLoadingViewModel @Inject constructor(
    serverManager: ServerManager2,
    private val serverListUpdater: ServerListUpdater,
) : ViewModel() {
    private val loaderState = MutableStateFlow<LoaderState>(LoaderState.Loading)

    init {
        viewModelScope.launch {
            val isDownloaded = serverManager.isDownloadedAtLeastOnceFlow.first()
            if (!isDownloaded) {
                updateServerList()
            }
        }
    }

    val serverLoadingState = combine(
        serverManager.isDownloadedAtLeastOnceFlow,
        loaderState
    ) { isDownloaded, loaderState ->
        if (isDownloaded) {
            return@combine LoaderState.Loaded
        } else {
            loaderState
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = null
    )

    fun updateServerList() {
        viewModelScope.launch {
            loaderState.value = LoaderState.Loading
            val result = serverListUpdater.updateServerList()
            loaderState.value = if (result.isSuccess) LoaderState.Loaded else LoaderState.Error
        }
    }

    sealed class LoaderState {
        object Loading : LoaderState()
        object Loaded : LoaderState()
        object Error : LoaderState()
    }
}
