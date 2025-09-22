/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.tv.settings.autoconnect

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.tv.settings.TvSettingDescriptionRow
import com.protonvpn.android.tv.settings.TvSettingsMainToggleLayout
import com.protonvpn.android.tv.ui.TvUiConstants
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.compose.tv.theme.ProtonThemeTv

@AndroidEntryPoint
class TvSettingsAutoConnectActivity : BaseTvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProtonThemeTv {
                val viewModel: TvSettingsAutoConnectViewModel = hiltViewModel()
                val viewState = viewModel.viewState.collectAsStateWithLifecycle().value

                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (viewState != null) {
                        TvSettingsAutoConnect(
                            viewState = viewState,
                            onToggled = viewModel::toggleAutoConnect,
                            modifier = Modifier.widthIn(max = TvUiConstants.SingleColumnWidth),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSettingsAutoConnect(
    viewState: TvSettingsAutoConnectViewModel.ViewState,
    onToggled: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvSettingsMainToggleLayout(
        modifier = modifier,
        title = stringResource(R.string.settings_autoconnect_title),
        onToggled = onToggled,
        toggleValue = viewState.isEnabled,
    ) {
        item {
            TvSettingDescriptionRow(
                text = stringResource(R.string.settings_autoconnect_boot_description),
            )
        }
    }
}
