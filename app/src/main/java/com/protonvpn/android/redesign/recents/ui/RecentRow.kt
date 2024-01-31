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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.FlagOrGatewayIndicator
import com.protonvpn.android.redesign.base.ui.unavailableServerAlpha
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentLabels
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import kotlinx.coroutines.flow.distinctUntilChanged
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.overlineStrongUnspecified
import me.proton.core.presentation.R as CoreR

enum class RecentAvailability {
    // Order is significant, see RecentsListViewStateFlow.getAvailability.
    UNAVAILABLE_PLAN, UNAVAILABLE_PROTOCOL, AVAILABLE_OFFLINE, ONLINE
}

data class RecentItemViewState(
    val id: Long,
    val connectIntent: ConnectIntentViewState,
    val isPinned: Boolean,
    val isConnected: Boolean,
    val availability: RecentAvailability,
)

private object RecentRow {
    val pinActionShape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
    val removeActionShape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
}

@Composable
fun RecentRow(
    item: RecentItemViewState,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    // SwipeableRecentActions executes the (un)pin action *after* swiped row returns to natural position to delay list
    // change animations (I know, model changes should not be affected by animations but that's the simplest solution).
    // In order to reflect the pinned state immediately on the item we have a local copy that is being updated
    // immediately by onWillTogglePin.
    var localItem by remember(item) { mutableStateOf(item) }
    SwipeableRecentActions(
        isPinned = item.isPinned,
        onWillTogglePin = { localItem = localItem.copy(isPinned = !localItem.isPinned) },
        onTogglePin = onTogglePin,
        onRemove = onRemove,
        modifier = modifier
    ) {
        val pinnedStateDescription = stringResource(id = R.string.recent_action_accessibility_state_pinned)
        val pinLabelRes = if (localItem.isPinned) R.string.recent_action_unpin else R.string.recent_action_pin
        val customAccessibilityActions = listOf(
            CustomAccessibilityAction(stringResource(id = pinLabelRes)) { onTogglePin(); true },
            CustomAccessibilityAction(stringResource(id = R.string.recent_action_remove)) { onRemove(); true },
        )
        val extraContentDescription = localItem.availability.extraContentDescription()
        val clickActionLabel = localItem.availability.accessibilityAction()
        val semantics = Modifier
            .clickable(onClick = onClick, onClickLabel = clickActionLabel)
            .semantics(mergeDescendants = true) {
                if (localItem.isPinned) stateDescription = pinnedStateDescription
                if (extraContentDescription != null) contentDescription = extraContentDescription
                customActions = customAccessibilityActions
            }
        RecentRowContent(localItem, modifier.then(semantics))
    }
}

@Composable
private fun RecentRowContent(
    item: RecentItemViewState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.Start
        ) {
            val isDisabled = item.availability != RecentAvailability.ONLINE
            val iconRes =
                if (item.isPinned) CoreR.drawable.ic_proton_pin_filled else CoreR.drawable.ic_proton_clock_rotate_left
            Row(
                modifier = Modifier.unavailableServerAlpha(isDisabled),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    tint = ProtonTheme.colors.iconWeak,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp)
                )
                FlagOrGatewayIndicator(item.connectIntent.primaryLabel)
            }
            ConnectIntentLabels(
                item.connectIntent.primaryLabel,
                item.connectIntent.secondaryLabel,
                item.connectIntent.serverFeatures,
                item.isConnected,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
                    .unavailableServerAlpha(isDisabled)
            )
            if (item.availability == RecentAvailability.AVAILABLE_OFFLINE) {
                Icon(
                    painterResource(id = CoreR.drawable.ic_proton_wrench),
                    tint = ProtonTheme.colors.iconWeak,
                    contentDescription = null, // Description is added on the whole row.
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}

@Composable
private fun PinActionBackground(
    pinned: Boolean,
    modifier: Modifier = Modifier
) {
    SwipeActionBackground(
        labelRes = if (pinned) R.string.recent_action_unpin else R.string.recent_action_pin,
        iconRes = if (pinned) CoreR.drawable.ic_proton_pin_slash_filled else CoreR.drawable.ic_proton_pin_filled,
        color = ProtonTheme.colors.notificationWarning,
        shape = RecentRow.pinActionShape,
        alignment = Alignment.CenterStart,
        modifier = modifier
    )
}

@Composable
private fun RemoveActionBackground(
    modifier: Modifier = Modifier
) {
    SwipeActionBackground(
        labelRes = R.string.recent_action_remove,
        iconRes = CoreR.drawable.ic_proton_trash,
        color = ProtonTheme.colors.notificationError,
        shape = RecentRow.removeActionShape,
        alignment = Alignment.CenterEnd,
        modifier = modifier
    )
}

@Composable
private fun SwipeActionBackground(
    labelRes: Int,
    iconRes: Int,
    color: Color,
    shape: Shape,
    alignment: Alignment,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = color, shape = shape) {
        CompositionLocalProvider(LocalContentColor provides ProtonTheme.colors.textInverted) {
            Box {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(alignment)
                        .padding(horizontal = 24.dp)
                ) {
                    Icon(
                        painterResource(id = iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    VerticalSpacer(height = 4.dp)
                    Text(stringResource(id = labelRes), style = ProtonTheme.typography.overlineStrongUnspecified)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRecentActions(
    isPinned: Boolean,
    onWillTogglePin: () -> Unit,
    onTogglePin: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    ObserveDismissState(dismissState, onWillTogglePin, onTogglePin, onRemove)
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            when (dismissState.dismissDirection) {

                SwipeToDismissBoxValue.EndToStart -> RemoveActionBackground(Modifier.fillMaxSize())
                SwipeToDismissBoxValue.StartToEnd -> {
                    // The pinned state of background should not change until row returns to the neutral position.
                    val rememberedPinned by remember(dismissState.dismissDirection) { derivedStateOf { isPinned } }
                    PinActionBackground(rememberedPinned, Modifier.fillMaxSize())
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        modifier = modifier
    ) {
        val shape = when (dismissState.dismissDirection) {
            SwipeToDismissBoxValue.EndToStart -> RecentRow.removeActionShape
            SwipeToDismissBoxValue.StartToEnd -> RecentRow.pinActionShape
            SwipeToDismissBoxValue.Settled -> RectangleShape
        }
        Surface(
            color = ProtonTheme.colors.backgroundNorm,
            shape = shape
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ObserveDismissState(
    dismissState: SwipeToDismissBoxState,
    onWillTogglePin: () -> Unit,
    onTogglePin: () -> Unit,
    onRemove: () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.targetValue }
            .distinctUntilChanged()
            .collect {
                if (it != SwipeToDismissBoxValue.Settled) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
    }
    LaunchedEffect(dismissState, onWillTogglePin, onTogglePin, onRemove) {
        snapshotFlow { dismissState.currentValue }
            .collect {
                when (it) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        // Notify parent that the item will be pinned right after the swipe animation finishes.
                        // See RecentRow above for more context.
                        onWillTogglePin()
                        dismissState.reset()
                        onTogglePin()
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        onRemove()
                    }
                    SwipeToDismissBoxValue.Settled -> Unit
                }
            }
    }
}

@Composable
private fun RecentAvailability.accessibilityAction(): String? =
    when (this) {
        RecentAvailability.ONLINE -> R.string.accessibility_action_connect
        RecentAvailability.UNAVAILABLE_PLAN -> R.string.accessibility_action_upgrade
        RecentAvailability.AVAILABLE_OFFLINE,
        RecentAvailability.UNAVAILABLE_PROTOCOL -> null
    }?.let { stringResource(it) }

@Composable
private fun RecentAvailability.extraContentDescription(): String? =
    when(this) {
        RecentAvailability.UNAVAILABLE_PLAN,
        RecentAvailability.UNAVAILABLE_PROTOCOL -> R.string.accessibility_item_unavailable
        RecentAvailability.AVAILABLE_OFFLINE -> R.string.accessibility_item_in_maintenance
        RecentAvailability.ONLINE -> null
    }?.let { stringResource(it) }

@Preview
@Composable
private fun PreviewRecent() {
    VpnTheme {
        var isPinned by remember { mutableStateOf(false) }
        RecentRow(
            item = RecentItemViewState(
                id = 0,
                ConnectIntentViewState(
                    primaryLabel = ConnectIntentPrimaryLabel.Country(CountryId.switzerland, CountryId.sweden),
                    secondaryLabel = ConnectIntentSecondaryLabel.SecureCore(null, CountryId.sweden),
                    serverFeatures = emptySet()
                ),
                isPinned = isPinned,
                isConnected = true,
                availability = RecentAvailability.AVAILABLE_OFFLINE,
            ),
            onClick = {},
            onTogglePin = { isPinned = !isPinned },
            onRemove = {},
            modifier = Modifier.fillMaxWidth()
        )
    }
}
