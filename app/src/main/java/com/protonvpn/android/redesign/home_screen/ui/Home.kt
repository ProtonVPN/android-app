/*
 * Copyright (c) 2023 Proton AG
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

package com.protonvpn.android.redesign.home_screen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.LocalVpnUiDelegate
import com.protonvpn.android.redesign.recents.ui.RecentsList
import com.protonvpn.android.redesign.vpn.ui.VpnStatusBottom
import com.protonvpn.android.redesign.vpn.ui.VpnStatusTop
import com.protonvpn.android.redesign.vpn.ui.rememberVpnStateAnimationProgress
import com.protonvpn.android.redesign.vpn.ui.vpnStatusOverlayBackground
import kotlinx.coroutines.launch
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun HomeRoute() {
    HomeView()
}

@Composable
fun HomeView() {
    val viewModel: HomeViewModel = hiltViewModel()
    val recentsViewState = viewModel.recentsViewState.collectAsStateWithLifecycle().value
    val vpnState = viewModel.vpnStateViewFlow.collectAsStateWithLifecycle().value
    val vpnStateTransitionProgress = rememberVpnStateAnimationProgress(vpnState)
    val coroutineScope = rememberCoroutineScope()

    ConstraintLayout(
        modifier = Modifier
            .fillMaxSize()
            // Put something in the background to pretend there's a map. TODO: remove when map is added.
            .paint(
                painter = painterResource(R.drawable.ic_proton_earth_filled),
                alpha = 0.2f,
                contentScale = ContentScale.Crop
            )
    ) {
        val (vpnStatusTop, vpnStatusBottom) = createRefs()

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .vpnStatusOverlayBackground(vpnState)
        )
        VpnStatusBottom(
            vpnState,
            transitionValue = { vpnStateTransitionProgress.value },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .constrainAs(vpnStatusBottom) {
                    top.linkTo(vpnStatusTop.bottom)
                }
        )

        val vpnUiDelegate = LocalVpnUiDelegate.current
        val connectAction = remember<() -> Unit>(vpnUiDelegate) {
            { coroutineScope.launch { viewModel.connect(vpnUiDelegate) } }
        }
        val connectRecentAction = remember<(Long) -> Unit>(vpnUiDelegate) {
            { id -> coroutineScope.launch { viewModel.connectRecent(id, vpnUiDelegate) } }
        }
        val listBgColor = ProtonTheme.colors.backgroundNorm
        val listBgGradientColors = listOf(Color.Transparent, listBgColor)
        val listBgGradientHeight = 100.dp
        val listState = rememberLazyListState()
        val bgOffset = remember { derivedStateOf { calculateBgOffset(listState) } }
        BoxWithConstraints {
            RecentsList(
                viewState = recentsViewState,
                lazyListState = listState,
                onConnectClicked = connectAction,
                onDisconnectClicked = viewModel::disconnect,
                onOpenPanelClicked = {},
                onHelpClicked = {},
                onRecentClicked = connectRecentAction,
                onRecentPinToggle = viewModel::togglePinned,
                onRecentRemove = viewModel::removeRecent,
                maxHeight = maxHeight,
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.linearGradient(
                                listBgGradientColors,
                                start = Offset(0f, bgOffset.value.toFloat() - listBgGradientHeight.toPx()),
                                end = Offset(0f, bgOffset.value.toFloat())
                            )
                        )
                        drawRect(listBgColor, topLeft = Offset(0f, bgOffset.value.toFloat()))
                    }
            )
        }

        val vpnStatusTopMinHeight = 48.dp
        val fullCoverThresholdPx = LocalDensity.current.run {
            (listBgGradientHeight - vpnStatusTopMinHeight).toPx()
        }
        val coverAlpha = remember(fullCoverThresholdPx) {
            derivedStateOf { calculateOverlayAlpha(listState, fullCoverThresholdPx) }
        }
        VpnStatusTop(
            vpnState,
            transitionValue = { vpnStateTransitionProgress.value },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = vpnStatusTopMinHeight)
                .recentsScrollOverlayBackground(coverAlpha)
                .statusBarsPadding()
                .constrainAs(vpnStatusTop) {}
        )
    }
}

private fun Modifier.recentsScrollOverlayBackground(
    alpha: State<Float>
): Modifier = composed {
    val separatorHeight = 1.dp
    val backgroundColor = ProtonTheme.colors.backgroundNorm
    val separatorColor = ProtonTheme.colors.separatorNorm
    drawBehind {
        drawRect(color = backgroundColor, alpha = alpha.value)
        drawRect(
            color = separatorColor,
            topLeft = Offset(0f, size.height - separatorHeight.toPx()),
            size = Size(size.width, separatorHeight.toPx()),
            alpha = alpha.value
        )
    }
}

private fun calculateBgOffset(lazyListState: LazyListState): Int {
    val firstVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.getOrNull(0)
    return when {
        firstVisibleItem == null -> lazyListState.layoutInfo.beforeContentPadding
        firstVisibleItem.index == 0 ->
            (lazyListState.layoutInfo.beforeContentPadding + firstVisibleItem.offset).coerceAtLeast(0)
        else -> 0
    }
}

private fun calculateOverlayAlpha(lazyListState: LazyListState, fullCoverPx: Float): Float {
    val firstVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.getOrNull(0)
    return when {
        firstVisibleItem == null -> 0f
        firstVisibleItem.index == 0 &&
            lazyListState.layoutInfo.beforeContentPadding + firstVisibleItem.offset > 0 ->
            0f
        firstVisibleItem.index == 0 -> {
            val onScreenSize =
                fullCoverPx + lazyListState.layoutInfo.beforeContentPadding + firstVisibleItem.offset
            (1f - onScreenSize / fullCoverPx).coerceIn(0f, 1f)
        }
        else -> 1f
    }
}
