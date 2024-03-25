/*
 * Copyright (c) 2024 Proton AG
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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.protonvpn.android.redesign.countries.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.Flag
import com.protonvpn.android.redesign.base.ui.GatewayIndicator
import com.protonvpn.android.redesign.base.ui.InfoType
import com.protonvpn.android.redesign.vpn.ui.label
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultSmallWeak
import me.proton.core.compose.theme.headlineNorm
import me.proton.core.presentation.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerGroupsBottomSheet(
    modifier: Modifier,
    screen: SubScreenState,
    onNavigateBack: suspend (suspend () -> Unit) -> Unit,
    onNavigateToItem: (ServerGroupUiItem.ServerGroup) -> Unit,
    onItemClicked: (ServerGroupUiItem.ServerGroup) -> Unit,
    navigateToUpsell: (ServerGroupUiItem.BannerType) -> Unit,
    onClose: () -> Unit,
    onOpenInfo: (InfoType) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    val headerAreaColor by animateColorAsState(
        targetValue = if (sheetState.targetValue == SheetValue.Expanded) ProtonTheme.colors.backgroundSecondary else ProtonTheme.colors.backgroundNorm,
        animationSpec = tween(durationMillis = 500),
        label = "BottomSheetTopColorAnimation"
    )
    val listStatesMap = remember { mutableMapOf<String, LazyListState>() }
    val listState = listStatesMap.getOrPut(screen.savedState.rememberStateKey) { rememberLazyListState() }
    val onCloseWithClearState = {
        listStatesMap.clear()
        onClose()
    }
    val onNavigateBackWithClearState: suspend (suspend () -> Unit) -> Unit = { onBack ->
        listStatesMap.remove(screen.savedState.rememberStateKey)
        onNavigateBack(onBack)
    }
    ModalBottomSheetWithBackNavigation(
        modifier = modifier,
        containerColor = headerAreaColor,
        onNavigateBack = onNavigateBackWithClearState,
        onClose = onCloseWithClearState,
        sheetState = sheetState,
        scope = scope
    ) {
        BottomSheetScreen(
            screen = screen,
            onItemOpen = onNavigateToItem,
            onItemClick = onItemClicked,
            listState = listState,
            onNavigateBack = { scope.launch { onNavigateBackWithClearState {} } },
            navigateToUpsell = navigateToUpsell,
            onOpenInfo = onOpenInfo,
            modifier = Modifier
        )
    }
}

@Composable
private fun BottomSheetScreen(
    modifier: Modifier = Modifier,
    screen: SubScreenState,
    listState: LazyListState,
    onOpenInfo: (InfoType) -> Unit,
    onItemOpen: (ServerGroupUiItem.ServerGroup) -> Unit,
    onItemClick: (ServerGroupUiItem.ServerGroup) -> Unit,
    navigateToUpsell: (ServerGroupUiItem.BannerType) -> Unit,
    onNavigateBack: () -> Unit,
) {
    Column(modifier) {
        AnimatedBottomSheetHeader(screen = screen, onNavigateBack = onNavigateBack)
        ServerGroupItemsList(
            listState = listState,
            items = screen.items,
            onItemOpen = onItemOpen,
            onItemClick = onItemClick,
            onOpenInfo = onOpenInfo,
            navigateToUpsell = navigateToUpsell,
            modifier = Modifier
                .fillMaxHeight()
                .background(ProtonTheme.colors.backgroundNorm)
        )
    }
}

@Composable
private fun AnimatedBottomSheetHeader(
    modifier: Modifier = Modifier,
    screen: SubScreenState,
    onNavigateBack: () -> Unit,
) {
    val flagComposable: @Composable () -> Unit = when (screen) {
        is GatewayServersScreenState -> { -> GatewayIndicator(null) }
        is CitiesScreenState -> { -> Flag(exitCountry = screen.countryId) }
        is ServersScreenState -> { -> Flag(exitCountry = screen.countryId) }
    }

    val shouldAnimateTransition: Boolean
    val titleText: String
    val filterButtons: List<FilterButton>?
    val selectedCity: String?
    when (screen) {
        is CitiesScreenState -> {
            shouldAnimateTransition = false
            titleText = screen.countryId.label()
            filterButtons = screen.filterButtons
            selectedCity = null
        }

        is GatewayServersScreenState -> {
            shouldAnimateTransition = false
            filterButtons = null
            selectedCity = null
            titleText = screen.gatewayName
        }

        is ServersScreenState -> {
            shouldAnimateTransition = true
            filterButtons = null
            selectedCity = screen.city
            titleText = screen.countryId.label()
        }
    }

    var showSecondStepAnimations by remember { mutableStateOf(false) }
    LaunchedEffect(shouldAnimateTransition) {
        showSecondStepAnimations = if (shouldAnimateTransition) {
            delay(100)
            true
        } else {
            false
        }
    }
    val textPadding = 16.dp
    var rowWidth by remember { mutableIntStateOf(0) }
    Column(modifier) {
        Column(
            Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(32.dp)
                        .onGloballyPositioned {
                            // Get width of a row with Icon and flag and set it for city padding
                            rowWidth = it.size.width
                        }
                ) {
                    AnimatedVisibility(visible = shouldAnimateTransition) {
                        Icon(
                            painter = painterResource(id = CoreR.drawable.ic_arrow_left),
                            tint = ProtonTheme.colors.iconNorm,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onNavigateBack()
                                }
                        )
                    }
                    flagComposable()

                }
                Text(
                    text = titleText,
                    modifier = Modifier.padding(start = textPadding),
                    style = ProtonTheme.typography.headlineNorm
                )
            }
            AnimatedVisibility(visible = showSecondStepAnimations) {
                Text(
                    text = selectedCity ?: "",
                    modifier = Modifier
                        .padding(start = with(LocalDensity.current) {
                            rowWidth.toDp() + textPadding
                        }),
                    style = ProtonTheme.typography.defaultSmallWeak
                )
            }
        }

        val filterButtonsTransition = remember {
            MutableTransitionState(filterButtons)
        }
        AnimatedVisibility(visible = !showSecondStepAnimations) {
            val buttons = filterButtons ?: filterButtonsTransition.currentState
            buttons?.let {
                FiltersRow(
                    buttonActions = it,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

@Preview
@Composable
fun BottomSheetHeaderCitySelectionPreview() {
    AnimatedBottomSheetHeader(
        screen = CitiesScreenState(
            savedState = CitiesScreenSaveState(
                CountryId("CH"),
                ServerListFilter(),
                "test"
            ),
            filterButtons = listOf(
                FilterButton(
                    ServerFilterType.All,
                    label = R.string.country_filter_all,
                    onClick = {},
                    isSelected = true
                ),
                FilterButton(
                    ServerFilterType.SecureCore,
                    label = R.string.country_filter_secure_core,
                    onClick = {},
                    isSelected = false
                )
            ),
            items = emptyList()
        ),
        onNavigateBack = {}
    )
}

@Preview
@Composable
fun BottomSheetHeaderGatewayPreview() {
    AnimatedBottomSheetHeader(
        screen = GatewayServersScreenState(
            savedState = GatewayServersScreenSaveState(
                "Gateway",
                ServerListFilter(), "test"
            ),
            items = emptyList()
        ),
        onNavigateBack = {}
    )
}