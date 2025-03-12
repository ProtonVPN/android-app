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
package com.protonvpn.android.redesign.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.DIALOG_CONTENT_PADDING
import com.protonvpn.android.redesign.base.ui.ProtonBasicAlert
import com.protonvpn.android.redesign.base.ui.SettingsRadioItemSmall
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.ui.settings.formatSplitTunnelingItems
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultWeak
import me.proton.core.presentation.R as CoreR

@Composable
fun SplitTunnelingSubSetting(
    onClose: () -> Unit,
    splitTunneling: SettingsViewModel.SettingViewState.SplitTunneling,
    onLearnMore: () -> Unit,
    onSplitTunnelToggle: () -> Unit,
    onSplitTunnelModeSelected: (SplitTunnelingMode) -> Unit,
    onAppsClick: (SplitTunnelingMode) -> Unit,
    onIpsClick: (SplitTunnelingMode) -> Unit,
) {
    var changeModeDialogShown by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    FeatureSubSettingScaffold(
        title = stringResource(id = splitTunneling.titleRes),
        onClose = onClose,
        listState = listState,
        titleInListIndex = 1,
    ) { contentPadding ->
        val horizontalItemPaddingModifier = Modifier
            .largeScreenContentPadding()
            .padding(horizontal = 16.dp)
        val splitTunnelingMode = splitTunneling.mode
        val modeStandard = splitTunnelingMode == SplitTunnelingMode.EXCLUDE_ONLY
        val modeLabel =
            if (modeStandard) R.string.settings_split_tunneling_mode_standard
            else R.string.settings_split_tunneling_mode_inverse
        val appsLabel =
            if (modeStandard) R.string.settings_split_tunneling_excluded_apps
            else R.string.settings_split_tunneling_included_apps
        val ipsLabel =
            if (modeStandard) R.string.settings_split_tunneling_excluded_ips
            else R.string.settings_split_tunneling_included_ips
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(contentPadding)
        ) {
            addFeatureSettingItems(
                itemModifier = horizontalItemPaddingModifier,
                setting = splitTunneling,
                imageRes = R.drawable.setting_split_tunneling,
                onToggle = onSplitTunnelToggle,
                onLearnMore = onLearnMore,
            )
            if (splitTunneling.value) {
                item {
                    SettingRow(
                        title = stringResource(id = R.string.settings_split_tunneling_mode_title),
                        subtitleComposable = {
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(text = stringResource(modeLabel), style = ProtonTheme.typography.defaultWeak)
                        },
                        onClick = { changeModeDialogShown = true },
                        modifier = Modifier.largeScreenContentPadding().animateItem()
                    )
                }
                item {
                    SettingRowWithIcon(
                        icon = CoreR.drawable.ic_proton_mobile,
                        title = stringResource(id = appsLabel),
                        settingValue =
                            SettingValue.SettingText(formatSplitTunnelingItems(splitTunneling.currentModeAppNames)),
                        onClick = { onAppsClick(splitTunnelingMode) },
                        modifier = Modifier.largeScreenContentPadding().animateItem()
                    )
                }
                item {
                    SettingRowWithIcon(
                        icon = CoreR.drawable.ic_proton_window_terminal,
                        title = stringResource(id = ipsLabel),
                        settingValue =
                            SettingValue.SettingText(formatSplitTunnelingItems(splitTunneling.currentModeIps)),
                        onClick = { onIpsClick(splitTunnelingMode) },
                        modifier = Modifier.largeScreenContentPadding().animateItem()
                    )
                }
            }
        }
    }

    if (changeModeDialogShown) {
        ChangeModeDialog(
            isStandardSelected = splitTunneling.mode == SplitTunnelingMode.EXCLUDE_ONLY,
            onStandardSelected = { onSplitTunnelModeSelected(SplitTunnelingMode.EXCLUDE_ONLY) },
            onInverseSelected = { onSplitTunnelModeSelected(SplitTunnelingMode.INCLUDE_ONLY) },
            onDismissRequest = { changeModeDialogShown = false }
        )
    }
}

@Composable
private fun ChangeModeDialog(
    isStandardSelected: Boolean,
    onStandardSelected: () -> Unit,
    onInverseSelected: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ProtonBasicAlert(
        onDismissRequest = onDismissRequest,
    ) {
        Column {
            Text(
                stringResource(R.string.settings_split_tunneling_mode_title),
                style = ProtonTheme.typography.body1Bold,
                modifier = Modifier.padding(horizontal = DIALOG_CONTENT_PADDING)
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingsRadioItemSmall(
                title = stringResource(R.string.settings_split_tunneling_mode_standard),
                description = stringResource(R.string.settings_split_tunneling_mode_description_standard),
                selected = isStandardSelected,
                horizontalContentPadding = DIALOG_CONTENT_PADDING,
                onSelected = {
                    onStandardSelected()
                    onDismissRequest()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsRadioItemSmall(
                title = stringResource(R.string.settings_split_tunneling_mode_inverse),
                description = stringResource(R.string.settings_split_tunneling_mode_description_inverse),
                selected = !isStandardSelected,
                horizontalContentPadding = DIALOG_CONTENT_PADDING,
                onSelected = {
                    onInverseSelected()
                    onDismissRequest()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
