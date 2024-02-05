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
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.verticalScrollAxisRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.MaxContentWidth
import com.protonvpn.android.redesign.base.ui.VpnDivider
import com.protonvpn.android.redesign.base.ui.optional
import com.protonvpn.android.redesign.recents.usecases.RecentsListViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionWeak
import kotlin.math.roundToInt

data class ItemIds(
    val connectionCard: Long?,
    val recents: List<Long>
)

/**
 * The recents list is initially positioned to only show the connection card and it can be expanded to its full height
 * by dragging it up, a bit similar to a standard bottom sheet.
 * This class manages the state of the expansion:
 *  - the list offset controls the list's offset from the regular laid out position,
 *  - createNestedScrollConnection() creates a nested scroll connection to use with the list's nestedScroll, the
 *    connection manages the list offset via nested scroll events,
 *  - setPeekHeight, setListHeight and setMaxHeight need to be called after layout/measure to set the list offset range.
 */
@Stable
class RecentsExpandState(
    initialListOffsetPx: Int = Int.MAX_VALUE,
    val lazyListState: LazyListState = LazyListState()
) {
    private val mutatorMutex = MutatorMutex()
    private val maxHeightState = mutableIntStateOf(0)
    private val listOffsetState = mutableIntStateOf(initialListOffsetPx)
    private val listHeightState = mutableIntStateOf(0)
    private val peekHeightState = mutableIntStateOf(0)

    private val maxHeightPx: Int get() = maxHeightState.intValue

    private val minOffset: Int get() = maxHeightPx - listHeightState.intValue
    private val maxOffset: Int get() = maxHeightPx - peekHeightState.intValue

    val isCollapsed: Boolean get() = listOffsetPx == maxOffset

    val listOffsetPx by listOffsetState
    val fullExpandProgress: Float get() = when { // 0 when collapsed (at the bottom), 1 when covers the whole viewport.
        // Not all values initialized yet.
        listOffsetPx == Int.MAX_VALUE -> 0f
        maxHeightPx == 0 -> 0f
        peekHeightState.intValue == 0 -> 0f

        maxOffset == 0 -> 0f
        else -> 1f - listOffsetPx.toFloat() / maxOffset
    }

    fun createNestedScrollConnection(coroutineScope: CoroutineScope) =
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (available.y < 0) handleAvailableScroll(available) else Offset.Zero

            override fun onPostScroll(
                consumed: Offset, available: Offset, source: NestedScrollSource
            ): Offset = if (available.y > 0) handleAvailableScroll(available) else Offset.Zero

            private fun handleAvailableScroll(available: Offset): Offset {
                if (maxHeightPx == 0) return Offset.Zero // Max height hasn't been initialized yet.

                val newOffset = (listOffsetPx + available.y).coerceIn(minOffset.toFloat(), maxOffset.toFloat())
                val deltaToConsume = newOffset - listOffsetPx
                coroutineScope.launch {
                    mutatorMutex.mutate(MutatePriority.UserInput) {
                        listOffsetState.intValue = newOffset.roundToInt()
                    }
                }
                return Offset(0f, deltaToConsume)
            }
        }

    suspend fun expand() {
        animateOffsetTo(minOffset)
    }

    suspend fun collapse() {
        // This runs two separate animations and it might look less than ideal (two "motions"), but it's simple and
        // maybe won't be too noticeable. If needed it should be possible to run a single animation to scroll both.
        lazyListState.animateScrollToItem(0)
        animateOffsetTo(maxOffset)
    }

    fun setPeekHeight(newPeekHeight: Int) {
        val shouldContract = listOffsetPx == maxOffset
        peekHeightState.intValue = newPeekHeight
        when {
            listOffsetPx > maxOffset -> listOffsetState.intValue = maxOffset
            shouldContract -> listOffsetState.intValue = maxOffset
        }
    }

    fun setListHeight(newListHeight: Int) {
        if (listHeightState.intValue != newListHeight) {
            listHeightState.intValue = newListHeight
            when {
                listOffsetPx > maxOffset -> listOffsetState.intValue = maxOffset
                listOffsetPx < minOffset -> listOffsetState.intValue = minOffset
            }
        }
    }

    fun setMaxHeight(newMaxHeight: Int) {
        maxHeightState.intValue = newMaxHeight
    }

    private suspend fun animateOffsetTo(newOffset: Int) {
        mutatorMutex.mutate(MutatePriority.Default) {
            try {
                animate(listOffsetState.intValue.toFloat(), newOffset.toFloat()) { value, _ ->
                    listOffsetState.intValue = value.roundToInt()
                }
            } finally {
                listOffsetState.intValue = newOffset
            }
        }
    }

    companion object {
        val Saver: Saver<RecentsExpandState, *> = listSaver(
            save = {
                listOf(
                    it.listOffsetPx,
                    it.lazyListState.firstVisibleItemIndex,
                    it.lazyListState.firstVisibleItemScrollOffset
                )
           },
            restore = { RecentsExpandState(initialListOffsetPx = it[0], lazyListState = LazyListState(it[1], it[2]) ) }
        )
    }
}

// Note: in theory accessibility on the list with connection card and recents should be handled automatically
// via nested scroll and the default scroll accessibility.
// Unfortunately it doesn't work this way: https://issuetracker.google.com/issues/240449680
@Composable
private fun Modifier.expandCollapseSemantics(
    listState: LazyListState,
    expandState: RecentsExpandState,
): Modifier {
    // Lazy lists cannot provide absolute scroll offsets so generate pseudo-offsets that are good enough for
    // accessibility services to scroll the list. The expanded/coll
    // Heavily inspired by Modifier.lazyLayoutSemantics and LazyLayoutSemanticState - see their code for more
    // detailed explanations.
    fun pseudoScrollOffset() = with(listState) {
        ((if (expandState.isCollapsed) 0 else 1) + firstVisibleItemScrollOffset + firstVisibleItemIndex * 500).toFloat()
    }
    fun maxPseudoScrollOffset() =
        if (expandState.isCollapsed || listState.canScrollForward) pseudoScrollOffset() + 100 else pseudoScrollOffset()
    fun isAtTopOfList() = with(listState) { firstVisibleItemScrollOffset + firstVisibleItemIndex == 0 }

    val coroutineScope = rememberCoroutineScope()
    val expandCollapseSemantics = remember(listState) {
        val scrollAxisRange = ScrollAxisRange(
            value = ::pseudoScrollOffset,
            maxValue = ::maxPseudoScrollOffset,
            reverseScrolling = false
        )
        val scrollAction: (Float) -> Unit = { y ->
            coroutineScope.launch {
                when {
                    expandState.isCollapsed && y > 0 -> expandState.expand()
                    isAtTopOfList() && !expandState.isCollapsed && y < 0 -> expandState.collapse()
                    else -> listState.animateScrollBy(y)
                }
            }
        }


        Modifier.semantics {
            verticalScrollAxisRange = scrollAxisRange
            scrollBy { _, y ->
                scrollAction(y)
                true
            }
        }
    }
    return this.then(expandCollapseSemantics)
}

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
    onRecentPinToggle: (item: RecentItemViewState) -> Unit,
    onRecentRemove: (item: RecentItemViewState) -> Unit,
    errorSnackBar: androidx.compose.material.SnackbarHostState?,
    modifier: Modifier = Modifier,
    changeServerButton: (@Composable ColumnScope.() -> Unit)? = null,
    promoBanner: (@Composable (Modifier) -> Unit)? = null,
    promoBannerPeekOffset: Dp = 0.dp,
    upsellContent: (@Composable (Modifier) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(),
    expandState: RecentsExpandState?,
) {
    val itemIds = viewState.toItemIds()
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
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            val connectionCardModifier = Modifier
                .widthIn(max = ProtonTheme.MaxContentWidth)
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
                        .widthIn(max = ProtonTheme.MaxContentWidth)
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
                    style = ProtonTheme.typography.captionWeak,
                    modifier = Modifier
                        .widthIn(max = ProtonTheme.MaxContentWidth)
                        .fillMaxWidth()
                        .animateItemPlacement()
                        .optional({ peekThresholdItem == PeekThresholdItem.Header }, peekPositionObserver)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        if (upsellContent != null) {
            item {upsellContent(Modifier.animateItemPlacement()) }
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
                VpnDivider(
                    Modifier
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
