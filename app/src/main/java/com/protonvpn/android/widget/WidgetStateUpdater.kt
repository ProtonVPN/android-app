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

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.redesign.recents.usecases.RecentsListViewStateFlow
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.widget.ui.ProtonVpnGlanceWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetStateUpdater @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val mainScope: CoroutineScope,
    vpnStatusProviderUi: VpnStatusProviderUI,
    recentsListViewStateFlow: RecentsListViewStateFlow,
    currentUser: CurrentUser,
) {

    private val vpnStatusFlow = vpnStatusProviderUi.uiStatus
        .map {
            when (it.state) {
                VpnState.Disabled,
                VpnState.Disconnecting -> WidgetVpnStatus.Disconnected
                VpnState.Connecting,
                VpnState.Reconnecting,
                VpnState.ScanningPorts,
                VpnState.CheckingAvailability -> WidgetVpnStatus.Connecting
                VpnState.Connected -> WidgetVpnStatus.Connected
                VpnState.WaitingForNetwork -> WidgetVpnStatus.WaitingForNetwork
                is VpnState.Error -> WidgetVpnStatus.Error
            }
        }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val widgetViewStateFlow: StateFlow<WidgetViewState?> = currentUser.vpnUserFlow.flatMapLatest { vpnUser ->
        if (vpnUser == null)
            flowOf(WidgetViewState.NeedLogin)
        else combine(
            vpnStatusFlow,
            recentsListViewStateFlow
        ) { vpnStatus, recents ->
            val widgetRecents = recents.recents.map {
                WidgetRecent(it.id, it.connectIntent)
            }
            WidgetViewState.LoggedIn(recents.connectionCard.connectIntentViewState, vpnStatus, widgetRecents)
        }
    }.stateIn(
        mainScope,
        SharingStarted.WhileSubscribed(),
        null
    )

    fun start() {
        widgetViewStateFlow
            .onEach { ProtonVpnGlanceWidget().updateAll(appContext) }
            .launchIn(mainScope)
    }
}