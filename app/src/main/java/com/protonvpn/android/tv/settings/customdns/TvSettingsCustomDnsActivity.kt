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

package com.protonvpn.android.tv.settings.customdns

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.Cancel
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component1
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component2
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component3
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component4
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.redesign.base.ui.collectAsEffect
import com.protonvpn.android.tv.buttons.TvSolidButton
import com.protonvpn.android.tv.dialogs.TvAlertDialog
import com.protonvpn.android.tv.drawers.TvModalDrawer
import com.protonvpn.android.tv.settings.TvListRow
import com.protonvpn.android.tv.settings.TvSettingsMainToggleLayout
import com.protonvpn.android.tv.settings.TvSettingsMainWarningBanner
import com.protonvpn.android.tv.settings.TvSettingsOptionsMenu
import com.protonvpn.android.tv.settings.TvSettingsOptionsMenuItem
import com.protonvpn.android.tv.settings.TvSettingsReconnectDialog
import com.protonvpn.android.tv.settings.customdns.add.TvSettingsAddCustomDnsActivity
import com.protonvpn.android.tv.ui.TvUiConstants
import com.protonvpn.android.utils.openWifiSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.receiveAsFlow
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.compose.tv.theme.ProtonThemeTv

@AndroidEntryPoint
class TvSettingsCustomDnsActivity : BaseTvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ProtonThemeTv {
                val viewModel = hiltViewModel<TvSettingsCustomDnsViewModel>()
                val viewState = viewModel.viewStateFlow.collectAsStateWithLifecycle().value
                val dialogState = viewModel.dialogStateFlow.collectAsStateWithLifecycle().value
                val context = LocalContext.current
                var isSettingsChangedToastShown by rememberSaveable { mutableStateOf(value = false) }

                BackHandler(enabled = viewState?.areCustomDnsSettingsChanged == true) {
                    viewModel.onShowReconnectNowDialog(vpnUiDelegate = getVpnUiDelegate())
                }

                LaunchedEffect(key1 = viewState?.areCustomDnsSettingsChanged) {
                    if (viewState?.areCustomDnsSettingsChanged == true && !isSettingsChangedToastShown) {
                        isSettingsChangedToastShown = true

                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_changes_apply_on_reconnect_toast),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }

                viewModel.eventChannelReceiver.receiveAsFlow().collectAsEffect { event ->
                    when (event) {
                        TvSettingsCustomDnsViewModel.Event.OnClose -> finish()
                    }
                }

                when (viewState) {
                    is TvSettingsCustomDnsViewModel.ViewState.CustomDns -> {
                        BackHandler(enabled = viewState.showOptionsDrawer) {
                            viewModel.onCloseSelectedCustomDnsOptions()
                        }

                        TvSettingsCustomDns(
                            modifier = Modifier.fillMaxWidth(),
                            viewState = viewState,
                            onToggled = viewModel::onToggleIsCustomDnsEnabled,
                            onSelectedCustomDns = viewModel::onCustomDnsSelected,
                            onMoveCustomDnsToTop = viewModel::onMoveCustomDnsToTop,
                            onMoveCustomDnsUp = viewModel::onMoveCustomDnsUp,
                            onMoveCustomDnsDown = viewModel::onMoveCustomDnsDown,
                            onDeleteCustomDns = viewModel::onShowDeleteCustomDnsDialog,
                            onAddNewCustomDns = {
                                openAddCustomDns()
                            },
                        )
                    }

                    is TvSettingsCustomDnsViewModel.ViewState.Empty -> {
                        TvSettingsCustomDnsEmpty(
                            modifier = Modifier.fillMaxSize(),
                            onAddNewCustomDns = ::openAddCustomDns,
                        )
                    }

                    is TvSettingsCustomDnsViewModel.ViewState.PrivateDnsConflict -> {
                        TvSettingsCustomDnsConflict(
                            modifier = Modifier.fillMaxWidth(),
                            onConflictActionClicked = { openWifiSettings(isTv = true) },
                        )
                    }

                    null -> Unit
                }

                when (dialogState) {
                    is TvSettingsCustomDnsViewModel.DialogState.Delete -> {
                        TvAlertDialog(
                            title = stringResource(
                                id = R.string.tv_dialog_delete_server_title,
                                dialogState.customDns,
                            ),
                            confirmText = stringResource(id = R.string.delete),
                            dismissText = stringResource(id = R.string.cancel),
                            focusedButton = DialogInterface.BUTTON_POSITIVE,
                            onDismissRequest = viewModel::onDismissDeleteCustomDnsDialog,
                            onConfirm = {
                                viewModel.onDeleteCustomDns(
                                    selectedCustomDns = TvSettingsCustomDnsViewModel.SelectedCustomDns(
                                        index = dialogState.index,
                                        customDns = dialogState.customDns,
                                    )
                                )
                            }
                        )
                    }

                    TvSettingsCustomDnsViewModel.DialogState.Reconnect -> {
                        TvSettingsReconnectDialog(
                            onReconnectNow = {
                                viewModel.onReconnectNow(vpnUiDelegate = getVpnUiDelegate())
                            },
                            onDismissRequest = viewModel::onDismissReconnectNowDialog,
                        )
                    }

                    null -> Unit
                }
            }
        }
    }

    private fun openAddCustomDns() {
        val intent = Intent(this, TvSettingsAddCustomDnsActivity::class.java)

        startActivity(intent)
    }

}

@Composable
private fun TvSettingsCustomDnsEmpty(
    onAddNewCustomDns: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(key1 = Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(width = 360.dp)
                .padding(horizontal = TvUiConstants.SelectionPaddingHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.tv_settings_custom_dns_header_image),
                contentDescription = null,
            )

            Text(
                text = stringResource(id = R.string.settings_custom_dns_title),
                style = ProtonTheme.typography.hero,
            )

            Text(
                text = stringResource(id = R.string.settings_custom_dns_description_tv),
                textAlign = TextAlign.Center,
                style = ProtonTheme.typography.body2Regular,
                color = ProtonTheme.colors.textWeak,
            )

            TvSolidButton(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .focusRequester(focusRequester = focusRequester),
                text = stringResource(id = R.string.settings_add_dns_title),
                onClick = onAddNewCustomDns,
            )
        }
    }
}

@Composable
private fun TvSettingsCustomDns(
    viewState: TvSettingsCustomDnsViewModel.ViewState.CustomDns,
    onToggled: () -> Unit,
    onSelectedCustomDns: (Int, String) -> Unit,
    onMoveCustomDnsToTop: (TvSettingsCustomDnsViewModel.SelectedCustomDns) -> Unit,
    onMoveCustomDnsUp: (TvSettingsCustomDnsViewModel.SelectedCustomDns) -> Unit,
    onMoveCustomDnsDown: (TvSettingsCustomDnsViewModel.SelectedCustomDns) -> Unit,
    onDeleteCustomDns: (TvSettingsCustomDnsViewModel.SelectedCustomDns) -> Unit,
    onAddNewCustomDns: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvModalDrawer(
        modifier = modifier,
        isDrawerOpen = viewState.showOptionsDrawer,
        drawerContent = {
            viewState.selectedCustomDns?.let { selectedCustomDns ->
                TvSettingsCustomDnsOptions(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 32.dp),
                    viewState = viewState,
                    onMoveToTop = onMoveCustomDnsToTop,
                    onMoveUp = onMoveCustomDnsUp,
                    onMoveDown = onMoveCustomDnsDown,
                    onDelete = onDeleteCustomDns,
                )
            }
        },
        content = {
            TvSettingsCustomDnsContent(
                modifier = Modifier.fillMaxWidth(),
                viewState = viewState,
                onAddNewCustomDns = onAddNewCustomDns,
                onCustomDnsSelected = onSelectedCustomDns,
                onToggled = onToggled,
            )
        }
    )
}

@Composable
private fun TvSettingsCustomDnsContent(
    viewState: TvSettingsCustomDnsViewModel.ViewState.CustomDns,
    onToggled: () -> Unit,
    onCustomDnsSelected: (Int, String) -> Unit,
    onAddNewCustomDns: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val customDnsDescriptionResId = remember(key1 = viewState.hasSingleCustomDns) {
        if (viewState.hasSingleCustomDns) R.string.settings_dns_list_description
        else R.string.settings_dns_list_description_multiple
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        TvSettingsMainToggleLayout(
            modifier = Modifier.widthIn(max = TvUiConstants.SingleColumnWidth),
            title = stringResource(id = R.string.settings_custom_dns_title),
            titleImageRes = R.drawable.tv_settings_custom_dns_header_image,
            toggleLabel = stringResource(id = R.string.settings_custom_dns_title),
            toggleValue = viewState.isCustomDnsEnabled,
            onToggled = onToggled,
        ) {
            item {
                Text(
                    modifier = Modifier
                        .padding(horizontal = TvUiConstants.SelectionPaddingHorizontal)
                        .padding(top = 12.dp, bottom = 16.dp),
                    text = stringResource(id = R.string.settings_custom_dns_description_tv),
                    style = ProtonTheme.typography.body2Regular,
                    color = ProtonTheme.colors.textWeak,
                )
            }

            if (viewState.isCustomDnsEnabled) {
                item {
                    Text(
                        modifier = Modifier
                            .padding(horizontal = TvUiConstants.SelectionPaddingHorizontal)
                            .padding(top = 12.dp, bottom = 16.dp),
                        text = stringResource(
                            id = R.string.settings_dns_list_title,
                            viewState.customDnsCount,
                        ),
                        style = ProtonTheme.typography.body2Medium,
                        color = ProtonTheme.colors.textNorm,
                    )
                }

                itemsIndexed(items = viewState.customDnsList) { index, customDns ->
                    TvListRow(
                        onClick = { onCustomDnsSelected(index, customDns) },
                    ) {
                        Text(
                            text = customDns,
                            style = ProtonTheme.typography.body1Regular,
                            color = ProtonTheme.colors.textNorm,
                        )
                    }
                }

                item {
                    Text(
                        modifier = Modifier
                            .padding(horizontal = TvUiConstants.SelectionPaddingHorizontal)
                            .padding(top = 12.dp, bottom = 16.dp),
                        text = stringResource(id = customDnsDescriptionResId),
                        style = ProtonTheme.typography.body2Regular,
                        color = ProtonTheme.colors.textHint,
                    )
                }

                item {
                    TvSolidButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.settings_add_dns_title),
                        onClick = onAddNewCustomDns,
                    )
                }

                item {
                    Spacer(
                        modifier = Modifier.height(height = 16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvSettingsCustomDnsOptions(
    viewState: TvSettingsCustomDnsViewModel.ViewState.CustomDns,
    onMoveToTop: (TvSettingsCustomDnsViewModel.SelectedCustomDns) -> Unit,
    onMoveUp: (TvSettingsCustomDnsViewModel.SelectedCustomDns) -> Unit,
    onMoveDown: (TvSettingsCustomDnsViewModel.SelectedCustomDns) -> Unit,
    onDelete: (TvSettingsCustomDnsViewModel.SelectedCustomDns) -> Unit,
    modifier: Modifier,
) {
    viewState.selectedCustomDns?.let { selectedCustomDns ->
        val focusRequester = remember { FocusRequester() }

        val (moveTop, moveUp, moveDown, delete) = FocusRequester.createRefs()

        LaunchedEffect(key1 = selectedCustomDns) {
            focusRequester.requestFocus()
        }

        TvSettingsOptionsMenu(
            modifier = modifier.focusRequester(focusRequester),
            title = selectedCustomDns.customDns,
            titleMaxLines = 1,
            titleOverflow = TextOverflow.Ellipsis,
        ) {
            if (viewState.canMoveUp) {
                item {
                    TvSettingsOptionsMenuItem(
                        modifier = Modifier
                            .focusRequester(moveTop)
                            .focusProperties {
                                left = Cancel
                                right = Cancel
                                up = Cancel
                                down = moveUp
                            },
                        text = stringResource(id = R.string.menu_option_move_to_top),
                        onClick = { onMoveToTop(selectedCustomDns) },
                    )
                }

                item {
                    TvSettingsOptionsMenuItem(
                        modifier = Modifier
                            .focusRequester(moveUp)
                            .focusProperties {
                                left = Cancel
                                right = Cancel
                                up = moveTop
                                down = if (viewState.canMoveDown) moveDown else delete
                            },
                        text = stringResource(id = R.string.menu_option_move_up),
                        onClick = { onMoveUp(selectedCustomDns) },
                    )
                }
            }

            if (viewState.canMoveDown) {
                item {
                    TvSettingsOptionsMenuItem(
                        modifier = Modifier
                            .focusRequester(moveDown)
                            .focusProperties {
                                left = Cancel
                                right = Cancel
                                up = if (viewState.canMoveUp) moveUp else moveDown
                                down = delete
                            },
                        text = stringResource(id = R.string.menu_option_move_down),
                        onClick = { onMoveDown(selectedCustomDns) },
                    )
                }
            }

            item {
                TvSettingsOptionsMenuItem(
                    modifier = Modifier
                        .focusRequester(delete)
                        .focusProperties {
                            left = Cancel
                            right = Cancel
                            up = if (viewState.canMoveDown) moveDown else moveUp
                            down = Cancel
                        },
                    text = stringResource(id = R.string.delete),
                    onClick = { onDelete(selectedCustomDns) },
                )
            }
        }
    }
}

@Composable
private fun TvSettingsCustomDnsConflict(
    onConflictActionClicked: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        TvSettingsMainWarningBanner(
            modifier = Modifier.widthIn(max = TvUiConstants.SingleColumnWidth),
            headerImageRes = R.drawable.tv_settings_custom_dns_header_image,
            headerTitle = stringResource(id = R.string.settings_custom_dns_title),
            headerDescription = stringResource(id = R.string.settings_custom_dns_description_tv),
            bannerTitle = stringResource(id = R.string.private_dns_conflict_banner_custom_dns_title),
            bannerDescription = stringResource(id = R.string.private_dns_conflict_banner_custom_dns_description_tv),
            actionText = stringResource(id = R.string.private_dns_conflict_banner_network_settings_button),
            onActionClicked = onConflictActionClicked,
        )
    }
}
