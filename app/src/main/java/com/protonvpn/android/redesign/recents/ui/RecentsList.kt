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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.SimpleModalBottomSheet
import com.protonvpn.android.redesign.base.ui.ConnectIntentIcon
import com.protonvpn.android.redesign.base.ui.VpnDivider
import com.protonvpn.android.redesign.base.ui.optional
import com.protonvpn.android.redesign.recents.usecases.RecentsListViewState
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentBlankRow
import com.protonvpn.android.redesign.vpn.ui.label
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm
import kotlin.math.roundToInt
import me.proton.core.presentation.R as CoreR

data class ItemIds(
    val connectionCard: Long?,
    val recents: List<Long>
)

private enum class PeekThresholdItem {
    ConnectionCard, Header, PromoBanner
}

@Composable
fun rememberRecentsExpandState() = rememberSaveable(saver = RecentsExpandState.Saver) { RecentsExpandState() }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentsList(
    viewState: RecentsListViewState,
    onConnectClicked: () -> Unit,
    onDisconnectClicked: () -> Unit,
    onOpenConnectionPanelClicked: () -> Unit,
    onRecentClicked: (item: RecentItemViewState) -> Unit,
    onRecentOpen: (item: RecentItemViewState) -> Unit,
    onOpenDefaultConnection: (() -> Unit)? = null,
    errorSnackBar: androidx.compose.material.SnackbarHostState?,
    modifier: Modifier = Modifier,
    changeServerButton: (@Composable ColumnScope.() -> Unit)? = null,
    promoBanner: (@Composable (Modifier) -> Unit)? = null,
    promoBannerPeekOffset: Dp = 0.dp,
    upsellContent: (@Composable (Modifier, Dp) -> Unit)? = null,
    topPadding: Dp = 0.dp,
    horizontalPadding: Dp = 0.dp,
    expandState: RecentsExpandState?,
) {
    val itemIds = remember(viewState) { viewState.toItemIds() }
    val itemIdsTransition = updateTransition(targetState = itemIds, label = "item IDs")

    val scope = rememberCoroutineScope()
    val listState = expandState?.lazyListState ?: rememberLazyListState()

    val listModifier = if (expandState != null) {
        modifier
            .nestedScroll(expandState.createNestedScrollConnection(scope))
            .expandCollapseSemantics(listState, expandState)
            .onGloballyPositioned {
                expandState.setListHeight(it.size.height)
            }
    } else {
        modifier
    }
    val peekPositionObserver = Modifier.onGloballyPositioned {
        expandState?.setPeekHeight(it.boundsInParent().bottom.roundToInt())
    }
    val peekThresholdItem = when {
        promoBanner != null -> PeekThresholdItem.PromoBanner
        viewState.recents.isNotEmpty() || upsellContent != null -> PeekThresholdItem.Header
        else -> PeekThresholdItem.ConnectionCard
    }

    LazyColumn(
        state = listState,
        modifier = listModifier
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            val connectionCardModifier = Modifier
                .padding(top = topPadding, start = horizontalPadding, end = horizontalPadding)
                .optional({ peekThresholdItem == PeekThresholdItem.ConnectionCard }, peekPositionObserver)
                .animateItemPlacement()
                .animateContentSize()
            Column(
                modifier = connectionCardModifier
            ) {
                VpnConnectionCard(
                    viewState = viewState.connectionCard,
                    changeServerButton = changeServerButton,
                    onConnect = onConnectClicked,
                    onDisconnect = onDisconnectClicked,
                    onOpenConnectionPanel = onOpenConnectionPanelClicked,
                    onOpenDefaultConnection = onOpenDefaultConnection,
                    modifier = Modifier.padding(16.dp),
                    itemIdsTransition = itemIdsTransition
                )
                errorSnackBar?.let {
                    androidx.compose.material.SnackbarHost(hostState = it) { data ->
                        androidx.compose.material.Snackbar(
                            actionColor = ProtonTheme.colors.backgroundDeep,
                            backgroundColor = ProtonTheme.colors.notificationError,
                            actionOnNewLine = true,
                            shape = ProtonTheme.shapes.medium,
                            snackbarData = data
                        )
                    }
                }
            }
        }
        if (promoBanner != null) {
            item {
                val peekHeightPx = with(LocalDensity.current) { promoBannerPeekOffset.toPx() }
                val bannerPeekObserver = Modifier.onGloballyPositioned {
                    expandState?.setPeekHeight((it.boundsInParent().top + peekHeightPx).roundToInt())
                }
                promoBanner(
                    Modifier
                        .padding(horizontal = horizontalPadding)
                        .fillMaxWidth()
                        .animateItemPlacement()
                        .optional({ peekThresholdItem == PeekThresholdItem.PromoBanner }, bannerPeekObserver)
                )
            }
        }
        if (viewState.recents.isNotEmpty() || upsellContent != null) {
            // Note: so far it's always either upsell content or recents.
            // This part will change with the addition of promo banners.
            item {
                val headlineText =
                    if (viewState.recents.isNotEmpty()) R.string.recents_headline
                    else R.string.home_upsell_carousel_headline
                Text(
                    stringResource(id = headlineText),
                    style = ProtonTheme.typography.body2Regular,
                    color = ProtonTheme.colors.textWeak,
                    modifier = Modifier
                        .padding(horizontal = horizontalPadding)
                        .fillMaxWidth()
                        .animateItemPlacement()
                        .optional({ peekThresholdItem == PeekThresholdItem.Header }, peekPositionObserver)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        if (upsellContent != null) {
            item {
                upsellContent(Modifier.animateItemPlacement(), horizontalPadding)
            }
        }
        itemsIndexed(viewState.recents, key = { _, item -> item.id }) { index, item ->
            val isVisible = remember {
                MutableTransitionState(!itemIdsTransition.itemJustAdded(item.id))
            }
            isVisible.targetState = true
            AnimatedVisibility(
                visibleState = isVisible,
                enter = slideInVertically { height -> -height } + fadeIn(),
                exit = ExitTransition.None,
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .animateItemPlacement(),
            ) {
                RecentRow(
                    item = item,
                    onClick = { onRecentClicked(item) },
                    onRecentSettingOpen = onRecentOpen
                )
            }
            if (index < viewState.recents.lastIndex) {
                VpnDivider(
                    Modifier
                        .padding(horizontal = horizontalPadding)
                        .animateItemPlacement())
            }
        }
    }
}

class RecentBottomSheetState {
    var recentSettingModalShown by mutableStateOf(false)
    var selectedRecentItem by mutableStateOf<RecentItemViewState?>(null)

    val onRecentSettingOpen: (RecentItemViewState) -> Unit = {
        selectedRecentItem = it
        recentSettingModalShown = true
    }

    fun dismiss() {
        recentSettingModalShown = false
        selectedRecentItem = null
    }
}

@Composable
fun rememberRecentBottomSheetState(): RecentBottomSheetState {
    // Note: it should be saveable but RecentBottomSheetState requires a RecentItemViewState which is a complex object
    // that should not be serialized but instead fetched by recent ID. This would require going through the ViewModel
    // and is not worth the effort for a small, short-lived dialog.
    return remember { RecentBottomSheetState() }
}

@Composable
fun RecentBottomSheetDialog(
    state: RecentBottomSheetState,
    onRecentPinToggle: (item: RecentItemViewState) -> Unit,
    onRecentRemove: (item: RecentItemViewState) -> Unit,
) {
    if (state.recentSettingModalShown) {
        state.selectedRecentItem?.let {
            RecentSettingBottomSheetContent(
                item = it,
                onRecentPinToggle = {
                    onRecentPinToggle(it)
                    state.dismiss()
                },
                onRecentRemove = {
                    onRecentRemove(it)
                    state.dismiss()
                },
                onDismiss = state::dismiss
            )
        }
    }
}

@Composable
private fun RecentSettingBottomSheetContent(
    item: RecentItemViewState,
    onRecentPinToggle: (item: RecentItemViewState) -> Unit,
    onRecentRemove: (item: RecentItemViewState) -> Unit,
    onDismiss: () -> Unit,
) {
    SimpleModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column {
            ConnectIntentBlankRow(
                leadingComposable = { ConnectIntentIcon(item.connectIntent.primaryLabel) },
                title = item.connectIntent.primaryLabel.label(),
                subTitle = item.connectIntent.secondaryLabel?.label(),
                serverFeatures = item.connectIntent.serverFeatures,
                isConnected = item.isConnected,
                isUnavailable = false,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            VpnDivider()
            IconedButton(
                title = stringResource(id = if (item.isPinned) R.string.recent_action_unpin else R.string.recent_action_pin),
                iconRes = if (item.isPinned) CoreR.drawable.ic_proton_pin_slash_filled else CoreR.drawable.ic_proton_pin_filled,
                onClick = {
                    onRecentPinToggle(item)
                }
            )
            VpnDivider()
            IconedButton(
                title = stringResource(id = R.string.recent_action_remove),
                iconRes = CoreR.drawable.ic_proton_trash_cross_filled,
                onClick = {
                    onRecentRemove(item)
                }
            )
        }
    }
}
@Composable
private fun IconedButton(
    modifier: Modifier = Modifier,
    title: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(64.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = ProtonTheme.colors.iconWeak,
            modifier = Modifier
                .padding(end = 8.dp)
        )
        Text(
            text = title,
            style = ProtonTheme.typography.defaultNorm,
        )
    }
}
@Preview
@Composable
fun IconedButtonsPreview() {
    IconedButton(
        title = stringResource(id = R.string.recent_action_pin),
        iconRes = CoreR.drawable.ic_proton_pin_filled,
        onClick =  {}
    )
    IconedButton(
        title = stringResource(id = R.string.recent_action_remove),
        iconRes = CoreR.drawable.ic_proton_trash_cross_filled,
        onClick =  {}
    )
}

private fun RecentsListViewState.toItemIds() =
    ItemIds(
        connectionCardRecentId,
        recents.map { it.id }
    )

private fun Transition<ItemIds>.itemJustAdded(itemId: Long): Boolean =
    currentState.connectionCard == itemId &&
        !currentState.recents.contains(itemId) &&
        targetState.recents.contains(itemId)
