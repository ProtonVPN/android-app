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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
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
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.VpnThumbFeedbackButton
import com.protonvpn.android.base.ui.VpnWeakSolidButton
import com.protonvpn.android.base.ui.clickableNoMultiClick
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.ConnectIntentIcon
import com.protonvpn.android.redesign.recents.usecases.ConnectionFeedback
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentLabels
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.ui.home.FreeConnectionsInfoBottomSheet
import kotlinx.coroutines.delay
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

sealed interface CardLabel {

    data class ConnectionStatus(
        @StringRes val labelResId: Int,
        val showConnectionFeedback: Boolean = false,
        val onConnectionFeedbackShown: () -> Unit = {},
        val onConnectionFeedbackProvided: (ConnectionFeedback) -> Unit = {},
    ) : CardLabel

    data object DefaultConnection : CardLabel

}

@Suppress("LongParameterList")
@Composable
fun VpnConnectionCard(
    viewState: VpnConnectionCardViewState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenConnectionPanel: () -> Unit,
    onOpenDefaultConnection: () -> Unit,
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
            modifier = Modifier.padding(vertical = 8.dp),
            cardLabel = viewState.cardLabel,
            onDefaultConnectionClick = onOpenDefaultConnection,
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
                Modifier.clickableNoMultiClick(action = action)
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
                val connectButtonModifier = Modifier.testTag("connectCardButton")
                if (viewState.isConnectedOrConnecting) {
                    val text = stringResource(viewState.mainButtonLabelRes)
                    VpnWeakSolidButton(text = text, onClick = onDisconnect, modifier = connectButtonModifier)
                } else {
                    val text = stringResource(viewState.mainButtonLabelRes)
                    VpnSolidButton(text = text, onClick = onConnect, modifier = connectButtonModifier)
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
    onDefaultConnectionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (cardLabel) {
        is CardLabel.ConnectionStatus -> {
            VpnConnectionStatusLabel(
                modifier = modifier,
                cardLabel = cardLabel,
            )
        }

        CardLabel.DefaultConnection -> {
            VpnDefaultConnectionLabel(
                modifier = modifier,
                onClick = onDefaultConnectionClick,
            )
        }
    }
}

@Composable
private fun VpnConnectionStatusLabel(
    cardLabel: CardLabel.ConnectionStatus,
    modifier: Modifier = Modifier,
) {
    var animateConnectionFeedbackOnHide by remember { mutableStateOf(value = false) }

    if (cardLabel.showConnectionFeedback) {
        LaunchedEffect(key1 = Unit) {
            delay(1_500) // Make sure the user had a chance to see it.

            cardLabel.onConnectionFeedbackShown()
        }
    }

    val statusEnterTransition by remember {
        derivedStateOf {
            if (animateConnectionFeedbackOnHide) {
                fadeIn(
                    animationSpec = tween(
                        durationMillis = 200,
                        delayMillis = 1300,
                        easing = EaseIn,
                    )
                )
            } else {
                EnterTransition.None
            }
        }
    }

    val feedbackExitTransition by remember {
        derivedStateOf {
            if (animateConnectionFeedbackOnHide) {
                fadeOut(
                    animationSpec = tween(
                        durationMillis = 200,
                        delayMillis = 1100,
                        easing = EaseIn,
                    )
                )
            } else {
                ExitTransition.None
            }
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        AnimatedVisibility(
            visible = !cardLabel.showConnectionFeedback,
            enter = statusEnterTransition,
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = 200,
                    delayMillis = 600,
                    easing = EaseIn,
                )
            ),
        ) {
            Text(
                modifier = Modifier.padding(vertical = 16.dp),
                text = stringResource(id = cardLabel.labelResId),
                style = ProtonTheme.typography.body2Regular,
            )
        }

        AnimatedVisibility(
            visible = cardLabel.showConnectionFeedback,
            enter = EnterTransition.None,
            exit = feedbackExitTransition,
        ) {
            VpnConnectionFeedbackRequest(
                modifier = Modifier.fillMaxWidth(),
                transition = transition,
                onConnectionFeedbackClick = { connectionFeedback ->
                    animateConnectionFeedbackOnHide = true

                    cardLabel.onConnectionFeedbackProvided(connectionFeedback)
                },
            )
        }
    }
}

@Composable
private fun VpnConnectionFeedbackRequest(
    transition: Transition<EnterExitState>,
    onConnectionFeedbackClick: (ConnectionFeedback) -> Unit,
    modifier: Modifier = Modifier,
    enterAnimationDelayMillis: Int = 700,
) {
    val feedbackButtonModifier = remember { Modifier.size(size = 48.dp) }

    var isNegativeFeedbackPlaying by remember { mutableStateOf(value = false) }

    var isPositiveFeedbackPlaying by remember { mutableStateOf(value = false) }

    val isFeedbackButtonEnabled by remember {
        derivedStateOf { !isNegativeFeedbackPlaying && !isPositiveFeedbackPlaying }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        VpnConnectionFeedbackStaggeredEntrance(
            transition = transition,
            enterDelayMillis = enterAnimationDelayMillis,
        ) {
            Text(
                text = stringResource(id = R.string.connection_card_label_connection_feedback),
                style = ProtonTheme.typography.body2Regular,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VpnConnectionFeedbackStaggeredEntrance(
                transition = transition,
                enterDelayMillis = enterAnimationDelayMillis + 50,
                verticalOffsetDivider = 4,
            ) {
                VpnThumbFeedbackButton(
                    modifier = feedbackButtonModifier,
                    isPositive = false,
                    isEnabled = isFeedbackButtonEnabled,
                    isPlaying = isNegativeFeedbackPlaying,
                    onClick = {
                        isNegativeFeedbackPlaying = true

                        onConnectionFeedbackClick(ConnectionFeedback.Negative)
                    },
                )
            }
            VpnConnectionFeedbackStaggeredEntrance(
                transition = transition,
                enterDelayMillis = enterAnimationDelayMillis + 75,
            ) {
                VerticalDivider(
                    modifier = Modifier.height(height = 16.dp),
                    color = ProtonTheme.colors.separatorNorm,
                )
            }

            VpnConnectionFeedbackStaggeredEntrance(
                transition = transition,
                enterDelayMillis = enterAnimationDelayMillis + 100,
                verticalOffsetDivider = 4,
            ) {
                VpnThumbFeedbackButton(
                    modifier = feedbackButtonModifier,
                    isPositive = true,
                    isEnabled = isFeedbackButtonEnabled,
                    isPlaying = isPositiveFeedbackPlaying,
                    onClick = {
                        isPositiveFeedbackPlaying = true

                        onConnectionFeedbackClick(ConnectionFeedback.Positive)
                    },
                )
            }
        }
    }
}

@Composable
fun VpnConnectionFeedbackStaggeredEntrance(
    transition: Transition<EnterExitState>,
    enterDurationMillis: Int = 200,
    enterDelayMillis: Int = 0,
    verticalOffsetDivider: Int = 2,
    isInversed: Boolean = false,
    content: @Composable () -> Unit,
) {
    val verticalOffsetMultiplier = if (isInversed) -1 else 1

    transition.AnimatedVisibility(
        visible = { it == EnterExitState.Visible || it == EnterExitState.PostExit },
        enter = slideInVertically(
            initialOffsetY = { (it * verticalOffsetMultiplier) / verticalOffsetDivider },
            animationSpec = tween(
                durationMillis = enterDurationMillis,
                delayMillis = enterDelayMillis,
                easing = EaseInOut,
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = enterDurationMillis,
                delayMillis = enterDelayMillis,
                easing = EaseInOut,
            )
        ),
        exit = ExitTransition.None,
    ) {
        content()
    }
}

@Composable
private fun VpnDefaultConnectionLabel(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier
            .padding(vertical = 16.dp)
            .clip(shape = RoundedCornerShape(size = 4.dp))
            .clickable(onClick = onClick)
            .then(other = modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(id = R.string.connection_card_label_default_connection),
            style = ProtonTheme.typography.body2Regular,
        )

        Icon(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(size = 16.dp),
            painter = painterResource(id = CoreR.drawable.ic_proton_chevron_down_filled),
            contentDescription = stringResource(id = R.string.accessibility_external_link_suffix),
        )
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
            CardLabel.ConnectionStatus(R.string.connection_card_label_last_connected),
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
