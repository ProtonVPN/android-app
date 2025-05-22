/*
 *
 *  * Copyright (c) 2023. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.android.tests.home

import android.os.SystemClock
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import com.protonvpn.android.R
import com.protonvpn.android.annotations.ProtonVpnTestPreview
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.home_screen.ui.ConnectionCardComponent
import com.protonvpn.android.redesign.home_screen.ui.HomeView
import com.protonvpn.android.redesign.home_screen.ui.RecentsComponent
import com.protonvpn.android.redesign.home_screen.ui.UpsellCarouselState
import com.protonvpn.android.redesign.recents.ui.CardLabel
import com.protonvpn.android.redesign.recents.ui.RecentsExpandState
import com.protonvpn.android.redesign.recents.ui.VpnConnectionCardViewState
import com.protonvpn.android.redesign.recents.usecases.RecentsListViewState
import com.protonvpn.android.redesign.vpn.ui.ChangeServerViewState
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.redesign.vpn.ui.LocationText
import com.protonvpn.android.redesign.vpn.ui.StatusBanner
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewState
import kotlinx.coroutines.flow.MutableSharedFlow

@ProtonVpnTestPreview
@Composable
fun FullHomeScreenDisconnected() {
    ProtonVpnPreview {
        HomeView(
            vpnState = VpnStatusViewState.Disabled(LocationText("Lithuania", "23.13.43.24")),
            // Map state is not loaded properly in preview
            mapState = null,
            dialogState = null,
            upsellCarouselState = UpsellCarouselState(1500, 1600),
            elapsedRealtimeClock = SystemClock::elapsedRealtime,
            onDismissDialog = {},
            onNetshieldValueChanged = {},
            onDisableCustomDns = {},
            bottomPromoComponent = null,
            prominentPromoComponent = null,
            connectionCardComponent = HomeViewData.connectionCardDisconnectedComponent,
            snackbarHostState = SnackbarHostState(),
            recentsComponent = HomeViewData.recentsComponentDisconnected,
            widgetAdoptionComponent = null
        )
    }
}

@ProtonVpnTestPreview
@Composable
fun FullHomeScreenConnected() {
    ProtonVpnPreview {
        HomeView(
            vpnState = VpnStatusViewState.Connected(
                isSecureCoreServer = false,
                banner = StatusBanner.UnwantedCountry
            ),
            // Map state is not loaded properly in preview
            mapState = null,
            dialogState = null,
            upsellCarouselState = UpsellCarouselState(1500, 1600),
            elapsedRealtimeClock = SystemClock::elapsedRealtime,
            onDismissDialog = {},
            onNetshieldValueChanged = {},
            onDisableCustomDns = {},
            bottomPromoComponent = null,
            prominentPromoComponent = null,
            connectionCardComponent = HomeViewData.connectionCardConnectedComponent,
            snackbarHostState = SnackbarHostState(),
            recentsComponent = HomeViewData.recentsComponentConnected,
            widgetAdoptionComponent = null
        )
    }
}

private object HomeViewData {
    val connectionCardDisconnectedComponent = getConnectionCardComponent()

    val connectionCardConnectedComponent = getConnectionCardComponent(
        ChangeServerViewState.Locked(
            remainingTimeInSeconds = 154,
            totalCooldownSeconds = 600,
            isFullLocked = true
        )
    )

    val vpnConnectionCardDisconnectedViewState = VpnConnectionCardViewState(
        CardLabel(R.string.connection_card_label_free_connection, false),
        mainButtonLabelRes = R.string.connect,
        isConnectedOrConnecting = false,
        connectIntentViewState = ConnectIntentViewState(
            ConnectIntentPrimaryLabel.Fastest(CountryId.fastest, false, false),
            serverFeatures = setOf(),
            secondaryLabel = ConnectIntentSecondaryLabel.FastestFreeServer(5)
        ),
        canOpenConnectionPanel = false,
        canOpenFreeCountriesPanel = true
    )

    val vpnConnectionCardConnectedViewState = VpnConnectionCardViewState(
        CardLabel(R.string.connection_card_label_connected, false),
        mainButtonLabelRes = R.string.connected,
        isConnectedOrConnecting = true,
        connectIntentViewState = ConnectIntentViewState(
            ConnectIntentPrimaryLabel.Fastest(CountryId.fastest, false, false),
            serverFeatures = setOf(),
            secondaryLabel = null
        ),
        canOpenConnectionPanel = true,
        canOpenFreeCountriesPanel = false
    )

    val recentsComponentDisconnected: RecentsComponent = getRecentsComponent(
        vpnConnectionCardDisconnectedViewState,
        -900,
        200
    )

    val recentsComponentConnected: RecentsComponent = getRecentsComponent(
        vpnConnectionCardConnectedViewState,
        -750,
        200
    )

    private fun getConnectionCardComponent(changeServerViewState: ChangeServerViewState? = null): ConnectionCardComponent =
        ConnectionCardComponent(
            onConnect = {},
            onDisconnect = {},
            onConnectionCardClick = {},
            onChangeServerClick = {},
            onChangeServerUpgradeButtonShown = {},
            changeServerState = changeServerViewState
        )

    private fun getRecentsListViewState(vpnConnectionCardViewState: VpnConnectionCardViewState): RecentsListViewState =
        RecentsListViewState(
            connectionCard = vpnConnectionCardViewState,
            recents = listOf(),
            connectionCardRecentId = null
        )

    private fun getRecentsComponent(
        vpnConnectionCardViewState: VpnConnectionCardViewState,
        recentsPeekHeight: Int,
        recentsListHeight: Int
    ): RecentsComponent {
        val recentsExpandState = RecentsExpandState()
        recentsExpandState.setPeekHeight(recentsPeekHeight)
        recentsExpandState.setListHeight(recentsListHeight)
        return RecentsComponent(
            recentsViewState = mutableStateOf(getRecentsListViewState(vpnConnectionCardViewState)),
            onEventCollapseRecentsConsumed = {},
            onRecentClickedAction = {},
            onRecentRemove = {},
            onRecentPinToggle = {},
            eventCollapseRecents = MutableSharedFlow(),
            recentsExpandState = recentsExpandState
        )
    }
}
