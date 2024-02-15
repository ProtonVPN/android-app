/*
 * Copyright (c) 2024. Proton AG
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

import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.verticalScrollAxisRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

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
                // listHeightState, peekHeightState and maxHeightState are being updated independently via
                // onGloballyPositioned. It's therefore possible that during more complex state changes the values
                // are temporarily in an incorrect state. Avoid computations (and exceptions) if that's the case.
                if (maxHeightPx == 0 || minOffset > maxOffset) return Offset.Zero

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

    suspend fun peekBelowTheFold(peekHeightPx: Float) {
        delay(0.5.seconds)
        mutatorMutex.mutate {
            val startOffset = listOffsetState.intValue.toFloat()
            animate(startOffset, maxOffset - peekHeightPx) { v, _ ->
                listOffsetState.intValue = v.roundToInt()
            }
            delay(1.5.seconds)
            animate(listOffsetState.intValue.toFloat(), maxOffset.toFloat()) { v, _ ->
                listOffsetState.intValue = v.roundToInt()
            }
        }
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
fun Modifier.expandCollapseSemantics(
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
