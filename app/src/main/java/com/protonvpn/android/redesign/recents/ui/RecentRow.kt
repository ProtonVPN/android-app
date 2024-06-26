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
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.ActiveDot
import com.protonvpn.android.redesign.base.ui.FlagOrGatewayIndicator
import com.protonvpn.android.redesign.base.ui.unavailableServerAlpha
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.redesign.vpn.ui.ServerDetailsRow
import com.protonvpn.android.redesign.vpn.ui.label
import me.proton.core.compose.theme.ProtonTheme
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

@Composable
fun RecentRow(
    item: RecentItemViewState,
    onClick: () -> Unit,
    onRecentSettingOpen: (RecentItemViewState) -> Unit,
    modifier: Modifier = Modifier
) {
    val pinnedStateDescription = stringResource(id = R.string.recent_action_accessibility_state_pinned)
    val customAccessibilityActions = listOf(
            CustomAccessibilityAction(stringResource(id = R.string.accessibility_action_open)) { onRecentSettingOpen(item); true },
        )
    val extraContentDescription = item.availability.extraContentDescription()
    val clickActionLabel = item.availability.accessibilityAction()
    val semantics = Modifier
        .clickable(onClick = onClick, onClickLabel = clickActionLabel)
        .semantics(mergeDescendants = true) {
            if (item.isPinned) stateDescription = pinnedStateDescription
            if (extraContentDescription != null) contentDescription = extraContentDescription
            customActions = customAccessibilityActions
        }
    val isDisabled = item.availability != RecentAvailability.ONLINE
    val iconRes =
        if (item.isPinned) CoreR.drawable.ic_proton_pin_filled else CoreR.drawable.ic_proton_clock_rotate_left
    RecentBlankRow(
        title = item.connectIntent.primaryLabel.label(),
        subTitle = item.connectIntent.secondaryLabel?.label(),
        serverFeatures = item.connectIntent.serverFeatures,
        isConnected = item.isConnected,
        modifier = modifier.then(semantics).unavailableServerAlpha(isDisabled).clickable(onClick = onClick).padding(horizontal = 16.dp),
        leadingComposable = {
            Row(
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
        },
        trailingComposable = {
            if (item.availability == RecentAvailability.AVAILABLE_OFFLINE) {
                Icon(
                    painterResource(id = CoreR.drawable.ic_proton_wrench),
                    tint = ProtonTheme.colors.iconWeak,
                    contentDescription = null, // Description is added on the whole row.
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            val interactionSource = remember { MutableInteractionSource() }
            val iconOverflow = 8.dp // How much the icon sticks out into edgePadding
            Box(
                modifier = Modifier
                    .height(IntrinsicSize.Max)
                    .clearAndSetSemantics {} // Accessibility handled via semantics on the whole row.
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null, // Indication only on the icon.
                        onClick = { onRecentSettingOpen(item) }
                    )
                    .padding(end = 16.dp - iconOverflow)
            ) {
                Icon(
                    painterResource(CoreR.drawable.ic_proton_three_dots_horizontal),
                    tint = ProtonTheme.colors.iconNorm,
                    modifier = Modifier
                        .clip(CircleShape)
                        .indication(interactionSource, rememberRipple())
                        .padding(8.dp),
                    contentDescription = null // Accessibility handled via semantics on the whole row.
                )
            }
        }
    )
}
@Composable
fun RecentBlankRow(
    modifier: Modifier = Modifier,
    leadingComposable: @Composable RowScope.() -> Unit,
    trailingComposable: (@Composable RowScope.() -> Unit)? = null,
    title: String,
    subTitle: AnnotatedString?,
    serverFeatures: Set<ServerFeature>,
    isConnected: Boolean = false,
) {
    val isLargerRecent = subTitle != null || serverFeatures.isNotEmpty()
    Row(
        modifier = modifier
            .heightIn(min = 64.dp)
            .padding(vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = if (isLargerRecent) Alignment.Top else Alignment.CenterVertically,
    ) {
        leadingComposable()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Row {
                Text(
                    text = title,
                    style = ProtonTheme.typography.body1Regular,
                )
                if (isConnected) {
                    ActiveDot(modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (subTitle != null || serverFeatures.isNotEmpty()) {
                ServerDetailsRow(
                    subTitle,
                    null,
                    serverFeatures,
                    detailsStyle = ProtonTheme.typography.body2Regular,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        trailingComposable?.invoke(this)
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
            onRecentSettingOpen = {},
            modifier = Modifier.fillMaxWidth()
        )
    }
}
