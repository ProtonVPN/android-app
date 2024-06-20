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

import android.content.Context
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.LocalVpnUiDelegate
import com.protonvpn.android.redesign.base.ui.collectAsEffect
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.tv.settings.TvSettingsHeader
import com.protonvpn.android.tv.settings.TvSettingsItem
import com.protonvpn.android.tv.settings.TvSettingsItemRadioSmall
import com.protonvpn.android.tv.settings.TvSettingsItemSwitch
import com.protonvpn.android.tv.settings.TvSettingsReconnectDialog
import com.protonvpn.android.tv.ui.ProtonTvDialogBasic
import com.protonvpn.android.tv.ui.TvUiConstants
import com.protonvpn.android.tv.ui.onFocusLost
import com.protonvpn.android.ui.settings.formatSplitTunnelingItems
import com.protonvpn.android.userstorage.DontShowAgainStore
import com.protonvpn.android.utils.DebugUtils
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.compose.tv.theme.ProtonThemeTv
import me.proton.core.presentation.R as CoreR

@Composable
fun TvSettingsSplitTunnelingMainRoute(
    navigateEditApps: (SplitTunnelingMode) -> Unit,
    navigateBack: () -> Unit,
    viewModel: TvSettingsSplitTunnelingMainVM = hiltViewModel(),
) {
    val vpnUiDelegate = LocalVpnUiDelegate.current
    val viewState = viewModel.mainViewState.collectAsStateWithLifecycle().value
    val showReconnectDialog = viewModel.showReconnectDialogFlow.collectAsStateWithLifecycle().value

    BackHandler {
        viewModel.onNavigateBack(vpnUiDelegate)
    }
    viewModel.eventNavigateBack.collectAsEffect { navigateBack() }

    if (viewState != null) {
        TvSettingsSplitTunnelingMain(
            viewState = viewState,
            showReconnectDialog = showReconnectDialog,
            onToggleEnabled = { viewModel.onToggleEnabled(vpnUiDelegate) },
            onModeChanged = viewModel::onModeChanged,
            onClickApps = navigateEditApps,
            onReconnectNow = { viewModel.onReconnectClicked(vpnUiDelegate, false) },
            onReconnectDismissed = { viewModel.dismissReconnectDialog(false) },
            modifier = Modifier.widthIn(max = TvUiConstants.SingleColumnWidth)
        )
    }
}

@Composable
private fun TvSettingsSplitTunnelingMain(
    viewState: TvSettingsSplitTunnelingMainVM.MainViewState,
    showReconnectDialog: DontShowAgainStore.Type?,
    onToggleEnabled: () -> Unit,
    onModeChanged: (SplitTunnelingMode) -> Unit,
    onClickApps: (SplitTunnelingMode) -> Unit,
    onReconnectNow: () -> Unit,
    onReconnectDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var changeModeDialogShown by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = modifier
    ) {
         TvSettingsHeader(
            stringResource(R.string.settings_split_tunneling_title),
            R.drawable.tv_split_tunneling_header_image,
            modifier = Modifier.padding(top = TvUiConstants.ScreenPaddingVertical)
        )

        Column(
            // Center the content vertically, it should fit. If it doesn't (e.g. huge text size), it will scroll.
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(bottom = TvUiConstants.ScreenPaddingVertical)
                .verticalScroll(rememberScrollState())
                .weight(1f)
        ) {
            TvSettingsItemSwitch(
                stringResource(R.string.settings_split_tunneling_title),
                checked = viewState.isEnabled,
                onClick = onToggleEnabled,
                modifier = Modifier.focusRequester(focusRequester)
            )
            Text(
                stringResource(R.string.tv_settings_split_tunneling_description),
                style = ProtonTheme.typography.body2Regular,
                color = ProtonTheme.colors.textWeak,
                modifier = Modifier
                    .padding(horizontal = TvUiConstants.SelectionPaddingHorizontal)
                    .padding(top = 12.dp, bottom = 16.dp)
            )

            val modeStandard = viewState.mode == SplitTunnelingMode.EXCLUDE_ONLY
            val modeLabel =
                if (modeStandard) R.string.settings_split_tunneling_mode_standard
                else R.string.settings_split_tunneling_mode_inverse
            val appsLabel =
                if (modeStandard) R.string.settings_split_tunneling_excluded_apps
                else R.string.settings_split_tunneling_included_apps
            TvSettingsItem(
                title = stringResource(R.string.settings_split_tunneling_mode_title),
                description = stringResource(modeLabel),
                onClick = { changeModeDialogShown = true },
            )
            TvSettingsItem(
                title = stringResource(appsLabel),
                description = formatSplitTunnelingItems(viewState.currentModeApps),
                iconRes = CoreR.drawable.ic_proton_squares_in_square,
                onClick = { onClickApps(viewState.mode) },
            )
        }
    }

    if (changeModeDialogShown) {
        ChangeModeDialog(
            isStandardSelected = viewState.mode == SplitTunnelingMode.EXCLUDE_ONLY,
            onStandardSelected = { onModeChanged(SplitTunnelingMode.EXCLUDE_ONLY) },
            onInverseSelected = { onModeChanged(SplitTunnelingMode.INCLUDE_ONLY) },
            onDismissRequest = { changeModeDialogShown = false }
        )
    }
    if (showReconnectDialog != null) {
        DebugUtils.debugAssert("Unsupported dialog type") {
            showReconnectDialog == DontShowAgainStore.Type.SplitTunnelingChangeWhenConnected
        }
        TvSettingsReconnectDialog(onReconnectNow = onReconnectNow, onDismissRequest = onReconnectDismissed)
    }
}

@Composable
private fun ChangeModeDialog(
    isStandardSelected: Boolean,
    onStandardSelected: () -> Unit,
    onInverseSelected: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ProtonTvDialogBasic(
        onDismissRequest = onDismissRequest
    ) { focusRequester ->
        Column {
            Text(
                stringResource(R.string.settings_split_tunneling_mode_title),
                style = ProtonTheme.typography.headline,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            TvSettingsItemRadioSmall(
                checked = isStandardSelected,
                title = stringResource(R.string.settings_split_tunneling_mode_standard),
                description = stringResource(R.string.settings_split_tunneling_mode_description_standard),
                onClick = {
                    onStandardSelected()
                    onDismissRequest()
                },
                clickSound = false, // Closing the dialog removes focus and produces an additional sound.
                modifier = Modifier.focusRequester(focusRequester)
            )
            TvSettingsItemRadioSmall(
                checked = !isStandardSelected,
                title = stringResource(R.string.settings_split_tunneling_mode_inverse),
                description = stringResource(R.string.settings_split_tunneling_mode_description_inverse),
                onClick = {
                    onInverseSelected()
                    onDismissRequest()
                },
                clickSound = false, // Closing the dialog removes focus and produces an additional sound.
            )
        }
    }
}

@Preview
@Composable
private fun PreviewTvSettingsSplitTunneling() {
    ProtonThemeTv {
        TvSettingsSplitTunnelingMain(
            viewState = TvSettingsSplitTunnelingMainVM.MainViewState(
                isEnabled = false,
                mode = SplitTunnelingMode.EXCLUDE_ONLY,
                currentModeApps = listOf("App1", "app2")
            ),
            showReconnectDialog = null,
            onToggleEnabled = {},
            onModeChanged = {},
            onClickApps = {},
            onReconnectNow = {},
            onReconnectDismissed = {},
        )
    }
}
