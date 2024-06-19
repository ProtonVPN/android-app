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

package com.protonvpn.android.tv.settings.splittunneling

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Checkbox
import androidx.tv.material3.Icon
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.optional
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.tv.settings.TvListRow
import com.protonvpn.android.tv.ui.TvSpinner
import com.protonvpn.android.tv.ui.TvUiConstants
import com.protonvpn.android.ui.settings.LabeledItem
import com.protonvpn.android.ui.settings.SplitTunnelingAppsViewModelHelper
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun TvSettingsSplitTunnelingAppsRoute(
    mode: SplitTunnelingMode,
    viewModel: TvSettingsSplitTunnelingAppsVM = hiltViewModel()
) {
    val viewState = viewModel.viewState.collectAsStateWithLifecycle().value
    TvSettingsSplitTunnelingApps(
        mode = mode,
        viewState = viewState,
        onAdd = viewModel::addApp,
        onRemove = viewModel::removeApp,
        onToggleLoadSystemApps = viewModel::toggleLoadSystemApps
    )
}

@Composable
private fun TvSettingsSplitTunnelingApps(
    mode: SplitTunnelingMode,
    viewState: SplitTunnelingAppsViewModelHelper.ViewState,
    onAdd: (LabeledItem) -> Unit,
    onRemove: (LabeledItem) -> Unit,
    onToggleLoadSystemApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(top = TvUiConstants.ScreenPaddingVertical)
            .padding(horizontal = TvUiConstants.ScreenPaddingHorizontal)
    ) {
        val titleRes = when (mode) {
            SplitTunnelingMode.INCLUDE_ONLY -> R.string.settings_split_tunneling_included_apps
            SplitTunnelingMode.EXCLUDE_ONLY -> R.string.settings_split_tunneling_excluded_apps
        }
        Text(
            stringResource(titleRes),
            style = ProtonTheme.typography.hero,
            modifier = Modifier.padding(horizontal = TvUiConstants.SelectionPaddingHorizontal, vertical = 24.dp)
        )

        when (viewState) {
            SplitTunnelingAppsViewModelHelper.ViewState.Loading ->
                Loading()
            is SplitTunnelingAppsViewModelHelper.ViewState.Content ->
                AppsSelection(
                    mode = mode,
                    viewState = viewState,
                    onAdd = onAdd,
                    onRemove = onRemove,
                    onToggleLoadSystemApps = onToggleLoadSystemApps,
                    modifier = Modifier.fillMaxWidth()
                )
        }
    }
}

@Composable
private fun AppsSelection(
    mode: SplitTunnelingMode,
    viewState: SplitTunnelingAppsViewModelHelper.ViewState.Content,
    onAdd: (LabeledItem) -> Unit,
    onRemove: (LabeledItem) -> Unit,
    onToggleLoadSystemApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
    ) {
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        val selectedAppsTitleRes = when(mode) {
            SplitTunnelingMode.INCLUDE_ONLY -> R.string.settingsIncludedAppsSelectedHeader
            SplitTunnelingMode.EXCLUDE_ONLY -> R.string.settingsExcludedAppsSelectedHeader
        }
        val selectedAppsInfoRes = when(mode) {
            SplitTunnelingMode.INCLUDE_ONLY -> R.string.settingsSplitTunnelingIncludedAppsList
            SplitTunnelingMode.EXCLUDE_ONLY -> R.string.settingsSplitTunnelingExcludedAppsList
        }
        val removeClickLabelRes = when (mode) {
            SplitTunnelingMode.INCLUDE_ONLY -> R.string.accessibility_action_remove_included_app
            SplitTunnelingMode.EXCLUDE_ONLY -> R.string.accessibility_action_remove_excluded_app
        }

        AppsList(
            title = stringResource(selectedAppsTitleRes, viewState.selectedApps.size),
            infoItemText = stringResource(selectedAppsInfoRes),
            apps = viewState.selectedApps,
            itemTrailingIcon = CoreR.drawable.ic_proton_minus_circle_filled,
            onClick = { item ->
                with (focusManager) {
                    // Move focus before making changes, it's difficult to do in response to state change.
                    moveFocus(FocusDirection.Down) || moveFocus(FocusDirection.Up) || moveFocus(FocusDirection.Next)
                }
                onRemove(item)
            },
            clickLabel = stringResource(removeClickLabelRes),
            focusRequester = focusRequester,
            modifier = Modifier.weight(1f),
        )

        val availableSystemAppsCount = when (viewState.availableSystemApps) {
            is SplitTunnelingAppsViewModelHelper.SystemAppsState.Content -> viewState.availableSystemApps.apps.size
            else -> 0
        }
        val addClickLabelRes = when (mode) {
            SplitTunnelingMode.INCLUDE_ONLY -> R.string.accessibility_action_add_included_app
            SplitTunnelingMode.EXCLUDE_ONLY -> R.string.accessibility_action_add_excluded_app
        }
        AppsList(
            title = stringResource(
                R.string.settingsSplitTunnelingAvailableHeader,
                viewState.availableRegularApps.size + availableSystemAppsCount
            ),
            viewState.availableRegularApps,
            CoreR.drawable.ic_proton_plus_circle_filled,
            onClick = { item ->
                with (focusManager) {
                    moveFocus(FocusDirection.Down) || moveFocus(FocusDirection.Up) || moveFocus(FocusDirection.Previous)
                }
                onAdd(item)
            },
            focusRequester = focusRequester,
            modifier = Modifier.weight(1f),
            systemApps = viewState.availableSystemApps,
            clickLabel = stringResource(addClickLabelRes),
            onToggleLoadSystemApps = onToggleLoadSystemApps,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun AppsList(
    title: String,
    apps: List<LabeledItem>,
    @DrawableRes itemTrailingIcon: Int,
    onClick: (LabeledItem) -> Unit,
    clickLabel: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    infoItemText: String? = null,
    systemApps: SplitTunnelingAppsViewModelHelper.SystemAppsState? = null,
    onToggleLoadSystemApps: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .focusGroup()
    ) {
        Text(
            title,
            style = ProtonTheme.typography.body2Medium,
            color = ProtonTheme.colors.textAccent,
            modifier = Modifier.padding(
                start = TvUiConstants.SelectionPaddingHorizontal,
                end = TvUiConstants.SelectionPaddingHorizontal,
                top = 4.dp,
                bottom = 8.dp
            )
        )

        ProvideTextStyle(
            ProtonTheme.typography.body2Regular
        ) {
            val listState = rememberTvLazyListState()
            TvLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxHeight()
            ) {
                if (infoItemText != null) {
                    item(key = "info item", contentType = "info item") {
                        Box( // The box is needed only to vertically-align the text.
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .heightIn(min = 56.dp)
                                .padding(horizontal = TvUiConstants.SelectionPaddingHorizontal, vertical = 12.dp)
                        ) {
                            Text(infoItemText, color = ProtonTheme.colors.textWeak)
                        }
                    }
                }
                itemsIndexed(apps, key = { _, item -> item.id }, contentType = { _, _ -> "app item" }) { index, item ->
                    AppItemRow(
                        item,
                        itemTrailingIcon,
                        onClick = { onClick(item) },
                        clickLabel = clickLabel,
                        modifier = Modifier
                            .optional({ index == 0 }, Modifier.focusRequester(focusRequester))
                            .animateItemPlacement()
                    )
                }
                if (systemApps != null) {
                    item(key = "system apps checkbox", contentType = "system apps checkbox") {
                        SystemAppsCheckboxRow(
                            systemApps,
                            requireNotNull(onToggleLoadSystemApps),
                            Modifier.animateItemPlacement()
                        )
                    }
                    when (systemApps) {
                        is SplitTunnelingAppsViewModelHelper.SystemAppsState.Loading ->
                            item(key = "system apps spinner", contentType = "system apps spinner") {
                                LaunchedEffect(Unit) {
                                    listState.animateScrollToItem(apps.size + 1)
                                }
                                SystemAppsLoadingRow(Modifier.animateItemPlacement())
                            }
                        is SplitTunnelingAppsViewModelHelper.SystemAppsState.Content ->
                            items(systemApps.apps, key = { it.id }, contentType = { "app item" }) { item ->
                                AppItemRow(
                                    item,
                                    itemTrailingIcon,
                                    onClick = { onClick(item) },
                                    clickLabel = clickLabel,
                                    modifier = Modifier.animateItemPlacement()
                                )
                            }
                        is SplitTunnelingAppsViewModelHelper.SystemAppsState.NotLoaded -> {}
                    }
                }
                // This should be done with contentPadding on the TvLazyColumn but it breaks item animations.
                item(key = "padding bottom", contentType = "padding bottom") {
                    Box(
                        Modifier
                            .height(TvUiConstants.ScreenPaddingVertical)
                            .animateItemPlacement()
                    )
                }
            }
        }
    }
}

@Composable
private fun AppItemRow(
    item: LabeledItem,
    @DrawableRes trailingIcon: Int,
    onClick: () -> Unit,
    clickLabel: String,
    modifier: Modifier = Modifier
) {
    TvListRow(
        onClick = onClick,
        clickSound = false, // Clicking moves focus which also produces sound.
        verticalAlignment = Alignment.CenterVertically,
        verticalContentPadding = 16.dp ,
        modifier = modifier.semantics {
            this.onClick(label = clickLabel, action = null)
        },
    ) {
        val iconModifier = Modifier.clip(ProtonTheme.shapes.small)
        if (item.iconDrawable != null) {
            val imageBitmap = with(item.iconDrawable) {
                // The app icon bitmaps can be of different sizes, use the drawable bounds.
                toBitmap(width = bounds.width(), height = bounds.height()).asImageBitmap()
            }
            Image(bitmap = imageBitmap, contentDescription = null, modifier = iconModifier)
        } else {
            Image(painter = painterResource(item.iconRes), contentDescription = null, modifier = iconModifier)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(painterResource(trailingIcon), contentDescription = null)
    }
}

@Composable
private fun SystemAppsCheckboxRow(
    systemApps: SplitTunnelingAppsViewModelHelper.SystemAppsState,
    onToggleLoadSystemApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val systemAppsChecked =
        systemApps !is SplitTunnelingAppsViewModelHelper.SystemAppsState.NotLoaded
    TvListRow(
        onClick = onToggleLoadSystemApps,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .semantics {
                toggleableState = ToggleableState(systemAppsChecked)
            },
    ) {
        Text(
            stringResource(R.string.settingsSplitTunnelAppsShowSystemAppsCheckbox),
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = systemAppsChecked,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

@Composable
private fun SystemAppsLoadingRow(
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(vertical = 40.dp)
            .fillMaxWidth(),
    ) {
        TvSpinner()
    }
}

@Composable
private fun Loading(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .padding(bottom = TvUiConstants.ScreenPaddingVertical),
        contentAlignment = Alignment.Center
    ) {
        TvSpinner()
    }
}
