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

package com.protonvpn.android.tv.settings.customdns.add

import android.content.DialogInterface
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.redesign.base.ui.collectAsEffect
import com.protonvpn.android.tv.dialogs.TvAlertDialog
import com.protonvpn.android.tv.settings.TvSettingsMainEditTextLayout
import com.protonvpn.android.tv.ui.TvUiConstants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.receiveAsFlow
import me.proton.core.presentation.compose.tv.theme.ProtonThemeTv

@AndroidEntryPoint
class TvSettingsAddCustomDnsActivity : BaseTvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ProtonThemeTv {
                val viewModel = hiltViewModel<TvSettingsAddCustomDnsViewModel>()
                val viewState = viewModel.viewStateFlow.collectAsStateWithLifecycle().value
                var showNetShieldConflictDialog by rememberSaveable { mutableStateOf(value = false) }

                viewModel.eventChannelReceiver.receiveAsFlow().collectAsEffect { event ->
                    when (event) {
                        TvSettingsAddCustomDnsViewModel.Event.OnCustomDnsAdded -> {
                            finish()
                        }

                        TvSettingsAddCustomDnsViewModel.Event.OnShowNetShieldConflictDialog -> {
                            showNetShieldConflictDialog = true
                        }
                    }
                }

                viewState?.let { state ->
                    TvSettingsAddCustomDns(
                        modifier = Modifier.fillMaxWidth(),
                        state = state,
                        onCustomDnsChanged = viewModel::onCustomDnsChanged,
                        onSubmitCustomDns = viewModel::onAddCustomDns,
                    )
                }

                if (showNetShieldConflictDialog) {
                    TvAlertDialog(
                        title = stringResource(id = R.string.settings_custom_dns_netshield_conflict_dialog_title),
                        description = stringResource(id = R.string.settings_custom_dns_netshield_conflict_dialog_message_tv),
                        focusedButton = DialogInterface.BUTTON_POSITIVE,
                        confirmText = stringResource(id = R.string.got_it),
                        onConfirm = {
                            showNetShieldConflictDialog = false

                            finish()
                        },
                    )
                }
            }
        }
    }

}

@Composable
private fun TvSettingsAddCustomDns(
    state: TvSettingsAddCustomDnsViewModel.ViewState,
    onCustomDnsChanged: () -> Unit,
    onSubmitCustomDns: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var customDnsValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(value = TextFieldValue())
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        TvSettingsMainEditTextLayout(
            modifier = Modifier.widthIn(max = TvUiConstants.SingleColumnWidth),
            title = stringResource(id = R.string.settings_add_dns_title),
            value = customDnsValue,
            onValueChange = { newCustomDnsValue ->
                customDnsValue = newCustomDnsValue

                onCustomDnsChanged()
            },
            assistiveText = stringResource(id = R.string.inputIpAddressHelp),
            placeholderText = stringResource(id = R.string.inputIpAddressHintIP),
            errorText = state.error?.errorRes?.let { errorResId -> stringResource(id = errorResId) },
            isError = state.error != null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { onSubmitCustomDns(customDnsValue.text.trim()) },
            ),
            singleLine = true,
            maxLines = 1,
        )
    }
}
