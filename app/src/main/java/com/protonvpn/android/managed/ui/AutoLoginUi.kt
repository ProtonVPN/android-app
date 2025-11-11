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

package com.protonvpn.android.managed.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.VpnOutlinedButton
import com.protonvpn.android.base.ui.VpnSolidButton
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultSmallNorm
import me.proton.core.compose.theme.headlineNorm
import me.proton.core.compose.theme.headlineSmallNorm

@Composable
fun AutoLoginView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.auto_login_loading_description),
                style = ProtonTheme.typography.headlineSmallNorm
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator()
        }
    }
}

@Composable
fun AutoLoginErrorView(
    message: String?,
    onRetry: () -> Unit,
    onReportIssue: () -> Unit,
    onShowLog: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.auto_login_error_title),
                    style = ProtonTheme.typography.headlineNorm,
                    textAlign = TextAlign.Center
                )
                message?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = ProtonTheme.typography.defaultSmallNorm,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Column {
            VpnSolidButton(
                text = stringResource(id = R.string.retry),
                onClick = onRetry,
            )
            Spacer(modifier = Modifier.height(8.dp))
            VpnOutlinedButton(
                text = stringResource(id = R.string.settings_report_issue_title),
                onClick = onReportIssue,
            )
            Spacer(modifier = Modifier.height(8.dp))
            VpnOutlinedButton(
                stringResource(R.string.settings_debug_logs_title),
                onClick = onShowLog,
            )
        }
    }
}

@ProtonVpnPreview
@Composable
fun PreviewAutoLoginView() {
    ProtonVpnPreview {
        AutoLoginView()
    }
}

@ProtonVpnPreview
@Composable
fun PreviewAutoLoginErrorView() {
    ProtonVpnPreview {
        AutoLoginErrorView(
            message = "Error message",
            onRetry = {},
            onReportIssue = {},
            onShowLog = {}
        )
    }
}
