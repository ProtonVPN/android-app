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

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.ui.drawer.bugreport.DynamicReportActivity
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.openUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

            if (isDownloaded) {
                loaderState.value = LoaderState.Loaded
            } else {
                updateServerList()
            }
        }
    }

    val serverLoadingState = combine(
        loaderState,
        serverManager.hasAnyCountryFlow,
        serverManager.hasAnyGatewaysFlow,
    ) { loaderState, hasCountries, hasGateways ->
        when (loaderState) {
            is LoaderState.Error,
            LoaderState.Loading -> loaderState

            LoaderState.Loaded -> {
                if(!hasCountries && !hasGateways) LoaderState.Error.NoCountriesNoGateways
                else loaderState
            }
        }
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

    fun updateServerList() {
        viewModelScope.launch {
            loaderState.value = LoaderState.Loading

            val result = serverListUpdater.updateServerList(forceFreshUpdate = true)

            loaderState.value = if (result.isSuccess) {
                LoaderState.Loaded
            } else {
                LoaderState.Error.RequestFailed
            }
        }
    }

    sealed interface LoaderState {

        data object Loading : LoaderState

        data object Loaded : LoaderState

        sealed class Error(
            @StringRes val descriptionResId: Int,
            @StringRes val helpResId: Int?,
            val linkAnnotationAction: ((Context) -> Unit)?,
        ) : LoaderState {

            @StringRes
            val titleResId: Int = R.string.no_connections_title

            data object DisabledByAdmin : Error(
                descriptionResId = R.string.no_connections_description_no_servers,
                helpResId = R.string.no_connections_help_follow_instructions,
                linkAnnotationAction = { context -> context.openUrl(url = Constants.URL_ENABLE_VPN_CONNECTION) },
            )

            data object NoCountriesNoGateways : Error(
                descriptionResId = R.string.no_connections_description_no_servers,
                helpResId = null,
                linkAnnotationAction = null,
            )

            data object RequestFailed : Error(
                descriptionResId = R.string.no_connections_description_loading_error,
                helpResId = R.string.no_connections_help_contact_us,
                linkAnnotationAction = { context ->
                    context.startActivity(Intent(context, DynamicReportActivity::class.java))
                },
            )

        }

    }

}
