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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.MaxContentWidth
import com.protonvpn.android.redesign.base.ui.VpnDivider
import com.protonvpn.android.redesign.recents.usecases.RecentsListViewState
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionWeak
import kotlin.math.roundToInt

data class ItemIds(
    val connectionCard: Long?,
    val recents: List<Long>
)

@Stable
class RecentsExpandState(
    initialListOffsetPx: Int = Int.MAX_VALUE,
) {
    private val maxHeightState = mutableIntStateOf(0)
    private val listOffsetState = mutableIntStateOf(initialListOffsetPx)
    private val listHeightState = mutableIntStateOf(0)
    private val peekHeightState = mutableIntStateOf(0)

    private val maxHeightPx: Int get() = maxHeightState.intValue

    private val minOffset: Int get() = maxHeightPx - listHeightState.intValue
    private val maxOffset: Int get() = maxHeightPx - peekHeightState.intValue

    val listOffsetPx by listOffsetState

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0) handleAvailableScroll(available) else Offset.Zero

        override fun onPostScroll(
            consumed: Offset, available: Offset, source: NestedScrollSource
        ): Offset = if (available.y > 0) handleAvailableScroll(available) else Offset.Zero

        private fun handleAvailableScroll(available: Offset): Offset {
            val newOffset = (listOffsetPx + available.y).coerceIn(minOffset.toFloat(), maxOffset.toFloat())
            val deltaToConsume = newOffset - listOffsetPx
            listOffsetState.intValue = newOffset.roundToInt()
            return Offset(0f, deltaToConsume)
        }
    }

    fun setPeekHeight(newPeekHeight: Int) {
        peekHeightState.intValue = newPeekHeight
        if (listOffsetPx > maxOffset) {
            listOffsetState.intValue = maxOffset
        }
    }

    fun setListHeight(newListHeight: Int) {
        listHeightState.intValue = newListHeight
        when {
            listOffsetPx > maxOffset -> listOffsetState.intValue = maxOffset
            listOffsetPx < minOffset -> listOffsetState.intValue = minOffset
        }
    }

    fun setMaxHeight(newMaxHeight: Int) {
        maxHeightState.intValue = newMaxHeight
    }

    companion object {
        val Saver: Saver<RecentsExpandState, *> = Saver(
            save = { it.listOffsetPx },
            restore = { RecentsExpandState(initialListOffsetPx = it) }
        )
    }
}

@Composable
fun rememberRecentsExpandState() = rememberSaveable(saver = RecentsExpandState.Saver) { RecentsExpandState() }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentsList(
    viewState: RecentsListViewState,
    onConnectClicked: () -> Unit,
    onDisconnectClicked: () -> Unit,
    onOpenPanelClicked: () -> Unit,
    onHelpClicked: () -> Unit,
    onRecentClicked: (item: RecentItemViewState) -> Unit,
    onRecentPinToggle: (item: RecentItemViewState) -> Unit,
    onRecentRemove: (item: RecentItemViewState) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    expandState: RecentsExpandState?
) {
    val itemIds = viewState.toItemIds()
    val itemIdsTransition = updateTransition(targetState = itemIds, label = "item IDs")

    val listModifier = if (expandState != null) {
        modifier
            .nestedScroll(expandState.nestedScrollConnection)
            .onGloballyPositioned { expandState.setListHeight(it.size.height) }
    } else {
        modifier
    }
    LazyColumn(
        modifier = listModifier,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = ProtonTheme.MaxContentWidth)
                    .onGloballyPositioned { expandState?.setPeekHeight(it.boundsInParent().bottom.roundToInt()) }
                    .animateItemPlacement()
                    .animateContentSize()
            ) {
                VpnConnectionCard(
                    viewState = viewState.connectionCard,
                    onConnect = onConnectClicked,
                    onDisconnect = onDisconnectClicked,
                    onOpenPanelClick = onOpenPanelClicked,
                    onHelpClick = onHelpClicked,
                    modifier = Modifier.padding(16.dp),
                    itemIdsTransition = itemIdsTransition
                )
                if (viewState.recents.isNotEmpty()) {
                    Text(
                        stringResource(R.string.recents_headline),
                        style = ProtonTheme.typography.captionWeak,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
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
                    .widthIn(max = ProtonTheme.MaxContentWidth)
                    .animateItemPlacement(),
            ) {
                RecentRow(
                    item = item,
                    onClick = { onRecentClicked(item) },
                    onTogglePin = { onRecentPinToggle(item) },
                    onRemove = { onRecentRemove(item) },
                )
            }
            if (index < viewState.recents.lastIndex) {
                VpnDivider(Modifier
                    .widthIn(max = ProtonTheme.MaxContentWidth)
                    .animateItemPlacement())
            }
        }
    }
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
