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

package com.protonvpn.android.redesign.recents.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Transition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.VpnWeakSolidButton
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.ConnectIntentIcon
import com.protonvpn.android.redesign.base.ui.clickableWithDebounce
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentLabels
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.ui.home.FreeConnectionsInfoBottomSheet
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Immutable
data class VpnConnectionCardViewState(
    val cardLabel: CardLabel,
    @StringRes val mainButtonLabelRes: Int,
    val isConnectedOrConnecting: Boolean,
    val connectIntentViewState: ConnectIntentViewState,
    val canOpenConnectionPanel: Boolean,
    val canOpenFreeCountriesPanel: Boolean,
) {
    val canOpenPanel: Boolean = canOpenConnectionPanel || canOpenFreeCountriesPanel
}

data class CardLabel(@StringRes val cardLabelRes: Int, val isClickable: Boolean = false)

@Suppress("LongParameterList")
@Composable
fun VpnConnectionCard(
    viewState: VpnConnectionCardViewState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenConnectionPanel: () -> Unit,
    onOpenDefaultConnection: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    changeServerButton: (@Composable ColumnScope.() -> Unit)? = null,
    itemIdsTransition: Transition<ItemIds>? = null
) {
    var showsInfoDialog by rememberSaveable { mutableStateOf(false) }
    val openFreeCountriesInfoPanel = { showsInfoDialog = true }
    Column(
        modifier = modifier
            .animateContentSize()
    ) {
        ContainerLabelRow(
            viewState.cardLabel,
            onOpenDefaultConnection,
            Modifier.padding(vertical = 12.dp) // There's 4.dp padding on the help button.
        )
        val surfaceShape = ProtonTheme.shapes.large
        Surface(
            color = ProtonTheme.colors.backgroundSecondary,
            shape = surfaceShape,
            modifier = Modifier.border(1.dp, ProtonTheme.colors.separatorNorm, surfaceShape)
        ) {
            // The whole card can be clicked to open the panel but for accessibility this action is placed on the
            // chevron icon.
            val panelModifier = if (viewState.canOpenPanel) {
                val action = if (viewState.canOpenConnectionPanel) onOpenConnectionPanel else openFreeCountriesInfoPanel
                Modifier.clickableWithDebounce(action = action)
            } else {
                Modifier
            }
            Column(
                modifier = panelModifier.padding(16.dp),
            ) {
                AnimatedContent(
                    targetState = viewState.connectIntentViewState,
                    transitionSpec = { getTransitionSpec(itemIdsTransition) },
                    label = "connect intent"
                ) { targetState ->
                    Row(
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .heightIn(min = 42.dp)
                            .semantics(mergeDescendants = true) {},
                    ) {
                        with(targetState) {
                            val alignment = if (secondaryLabel != null) Alignment.Top else Alignment.CenterVertically
                            ConnectIntentIcon(primaryLabel, modifier = Modifier.align(alignment))
                            ConnectIntentLabels(
                                primaryLabel,
                                secondaryLabel,
                                serverFeatures,
                                isConnected = false,
                                primaryLabelStyle = ProtonTheme.typography.body1Medium.copy(fontSize = 18.sp),
                                secondaryLabelVerticalPadding = 2.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .align(alignment)
                                    .padding(start = 16.dp)
                            )
                        }
                        if (viewState.canOpenPanel) {
                             val iconRes =
                                 if (viewState.canOpenConnectionPanel) CoreR.drawable.ic_proton_chevron_up
                                 else CoreR.drawable.ic_proton_info_circle
                            val contentDescriptionRes =
                                if (viewState.canOpenConnectionPanel) R.string.connection_card_accessbility_label_connection_details
                                else R.string.connection_card_accessbility_label_free_connections
                            OpenPanelButton(
                                iconRes = iconRes,
                                onOpenPanel = if (viewState.canOpenConnectionPanel) onOpenConnectionPanel else openFreeCountriesInfoPanel,
                                clickLabel = stringResource(R.string.accessibility_action_open),
                                contentDescription = stringResource(contentDescriptionRes),
                                Modifier.align(Alignment.Top)
                            )
                        }
                    }
                }
                if (viewState.isConnectedOrConnecting) {
                    VpnWeakSolidButton(text = stringResource(viewState.mainButtonLabelRes), onClick = onDisconnect)
                } else {
                    VpnSolidButton(text = stringResource(viewState.mainButtonLabelRes), onClick = onConnect)
                }
                if (changeServerButton != null) {
                    Spacer(Modifier.height(8.dp))
                    changeServerButton()
                }
            }
        }
    }

    if (showsInfoDialog) {
        FreeConnectionsInfoBottomSheet(
            onDismissRequest = { showsInfoDialog = false }
        )
    }
}

@Composable
private fun OpenPanelButton(
    @DrawableRes iconRes: Int,
    onOpenPanel: () -> Unit,
    clickLabel: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Icon(
        painterResource(id = iconRes),
        tint = ProtonTheme.colors.iconWeak,
        contentDescription = contentDescription,
        modifier = modifier.semantics {
            role = Role.Button
            onClick(label = clickLabel) {
                onOpenPanel()
                true
            }
        }
    )
}

private fun AnimatedContentTransitionScope<ConnectIntentViewState>.getTransitionSpec(
    ids: Transition<ItemIds>?
): ContentTransform {
    if (ids == null) return EnterTransition.None togetherWith ExitTransition.None

    val isSameThingOrIdentical =
        initialState == targetState || ids.currentState.connectionCard == ids.targetState.connectionCard
    val fromRecents = ids.currentState.recents.contains(ids.targetState.connectionCard)
    val enter = when (!isSameThingOrIdentical && fromRecents) {
        true -> slideInVertically { height -> height } + fadeIn()
        else -> EnterTransition.None
    }
    return enter togetherWith ExitTransition.None
}

@Composable
private fun ContainerLabelRow(
    cardLabel: CardLabel,
    onLabelAction: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val dynamicModifier = if (onLabelAction != null && cardLabel.isClickable) {
        Modifier.clip(RoundedCornerShape(4.dp)).clickable { onLabelAction() }.then(modifier)
    } else {
        modifier
    }

    Row(
        modifier = dynamicModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(cardLabel.cardLabelRes),
            style = ProtonTheme.typography.body2Regular,
        )
        if (cardLabel.isClickable) {
            Icon(
                painterResource(id = CoreR.drawable.ic_proton_chevron_down_filled),
                contentDescription = stringResource(R.string.accessibility_external_link_suffix),
                modifier = Modifier.padding(start = 8.dp).size(16.dp)
            )
        }
    }
}

@Preview
@Composable
private fun VpnConnectionCardFreeUserPreview() {
    ProtonVpnPreview {
        val connectIntentState = ConnectIntentViewState(
            ConnectIntentPrimaryLabel.Fastest(CountryId.sweden, isSecureCore = false, isFree = true),
            ConnectIntentSecondaryLabel.FastestFreeServer(4),
            emptySet(),
        )
        val state = VpnConnectionCardViewState(
            CardLabel(R.string.connection_card_label_last_connected, false),
            mainButtonLabelRes = R.string.buttonConnect,
            isConnectedOrConnecting = false,
            connectIntentViewState = connectIntentState,
            canOpenConnectionPanel = false,
            canOpenFreeCountriesPanel = false,
        )
        VpnConnectionCard(
            viewState = state,
            onConnect = {},
            onDisconnect = {},
            onOpenConnectionPanel = {},
            onOpenDefaultConnection = {},
            modifier = Modifier
        )
    }
}
