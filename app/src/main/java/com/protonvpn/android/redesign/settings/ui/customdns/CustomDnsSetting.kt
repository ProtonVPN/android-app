/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.redesign.settings.ui.customdns

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat.performHapticFeedback
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.AnnotatedClickableText
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.redesign.base.ui.ProtonSnackbar
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.redesign.settings.ui.DnsConflictBanner
import com.protonvpn.android.redesign.settings.ui.FeatureSubSettingScaffold
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel.SettingViewState
import com.protonvpn.android.redesign.settings.ui.addFeatureSettingItems
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.utils.showToast
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import me.proton.core.presentation.R as CoreR


@Composable
fun CustomDnsScreen(
    onClose: () -> Unit,
    onDnsToggled: () -> Unit,
    onLearnMore: () -> Unit,
    onDnsChange: (List<String>) -> Unit,
    onItemRemoved: (String) -> Unit,
    onAddNewAddress: () -> Unit,
    onPrivateDnsLearnMore: () -> Unit,
    onOpenPrivateDnsSettings: () -> Unit,
    showReconnectionDialog: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewState: SettingsViewModel.CustomDnsViewState,
) {
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val dnsViewState = viewState.dnsViewState

    val initialEnabled = rememberSaveable { dnsViewState.value }
    val initialCustomDns = rememberSaveable { dnsViewState.customDns.toList() }

    val valuesChanged = rememberSaveable { mutableStateOf(false) }
    val toastShown = rememberSaveable { mutableStateOf(false) }

    // Track any changes and show toast for reconnection on first data change
    LaunchedEffect(dnsViewState.value, dnsViewState.customDns) {
        val enabledChanged = dnsViewState.value != initialEnabled
        val dnsListChanged = dnsViewState.customDns.toList() != initialCustomDns
        valuesChanged.value = enabledChanged || dnsListChanged

        if (valuesChanged.value && !toastShown.value && viewState.isConnected) {
            toastShown.value = true
            Toast.makeText(
                context,
                context.getString(R.string.settings_changes_apply_on_reconnect_toast),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val handleClose = {
        if (valuesChanged.value) {
            showReconnectionDialog()
        }
        onClose()
    }

    BackHandler(onBack = handleClose)

    FeatureSubSettingScaffold(
        title = stringResource(id = dnsViewState.titleRes),
        onClose = handleClose,
        listState = listState,
        titleInListIndex = 1,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            val largeScreenModifier = Modifier.largeScreenContentPadding()
            when {
                viewState.dnsViewState.isPrivateDnsActive -> {
                    CustomDnsWithPrivateDnsConflict(
                        listState = listState,
                        settingViewState = dnsViewState,
                        onPrivateDnsLearnMore = onPrivateDnsLearnMore,
                        onOpenPrivateDnsSettings = onOpenPrivateDnsSettings,
                        largeScreenPaddingModifier = largeScreenModifier,
                    )
                }
                dnsViewState.customDns.isNotEmpty() -> {
                    CustomDnsContent(
                        listState = listState,
                        currentDnsList = dnsViewState.customDns,
                        onDnsChange = onDnsChange,
                        onCopyToClipboard = {
                            if (Build.VERSION.SDK_INT < 33) {
                                context.showToast(context.getString(R.string.copied_to_clipboard))
                            }
                            clipboardManager.setText(AnnotatedString(it))
                        },
                        settingViewState = dnsViewState,
                        onToggle = onDnsToggled,
                        onLearnMore = onLearnMore,
                        largeScreenPaddingModifier = largeScreenModifier,
                        onItemRemoved = onItemRemoved,
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> {
                    EmptyState(
                        dnsDescription = dnsViewState.descriptionRes,
                        onLearnMore = onLearnMore,
                        modifier = largeScreenModifier.weight(1f)
                    )
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
            ) {
                ProtonSnackbar(it)
            }
            if (viewState.showAddDnsButton) {
                VpnSolidButton(
                    text = stringResource(R.string.settings_add_dns_title),
                    onClick = onAddNewAddress,
                    modifier = largeScreenModifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    @StringRes dnsDescription: Int,
    onLearnMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
    ) {
        Image(
            painterResource(id = R.drawable.setting_custom_dns),
            contentDescription = null,
            Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.settings_custom_dns_title),
            style = ProtonTheme.typography.subheadline,
            color = ProtonTheme.colors.textNorm,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        AnnotatedClickableText(
            fullText = stringResource(dnsDescription),
            annotatedPart = stringResource(R.string.learn_more),
            onAnnotatedClick = onLearnMore,
            style = ProtonTheme.typography.body2Regular,
            annotatedStyle = ProtonTheme.typography.body2Medium,
            color = ProtonTheme.colors.textWeak,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CustomDnsWithPrivateDnsConflict(
    listState: LazyListState,
    settingViewState: SettingViewState<Boolean>,
    onPrivateDnsLearnMore: () -> Unit,
    onOpenPrivateDnsSettings: () -> Unit,
    largeScreenPaddingModifier: Modifier,
    modifier: Modifier = Modifier
) {
    val itemModifier = largeScreenPaddingModifier.padding(horizontal = 16.dp)
    LazyColumn(
        state = listState,
        modifier = modifier,
    ) {
        addFeatureSettingItems(
            setting = settingViewState,
            imageRes = R.drawable.setting_custom_dns,
            onLearnMore = onPrivateDnsLearnMore,
            itemModifier = itemModifier,
        )
        item {
            DnsConflictBanner(
                titleRes = R.string.private_dns_conflict_banner_custom_dns_title,
                descriptionRes = R.string.private_dns_conflict_banner_custom_dns_description,
                buttonRes = R.string.private_dns_conflict_banner_network_settings_button,
                onLearnMore = onPrivateDnsLearnMore,
                onButtonClicked = onOpenPrivateDnsSettings,
                modifier = itemModifier.padding(top = 24.dp),
            )
        }
    }
}

@Composable
private fun CustomDnsContent(
    listState: LazyListState,
    currentDnsList: List<String>,
    settingViewState: SettingViewState<Boolean>,
    onLearnMore: () -> Unit,
    onToggle: () -> Unit,
    onItemRemoved: (String) -> Unit,
    onDnsChange: (List<String>) -> Unit,
    onCopyToClipboard: (String) -> Unit,
    largeScreenPaddingModifier: Modifier,
    modifier: Modifier = Modifier
) {
    val dnsList = remember(currentDnsList) { currentDnsList.toMutableStateList() }
    var pendingDnsUpdate by remember { mutableStateOf(false) }
    val view = LocalView.current

    // Must take into account items before the reorderable items in LazyColumn
    val itemsBeforeList = 5

    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = from.index - itemsBeforeList
        val toIndex = to.index - itemsBeforeList

        if (fromIndex in dnsList.indices && toIndex in dnsList.indices) {
            val item = dnsList.removeAt(fromIndex)
            dnsList.add(toIndex, item)
            pendingDnsUpdate = true

            performHapticFeedback(view, HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK)
        }
    }

    // Call onDnsChange only after dragging has ended
    // To avoid updating state while user is still dragging
    LaunchedEffect(reorderableLazyListState.isAnyItemDragging) {
        if (!reorderableLazyListState.isAnyItemDragging && pendingDnsUpdate) {
            onDnsChange(dnsList)
            pendingDnsUpdate = false
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
    ) {
        addFeatureSettingItems(
            setting = settingViewState,
            imageRes = R.drawable.setting_custom_dns,
            onLearnMore = onLearnMore,
            onToggle = onToggle,
            itemModifier = largeScreenPaddingModifier.padding(horizontal = 16.dp)
        )

        if (settingViewState.value) {
            item {
                Text(
                    text = stringResource(id = R.string.settings_dns_list_title, currentDnsList.size),
                    style = ProtonTheme.typography.body2Medium,
                    modifier = largeScreenPaddingModifier.padding(16.dp)
                )
            }
            itemsIndexed(dnsList, key = { _, item -> item }) { index, item ->
                ReorderableItem(reorderableLazyListState, key = item) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp, label = "")
                    val backgroundColor by animateColorAsState(
                        targetValue = if (!isDragging) ProtonTheme.colors.backgroundNorm else ProtonTheme.colors.backgroundSecondary,
                        label = ""
                    )
                    val dragModifier = Modifier.draggableHandle(
                        onDragStarted = {
                            performHapticFeedback(
                                view,
                                HapticFeedbackConstantsCompat.GESTURE_START
                            )
                        },
                        onDragStopped = {
                            performHapticFeedback(
                                view,
                                HapticFeedbackConstantsCompat.GESTURE_END
                            )
                        }
                    )
                    // TODO(VPNAND-2125): this kind of logic belongs in the UI state or even viewmodel.
                    val onMoveUp = remember(index, currentDnsList, item, onDnsChange) {
                        { moveItemUp(currentDnsList, index, item, onDnsChange) }.takeIf { index > 0 }
                    }
                    val onMoveDown = remember(index, currentDnsList, item, onDnsChange) {
                        { moveItemDown(currentDnsList, index, item, onDnsChange) }
                            .takeIf { index < currentDnsList.size - 1 }
                    }
                    DnsListItem(
                        label = item,
                        elevation = elevation,
                        backgroundColor = backgroundColor,
                        onCopyToClipboard = onCopyToClipboard,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onDelete = { onItemRemoved(item) },
                        modifier = largeScreenPaddingModifier.animateItem(),
                        dragModifier = dragModifier
                    )
                }
            }
            item(key = "footer") {
                val label =
                    if (currentDnsList.size == 1) R.string.settings_dns_list_description
                    else R.string.settings_dns_list_description_multiple
                Text(
                    text = stringResource(id = label),
                    style = ProtonTheme.typography.body2Regular,
                    color = ProtonTheme.colors.textHint,
                    modifier = largeScreenPaddingModifier
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
                        .animateItem()
                )
            }
        }
    }
}

@Composable
private fun DnsListItem(
    label: String,
    elevation: Dp,
    backgroundColor: Color,
    onCopyToClipboard: (String) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier
) {
    val copyToClipboard = remember(label, onCopyToClipboard) { { onCopyToClipboard(label) } }

    val accessibilityActions = customAccessibilityActions(
        onMoveUp = onMoveUp, onMoveDown = onMoveDown, onCopyToClipboard = copyToClipboard, onDelete = onDelete
    )
    Surface(
        shadowElevation = elevation,
        color = backgroundColor,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                customActions = accessibilityActions
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { copyToClipboard() }
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(id = R.drawable.ic_dots),
                contentDescription = null,
                tint = LocalContentColor.current,
                modifier = dragModifier.padding(16.dp)
            )
            Text(
                text = label,
                style = ProtonTheme.typography.body1Regular,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.clearAndSetSemantics() {}
            ) {
                Icon(
                    painterResource(id = CoreR.drawable.ic_proton_trash),
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier
                        .size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }
    }
}

@Composable
private fun customAccessibilityActions(
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDelete: () -> Unit,
    onCopyToClipboard: () -> Unit,
): List<CustomAccessibilityAction> {
    @Composable
    fun createAction(@StringRes labelRes: Int, action: () -> Unit) =
        CustomAccessibilityAction(label = stringResource(labelRes), action = { action(); true })

    return buildList {
        if (onMoveUp != null)
            add(createAction(R.string.accessibility_action_item_move_up, onMoveUp))
        if (onMoveDown != null)
            add(createAction(R.string.accessibility_action_item_move_down, onMoveDown))
        add(createAction(R.string.accessibility_action_item_delete, onDelete))
        add(createAction(R.string.accessibility_action_copy_to_clipboard, onCopyToClipboard))
    }
}

private fun moveItemUp(list: List<String>, index: Int, item: String, onDnsChange: (List<String>) -> Unit) {
    val newList = list.toMutableList().apply {
        removeAt(index)
        add(index - 1, item)
    }
    onDnsChange(newList)
}

private fun moveItemDown(list: List<String>, index: Int, item: String, onDnsChange: (List<String>) -> Unit) {
    val newList = list.toMutableList().apply {
        removeAt(index)
        add(index + 1, item)
    }
    onDnsChange(newList)
}

@Preview
@Composable
private fun CustomDnsPreview() {
    ProtonVpnPreview {
        CustomDnsScreen(
            onClose = {},
            onDnsChange = {},
            onDnsToggled = {},
            onAddNewAddress = {},
            onLearnMore = {},
            onPrivateDnsLearnMore = {},
            onOpenPrivateDnsSettings = {},
            showReconnectionDialog = {},
            onItemRemoved = {},
            viewState = SettingsViewModel.CustomDnsViewState(
                dnsViewState = SettingViewState.CustomDns(
                    enabled = true,
                    customDns = listOf("1.1.1.1", "1.2.1.1"),
                    overrideProfilePrimaryLabel = null,
                    isFreeUser = false,
                    isPrivateDnsActive = false
                ),
                isConnected = false
            )
        )
    }
}

@Preview
@Composable
private fun CustomDnsConflictPreview() {
    ProtonVpnPreview {
        CustomDnsScreen(
            onClose = {},
            onDnsChange = {},
            onDnsToggled = {},
            onAddNewAddress = {},
            onLearnMore = {},
            onPrivateDnsLearnMore = {},
            onOpenPrivateDnsSettings = {},
            showReconnectionDialog = {},
            onItemRemoved = {},
            viewState = SettingsViewModel.CustomDnsViewState(
                dnsViewState = SettingViewState.CustomDns(
                    enabled = false,
                    customDns = listOf("1.1.1.1", "1.2.1.1"),
                    overrideProfilePrimaryLabel = null,
                    isFreeUser = false,
                    isPrivateDnsActive = true,
                ),
                isConnected = false
            )
        )
    }
}

@Preview
@Composable
private fun CustomDnsDisabledPreview() {
    ProtonVpnPreview {
        CustomDnsScreen(
            onClose = {},
            onDnsChange = {},
            onDnsToggled = {},
            onAddNewAddress = {},
            onLearnMore = {},
            onPrivateDnsLearnMore = {},
            onOpenPrivateDnsSettings = {},
            showReconnectionDialog = {},
            onItemRemoved = {},
            viewState = SettingsViewModel.CustomDnsViewState(
                dnsViewState = SettingViewState.CustomDns(
                    enabled = false,
                    customDns = emptyList(),
                    overrideProfilePrimaryLabel = null,
                    isFreeUser = false,
                    isPrivateDnsActive = true
                ),
                isConnected = false
            )
        )
    }
}

@Preview
@Composable
private fun CustomDnsEmptyState() {
    ProtonVpnPreview {
        CustomDnsScreen(
            onClose = {},
            onDnsChange = {},
            onDnsToggled = {},
            onAddNewAddress = {},
            onLearnMore = {},
            onPrivateDnsLearnMore = {},
            onOpenPrivateDnsSettings = {},
            showReconnectionDialog = {},
            onItemRemoved = {},
            viewState = SettingsViewModel.CustomDnsViewState(
                dnsViewState = SettingViewState.CustomDns(
                    enabled = false,
                    customDns = emptyList(),
                    overrideProfilePrimaryLabel = null,
                    isFreeUser = false,
                    isPrivateDnsActive = false,
                ),
                isConnected = false
            )
        )
    }
}
