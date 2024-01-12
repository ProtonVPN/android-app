/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.redesign.uicatalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.ProtonSnackbarType
import com.protonvpn.android.redesign.base.ui.showSnackbar
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.recents.ui.VpnConnectionCard
import com.protonvpn.android.redesign.recents.ui.VpnConnectionCardViewState
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.redesign.recents.ui.VpnConnectionState
import com.protonvpn.android.redesign.vpn.ui.ChangeServerViewState
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.ui.home.vpn.ChangeServerButton
import kotlinx.coroutines.launch
import me.proton.core.compose.component.VerticalSpacer
import java.util.EnumSet

class ConnectionCardSample : SampleScreen("Connection card", "connection_card") {
    @Composable
    override fun Content(modifier: Modifier, snackbarHostState: SnackbarHostState) {
        Column(
            modifier = modifier
                .padding(16.dp)
        ) {
            val coroutineScope = rememberCoroutineScope()
            val connectionCardModifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
            val openAction: () -> Unit = {
                coroutineScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar("Open panel", type = ProtonSnackbarType.SUCCESS)
                }
            }

            VerticalSpacer(height = 16.dp)
            SamplesSectionLabel(label = "Connection cards in various sample states")
            VpnConnectionCard(
                VpnConnectionCardViewState(
                    cardLabelRes = R.string.connection_card_label_last_connected,
                    mainButtonLabelRes = R.string.buttonConnect,
                    isConnectedOrConnecting = false,
                    connectIntentViewState = ConnectIntentViewState(
                        primaryLabel = ConnectIntentPrimaryLabel.Country(CountryId.fastest, null),
                        secondaryLabel = null,
                        serverFeatures = emptySet()
                    ),
                    canOpenConnectionPanel = false,
                    canOpenFreeCountriesPanel = false,
                ),
                onConnect = {},
                onDisconnect = {},
                onOpenConnectionPanel = openAction,
                modifier = connectionCardModifier
            )

            VpnConnectionCard(
                VpnConnectionCardViewState(
                    cardLabelRes = R.string.connection_card_label_connected,
                    mainButtonLabelRes = R.string.disconnect,
                    isConnectedOrConnecting = true,
                    connectIntentViewState = ConnectIntentViewState(
                        primaryLabel = ConnectIntentPrimaryLabel.Country(CountryId.fastest, null),
                        secondaryLabel = ConnectIntentSecondaryLabel.Country(CountryId("pl")),
                        serverFeatures = emptySet()
                    ),
                    canOpenConnectionPanel = true,
                    canOpenFreeCountriesPanel = false,
                ),
                onConnect = {},
                onDisconnect = {},
                onOpenConnectionPanel = openAction,
                modifier = connectionCardModifier
            )

            VpnConnectionCard(
                VpnConnectionCardViewState(
                    cardLabelRes = R.string.connection_card_label_connected,
                    mainButtonLabelRes = R.string.disconnect,
                    isConnectedOrConnecting = true,
                    connectIntentViewState = ConnectIntentViewState(
                        primaryLabel = ConnectIntentPrimaryLabel.Country(CountryId.fastest, null),
                        secondaryLabel = ConnectIntentSecondaryLabel.SecureCore(
                            exit = CountryId("pl"),
                            entry = CountryId("se")
                        ),
                        serverFeatures = EnumSet.of(ServerFeature.P2P),
                    ),
                    canOpenConnectionPanel = true,
                    canOpenFreeCountriesPanel = false,
                ),
                onConnect = {},
                onDisconnect = {},
                onOpenConnectionPanel = openAction,
                modifier = connectionCardModifier
            )

            VpnConnectionCard(
                VpnConnectionCardViewState(
                    cardLabelRes = R.string.connection_card_label_connected,
                    mainButtonLabelRes = R.string.disconnect,
                    isConnectedOrConnecting = true,
                    connectIntentViewState = ConnectIntentViewState(
                        primaryLabel = ConnectIntentPrimaryLabel.Country(
                            exitCountry = CountryId("lt"),
                            entryCountry = CountryId("ch"),
                        ),
                        secondaryLabel = ConnectIntentSecondaryLabel.SecureCore(exit = null, entry = CountryId("ch")),
                        serverFeatures = EnumSet.of(ServerFeature.P2P)
                    ),
                    canOpenConnectionPanel = true,
                    canOpenFreeCountriesPanel = false,
                ),
                onConnect = {},
                onDisconnect = {},
                onOpenConnectionPanel = openAction,
                modifier = connectionCardModifier
            )

            val changeServerButton: @Composable ColumnScope.() -> Unit = @Composable {
                ChangeServerButton(
                    ChangeServerViewState.Locked(60, 120, false),
                    {}, {}
                )
            }
            VpnConnectionCard(
                VpnConnectionCardViewState(
                    cardLabelRes = R.string.connection_card_label_connected,
                    mainButtonLabelRes = R.string.disconnect,
                    isConnectedOrConnecting = true,
                    connectIntentViewState = ConnectIntentViewState(
                        primaryLabel = ConnectIntentPrimaryLabel.Country(
                            exitCountry = CountryId("lt"),
                            entryCountry = CountryId("ch"),
                        ),
                        secondaryLabel = ConnectIntentSecondaryLabel.SecureCore(exit = null, entry = CountryId("ch")),
                        serverFeatures = EnumSet.of(ServerFeature.P2P)
                    ),
                    canOpenConnectionPanel = true,
                    canOpenFreeCountriesPanel = false,
                ),
                changeServerButton = changeServerButton,
                onConnect = {},
                onDisconnect = {},
                onOpenConnectionPanel = openAction,
                modifier = connectionCardModifier
            )
        }
    }
}
