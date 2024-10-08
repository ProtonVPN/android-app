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

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.protonvpn.android.R
import com.protonvpn.android.annotations.ProtonVpnTestPreview
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.ui.CardLabel
import com.protonvpn.android.redesign.recents.ui.VpnConnectionCard
import com.protonvpn.android.redesign.recents.ui.VpnConnectionCardViewState
import com.protonvpn.android.redesign.vpn.ui.ChangeServerViewState
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.ui.home.vpn.ChangeServerButton

private val freeConnection = ConnectionData(true)

@Composable
@ProtonVpnTestPreview
fun DisconnectedVpnConnectionCardFree() {
    VpnConnectionCardPreview(
        viewState = freeConnection.disconnectedUser,
        changeServerButton = null
    )
}

@Composable
@ProtonVpnTestPreview
fun ConnectedVpnConnectionCardFree() {
    VpnConnectionCardPreview(
        viewState = freeConnection.connectedUser,
        changeServerButton = changeServerButtonLocked
    )
}

@Composable
private fun VpnConnectionCardPreview(
    viewState: VpnConnectionCardViewState,
    changeServerButton: (@Composable ColumnScope.() -> Unit)? = null
) {
    ProtonVpnPreview {
        VpnConnectionCard(
            viewState = viewState,
            onConnect = {},
            onDisconnect = {},
            onOpenConnectionPanel = {},
            onOpenDefaultConnection = {},
            modifier = Modifier,
            changeServerButton = changeServerButton
        )
    }
}

private val changeServerButtonLocked: @Composable ColumnScope.() -> Unit = @Composable {
    ChangeServerButton(
        ChangeServerViewState.Locked(60, 120, false),
        {}, {}
    )
}

private class ConnectionData(isFree: Boolean) {
    private val countryId = CountryId("NL")

    private val connectionIntentDisconnected = ConnectIntentViewState(
        ConnectIntentPrimaryLabel.Fastest(
            CountryId.sweden,
            isSecureCore = false,
            isFree = isFree
        ),
        if (isFree) ConnectIntentSecondaryLabel.FastestFreeServer(5) else null,
        emptySet(),
    )

    private val connectionIntentConnected = ConnectIntentViewState(
        ConnectIntentPrimaryLabel.Fastest(
            CountryId.sweden,
            isSecureCore = false,
            isFree = isFree
        ),
        ConnectIntentSecondaryLabel.Country(countryId),
        emptySet(),
    )

    val disconnectedUser = VpnConnectionCardViewState(
        CardLabel(R.string.connection_card_label_free_connection, false),
        mainButtonLabelRes = R.string.buttonConnect,
        isConnectedOrConnecting = false,
        connectIntentViewState = connectionIntentDisconnected,
        canOpenConnectionPanel = false,
        canOpenFreeCountriesPanel = isFree,
    )

    val connectedUser = VpnConnectionCardViewState(
        CardLabel(R.string.connection_card_label_connected, false),
        mainButtonLabelRes = R.string.connected,
        isConnectedOrConnecting = true,
        connectIntentViewState = connectionIntentConnected,
        canOpenConnectionPanel = true,
        canOpenFreeCountriesPanel = false
    )
}