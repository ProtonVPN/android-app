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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.Flag
import com.protonvpn.android.redesign.base.ui.GatewayIndicator
import com.protonvpn.android.redesign.base.ui.InfoButton
import com.protonvpn.android.redesign.base.ui.InfoSheetState
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
    screen: ServerGroupsSubScreenState,
    onNavigateBack: suspend (suspend () -> Unit) -> Unit,
    onNavigateToItem: (ServerGroupUiItem.ServerGroup) -> Unit,
    onItemClicked: (ServerGroupUiItem.ServerGroup) -> Unit,
    navigateToUpsell: (ServerGroupUiItem.BannerType) -> Unit,
    onClose: () -> Unit,
    infoSheetState: InfoSheetState,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    val headerAreaColor by animateColorAsState(
        targetValue =
            if (sheetState.targetValue == SheetValue.Expanded) ProtonTheme.colors.backgroundSecondary
            else ProtonTheme.colors.backgroundNorm,
        animationSpec = tween(durationMillis = 500),
        label = "BottomSheetTopColorAnimation"
    )
    val listStatesMap = remember { mutableMapOf<String, LazyListState>() }
    val listState = listStatesMap.getOrPut(screen.rememberStateKey) { rememberLazyListState() }
    val onCloseWithClearState = {
        listStatesMap.clear()
        onClose()
    }
    val onNavigateBackWithClearState: suspend (suspend () -> Unit) -> Unit = { onBack ->
        listStatesMap.remove(screen.rememberStateKey)
        onNavigateBack(onBack)
    }
    ModalBottomSheetWithBackNavigation(
        modifier = modifier,
        containerColor = headerAreaColor,
        // The content needs extend under the navigation bar to cover headerAreaColor.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
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
            infoSheetState = infoSheetState,
        )
    }
}

val ServerGroupsSubScreenState.rememberStateKey get() = when (this) {
    is CitiesScreenState -> "country_view"
    is GatewayServersScreenState -> "gateway_view"
    is ServersScreenState -> "servers_view"
}

@Composable
private fun BottomSheetScreen(
    screen: ServerGroupsSubScreenState,
    listState: LazyListState,
    infoSheetState: InfoSheetState,
    modifier: Modifier = Modifier,
    onItemOpen: (ServerGroupUiItem.ServerGroup) -> Unit,
    onItemClick: (ServerGroupUiItem.ServerGroup) -> Unit,
    navigateToUpsell: (ServerGroupUiItem.BannerType) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val onOpenInfo = { infoType: InfoType -> infoSheetState.show(infoType) }
    Column(modifier) {
        AnimatedBottomSheetHeader(
            screen = screen,
            onNavigateBack = onNavigateBack,
            onOpenInfo = onOpenInfo
        )
        ServerGroupItemsList(
            listState = listState,
            items = screen.items,
            onItemOpen = onItemOpen,
            onItemClick = onItemClick,
            onOpenInfo = onOpenInfo,
            navigateToUpsell = navigateToUpsell,
            modifier = Modifier
                .semantics { traversalIndex = -1f }
                .fillMaxHeight()
                .background(ProtonTheme.colors.backgroundNorm)
        )
    }
}

@Composable
private fun AnimatedBottomSheetHeader(
    modifier: Modifier = Modifier,
    screen: ServerGroupsSubScreenState,
    onNavigateBack: () -> Unit,
    onOpenInfo: (InfoType) -> Unit,
) {
    val flagComposable: @Composable () -> Unit = when (screen) {
        is GatewayServersScreenState -> { -> GatewayIndicator(null) }
        is CitiesScreenState -> { -> Flag(exitCountry = screen.countryId) }
        is ServersScreenState -> { -> Flag(exitCountry = screen.countryId) }
    }

    val canNavigateBack: Boolean
    val titleText: String
    val filterButtons: List<FilterButton>?
    val cityStateDisplay: String?
    when (screen) {
        is CitiesScreenState -> {
            canNavigateBack = false
            titleText = screen.countryId.label()
            filterButtons = screen.filterButtons
            cityStateDisplay = null
        }

        is GatewayServersScreenState -> {
            canNavigateBack = false
            filterButtons = null
            cityStateDisplay = null
            titleText = screen.gatewayName
        }

        is ServersScreenState -> {
            canNavigateBack = true
            filterButtons = null
            cityStateDisplay = screen.cityStateDisplay
            titleText = screen.countryId.label()
        }
    }

    var showSecondStepAnimations by remember { mutableStateOf(false) }
    LaunchedEffect(canNavigateBack) {
        showSecondStepAnimations = if (canNavigateBack) {
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
                    AnimatedVisibility(visible = canNavigateBack) {
                        Icon(
                            painter = painterResource(id = CoreR.drawable.ic_arrow_left),
                            tint = ProtonTheme.colors.iconNorm,
                            contentDescription = stringResource(R.string.accessibility_back),
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
                    text = cityStateDisplay ?: "",
                    modifier = Modifier
                        .padding(start = with(LocalDensity.current) {
                            rowWidth.toDp() + textPadding
                        }),
                    style = ProtonTheme.typography.defaultSmallWeak
                )
            }
        }

        if (screen is CitiesScreenState) {
            val hostCountryId = screen.hostCountryId
            if (hostCountryId != null) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, bottom = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(id = CoreR.drawable.ic_proton_globe),
                        contentDescription = null,
                        tint = ProtonTheme.colors.iconWeak,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = stringResource(R.string.country_smart_routing_info, hostCountryId.label(), screen.countryId.label()),
                        style = ProtonTheme.typography.body2Regular,
                        color = ProtonTheme.colors.textWeak,
                        modifier = Modifier.weight(1f).padding(top = 4.dp)
                    )
                    InfoButton(info = InfoType.SmartRouting, onOpenInfo)
                }
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
            countryId = CountryId("DE"),
            selectedFilter = ServerFilterType.All,
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
            items = emptyList(),
            hostCountryId = CountryId("CH")
        ),
        onNavigateBack = {},
        onOpenInfo = {}
    )
}

@Preview
@Composable
fun BottomSheetHeaderGatewayPreview() {
    AnimatedBottomSheetHeader(
        screen = GatewayServersScreenState(
            gatewayName = "Gateway",
            items = emptyList()
        ),
        onNavigateBack = {},
        onOpenInfo = {}
    )
}
