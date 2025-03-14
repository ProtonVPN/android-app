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

import android.content.ComponentName
import android.content.Context
import android.net.VpnService
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.updateAll
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.WidgetStateUpdate
import com.protonvpn.android.redesign.recents.ui.RecentItemViewState
import com.protonvpn.android.redesign.recents.usecases.RecentsListViewStateFlow
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.ui.settings.AppIconManager
import com.protonvpn.android.utils.flatMapLatestNotNull
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.widget.data.WidgetTracker
import com.protonvpn.android.widget.ui.ProtonVpnGlanceWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
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
    vpnStatusProviderUi: dagger.Lazy<VpnStatusProviderUI>,
    recentsListViewStateFlow: dagger.Lazy<RecentsListViewStateFlow>,
    currentUser: dagger.Lazy<CurrentUser>,
    appIconManager: dagger.Lazy<AppIconManager>,
    widgetTracker: WidgetTracker,
) {

    private val vpnStatusFlow by lazy {
        vpnStatusProviderUi.get().uiStatus
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
                    is VpnState.Error ->
                        if (it.state.isFinal) WidgetVpnStatus.Disconnected
                        else WidgetVpnStatus.Connecting
                }
            }
            .distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val widgetViewStateFlow = widgetTracker.haveWidgets.flatMapLatestNotNull { haveWidgets ->
        if (!haveWidgets)
            flowOf(null)
        else combine(
            currentUser.get().vpnUserFlow,
            appIconManager.get().currentIconData.map { it.getComponentName(appContext) },
        ) { vpnUser, mainComponentName ->
            vpnUser to mainComponentName
        }.flatMapLatest { (vpnUser, mainComponentName) ->
            val mainActivityAction = actionStartActivity(mainComponentName)
            if (vpnUser == null)
                flowOf(WidgetViewState.NeedLogin(mainActivityAction))
            else combine(
                vpnStatusFlow,
                recentsListViewStateFlow.get()
            ) { vpnStatus, recents ->
                val haveVpnPermission = VpnService.prepare(appContext) == null
                val widgetRecents = recents.recents.map {
                    val canConnectInBackground = haveVpnPermission && !it.isProfileAutoOpen
                    WidgetRecent(
                        actionConnect(canConnectInBackground, mainComponentName, recentId = it.id),
                        it.connectIntent
                    )
                }
                val canCardConnectInBackground = haveVpnPermission && !recents.recents.first().isProfileAutoOpen
                val cardAction =
                    if (vpnStatus.isActionConnect) actionConnect(canCardConnectInBackground, mainComponentName)
                    else actionSendBroadcast(WidgetActionBroadcastReceiver.intentDisconnect(appContext))
                WidgetViewState.LoggedIn(
                    recents.connectionCard.connectIntentViewState,
                    cardAction,
                    vpnStatus,
                    widgetRecents,
                    mainActivityAction,
                )
            }
        }
    }.stateIn(
        mainScope,
        SharingStarted.WhileSubscribed(),
        null
    )

    private val WidgetVpnStatus.isActionConnect get() = when(this) {
        WidgetVpnStatus.Connected,
        WidgetVpnStatus.Connecting,
        WidgetVpnStatus.WaitingForNetwork -> false
        WidgetVpnStatus.Disconnected -> true
    }

    private fun actionConnect(
        canConnectInBackground: Boolean,
        mainComponentName: ComponentName,
        recentId: Long? = null
    ) = if (canConnectInBackground) {
        actionSendBroadcast(WidgetActionBroadcastReceiver.intentConnect(appContext, recentId))
    } else {
        actionStartActivity(
            mainComponentName,
            WidgetActionHandler.connectActionParameters(recentId),
        )
    }

    fun start() {
        widgetViewStateFlow
            .filterNotNull()
            .onEach {
                ProtonVpnGlanceWidget().updateAll(appContext)
                ProtonLogger.log(WidgetStateUpdate)
            }
            .launchIn(mainScope)
    }
}

private val RecentItemViewState.isProfileAutoOpen get() =
    (connectIntent.primaryLabel as? ConnectIntentPrimaryLabel.Profile)?.isAutoOpen == true