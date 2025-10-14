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

package com.protonvpn.android.tv.settings.ipv6

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.redesign.base.ui.collectAsEffect
import com.protonvpn.android.tv.drawers.TvModalDrawer
import com.protonvpn.android.tv.settings.TvSettingsMainToggleLayout
import com.protonvpn.android.tv.settings.TvSettingsReconnectDialog
import com.protonvpn.android.tv.ui.TvUiConstants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.receiveAsFlow
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.compose.tv.theme.ProtonThemeTv

@AndroidEntryPoint
class TvSettingsIPv6Activity : BaseTvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ProtonThemeTv {
                val viewModel: TvSettingsIPv6ViewModel = hiltViewModel()
                val viewState = viewModel.viewState.collectAsStateWithLifecycle().value
                var showReconnectDialog by remember { mutableStateOf(false) }

                viewModel.eventChannelReceiver.receiveAsFlow().collectAsEffect { event ->
                    when (event) {
                        TvSettingsIPv6ViewModel.Event.OnClose -> {
                            finish()
                        }

                        TvSettingsIPv6ViewModel.Event.OnDismissReconnectNowDialog -> {
                            showReconnectDialog = false
                        }

                        TvSettingsIPv6ViewModel.Event.OnShowReconnectNowDialog -> {
                            viewModel.onShowReconnectNowDialog(vpnUiDelegate = getVpnUiDelegate())
                            showReconnectDialog = true
                        }
                    }
                }

                if (viewState != null) {
                    TvModalDrawer(
                        isDrawerOpen = false,
                        drawerContent = {},
                        content = {
                            TvSettingsIPv6(
                                modifier = Modifier.fillMaxWidth(),
                                viewState = viewState,
                                onToggled = viewModel::toggle,
                            )
                        }
                    )
                }

                if(showReconnectDialog) {
                    TvSettingsReconnectDialog(
                        onReconnectNow = {
                            viewModel.onReconnectNow(vpnUiDelegate = getVpnUiDelegate())
                        },
                        onDismissRequest = viewModel::onDismissReconnectNowDialog,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSettingsIPv6(
    viewState: TvSettingsIPv6ViewModel.ViewState,
    onToggled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        TvSettingsMainToggleLayout(
            modifier = Modifier.widthIn(max = TvUiConstants.SingleColumnWidth),
            title = stringResource(id = R.string.settings_advanced_ipv6_title),
            toggleLabel = stringResource(id = R.string.settings_advanced_ipv6_title),
            toggleValue = viewState.isIPv6Enabled,
            onToggled = onToggled,
        ) {
            item {
                Text(
                    text = stringResource(id = R.string.settings_advanced_ipv6_description),
                    style = ProtonTheme.typography.body2Regular,
                    color = ProtonTheme.colors.textWeak,
                    modifier = Modifier
                        .padding(horizontal = TvUiConstants.SelectionPaddingHorizontal)
                        .padding(top = 12.dp)
                )
            }
        }
    }
}
