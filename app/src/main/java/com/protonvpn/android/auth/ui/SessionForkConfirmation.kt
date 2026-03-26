/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.auth.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.auth.ui.SessionForkConfirmationViewModel.ViewState
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.SimpleTopAppBar
import com.protonvpn.android.base.ui.TopAppBarCloseIcon
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.VpnTextButton
import com.protonvpn.android.base.ui.VpnWeakSolidButton
import com.protonvpn.android.base.ui.largeScreenContentPadding
import com.protonvpn.android.base.ui.theme.VpnTheme
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun SessionForkConfirmation(
    viewState: ViewState,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
    onReportBug: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (viewState) {
        is ViewState.AskForkConfirmation -> {
            SignIn(
                isLoading = viewState.isLoading,
                onConfirm = onConfirm,
                onClose = onClose,
                modifier = modifier,
            )
        }

        ViewState.ErrorBusinessUser,
        ViewState.ErrorUserCodeInvalid -> {
            FinishPage(
                imageRes = R.drawable.session_fork_error,
                titleRes = R.string.session_fork_error_generic_title,
                messageRes = R.string.session_fork_error_try_fallback_message,
                modifier = modifier,
            ) {
                VpnSolidButton(
                    stringResource(R.string.close),
                    onClick = onClose,
                )
            }
        }

        is ViewState.ForkError -> {
            when (viewState) {
                ViewState.ForkError.Expired -> {
                    FinishPage(
                        imageRes = R.drawable.session_fork_error,
                        titleRes = R.string.session_fork_error_expired_title,
                        messageRes = R.string.session_fork_error_expired_message,
                        modifier = modifier,
                    ) {
                        VpnSolidButton(
                            stringResource(R.string.close),
                            onClick = onClose,
                        )
                    }
                }

                ViewState.ForkError.Fatal -> {
                    FinishPage(
                        imageRes = R.drawable.session_fork_error,
                        titleRes = R.string.session_fork_error_generic_title,
                        messageRes = R.string.session_fork_error_fatal_message,
                        modifier = modifier
                    ) {
                        VpnTextButton(
                            stringResource(R.string.session_fork_error_report_button),
                            onClick = onReportBug,
                        )
                        VpnSolidButton(
                            stringResource(R.string.close),
                            onClick = onClose,
                        )
                    }
                }

                ViewState.ForkError.Network -> {
                    FinishPage(
                        imageRes = R.drawable.session_fork_error,
                        titleRes = R.string.session_fork_error_generic_title,
                        messageRes = R.string.session_fork_error_network_message,
                        modifier = modifier
                    ) {
                        VpnSolidButton(
                            stringResource(R.string.try_again),
                            onClick = onConfirm,
                        )
                        VpnTextButton(
                            stringResource(R.string.close),
                            onClick = onClose,
                        )
                    }
                }
            }
        }

        ViewState.ForkSuccess -> {
            FinishPage(
                imageRes = R.drawable.session_fork_success,
                titleRes = R.string.session_fork_success_title,
                messageRes = R.string.session_fork_success_message,
                modifier = modifier
            ) {
                VpnSolidButton(
                    stringResource(R.string.action_done),
                    onClick = onClose,
                )
            }
        }

        ViewState.Initial -> Unit
    }
}

@Composable
private fun SignIn(
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            SimpleTopAppBar(
                title = {},
                navigationIcon = { TopAppBarCloseIcon(onClose) }
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .largeScreenContentPadding()
                    .padding(16.dp),
            ) {
                VpnSolidButton(
                    stringResource(R.string.login),
                    onClick = onConfirm,
                    isLoading = isLoading,
                )
                VerticalSpacer(height = 8.dp)
                VpnWeakSolidButton(
                    stringResource(R.string.cancel),
                    onClick = onClose,
                )
            }
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .largeScreenContentPadding()
                .padding(paddingValues)
        ) {
            VerticalSpacer(height = 16.dp)
            Image(
                painterResource(CoreR.drawable.logo_vpn_with_text),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.width(138.dp),
            )
            VerticalSpacer(height = 32.dp)
            Text(
                stringResource(R.string.session_fork_confirmation_title),
                style = ProtonTheme.typography.headline,
                textAlign = TextAlign.Center,
            )
            VerticalSpacer(height = 8.dp)
            Text(
                stringResource(R.string.session_fork_confirmation_message),
                style = ProtonTheme.typography.body1Regular,
                textAlign = TextAlign.Center,
            )
            VerticalSpacer(modifier = Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(CoreR.drawable.ic_proton_info_circle_filled),
                    modifier = Modifier.size(14.dp),
                    contentDescription = null,
                    tint = ProtonTheme.colors.iconWeak,
                )
                Spacer(Modifier.width(11.dp))
                Text(
                    stringResource(R.string.session_fork_confirmation_bottom_note),
                    style = ProtonTheme.typography.body2Regular,
                    color = ProtonTheme.colors.textWeak,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun FinishPage(
    @DrawableRes imageRes: Int,
    @StringRes titleRes: Int,
    @StringRes messageRes: Int,
    modifier: Modifier = Modifier,
    buttonContent: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        bottomBar = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(16.dp),
            ) {
                buttonContent()
            }
        },
        modifier = modifier
            .largeScreenContentPadding(),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painterResource(imageRes),
                contentDescription = null,
            )
            Text(
                stringResource(titleRes),
                style = ProtonTheme.typography.headline,
                textAlign = TextAlign.Center,
            )
            VerticalSpacer(height = 8.dp)
            Text(
                stringResource(messageRes),
                style = ProtonTheme.typography.body1Regular,
                color = ProtonTheme.colors.textWeak,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@ProtonVpnPreview
@Composable
private fun PreviewSessionForkConfirmation() {
    VpnTheme {
        SessionForkConfirmation(
            viewState = ViewState.AskForkConfirmation(false),
            onConfirm = {},
            onClose = {},
            onReportBug = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@ProtonVpnPreview
@Composable
private fun PreviewSessionForkConfirmationLoading() {
    VpnTheme {
        SessionForkConfirmation(
            viewState = ViewState.AskForkConfirmation(true),
            onConfirm = {},
            onClose = {},
            onReportBug = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@ProtonVpnPreview
@Composable
private fun PreviewSessionForkSuccess() {
    VpnTheme {
        SessionForkConfirmation(
            viewState = ViewState.ForkSuccess,
            onConfirm = {},
            onClose = {},
            onReportBug = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@ProtonVpnPreview
@Composable
private fun PreviewSessionForkErrorNoNetwork() {
    VpnTheme {
        SessionForkConfirmation(
            viewState = ViewState.ForkError.Network,
            onConfirm = {},
            onClose = {},
            onReportBug = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@ProtonVpnPreview
@Composable
private fun PreviewSessionForkErrorFatal() {
    VpnTheme {
        SessionForkConfirmation(
            viewState = ViewState.ForkError.Fatal,
            onConfirm = {},
            onClose = {},
            onReportBug = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}