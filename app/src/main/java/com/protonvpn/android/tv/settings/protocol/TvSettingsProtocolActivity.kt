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

package com.protonvpn.android.tv.settings.protocol

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.redesign.base.ui.collectAsEffect
import com.protonvpn.android.tv.settings.TvSettingsReconnectDialog
import com.protonvpn.android.tv.ui.TvUiConstants
import com.protonvpn.android.userstorage.DontShowAgainStore
import com.protonvpn.android.utils.DebugUtils
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.compose.tv.theme.ProtonThemeTv

@AndroidEntryPoint
class TvSettingsProtocolActivity : BaseTvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProtonThemeTv {
                val viewModel: TvSettingsProtocolViewModel = hiltViewModel()
                val viewState = viewModel.viewState.collectAsStateWithLifecycle().value

                viewModel.eventNavigateBack.collectAsEffect {
                    finish()
                }

                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (viewState != null) {
                        var locallySelectedProtocol by rememberSaveable {
                            mutableStateOf(viewState.selectedProtocol)
                        }

                        BackHandler {
                            viewModel.onNavigatedBack(getVpnUiDelegate(), locallySelectedProtocol)
                        }

                        TvSettingsProtocolMain(
                            selectedProtocol = locallySelectedProtocol,
                            showProtun = viewState.showProtun,
                            onSelected = { locallySelectedProtocol = it },
                            modifier = Modifier.widthIn(max = TvUiConstants.SingleColumnWidth),
                        )
                    }
                }

                if (viewState?.reconnectDialog != null) {
                    DebugUtils.debugAssert("Unsupported dialog type") {
                        viewState.reconnectDialog == DontShowAgainStore.Type.ProtocolChangeWhenConnected
                    }
                    TvSettingsReconnectDialog(
                        onReconnectNow = { viewModel.onReconnectClicked(getVpnUiDelegate(), false) },
                        onDismissRequest = { viewModel.dismissReconnectDialog(false) }
                    )
                }
            }
        }
    }
}
