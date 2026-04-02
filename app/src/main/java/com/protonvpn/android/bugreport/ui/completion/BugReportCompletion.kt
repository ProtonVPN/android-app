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

package com.protonvpn.android.bugreport.ui.completion

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.protonvpn.android.base.ui.BottomButtonsColumn
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.VpnSolidButton
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun BugReportCompletion(
    @DrawableRes imageResId: Int,
    titleText: String,
    descriptionText: String,
    actionText: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            BottomButtonsColumn {
                VpnSolidButton(
                    text = actionText,
                    onClick = onActionClick,
                )
            }
        },
    ) { innerPaddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues = innerPaddingValues),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(space = 16.dp),
            ) {
                Image(
                    painter = painterResource(id = imageResId),
                    contentDescription = null,
                )

                Text(
                    text = titleText,
                    textAlign = TextAlign.Center,
                    style = ProtonTheme.typography.headline,
                )

                Text(
                    text = descriptionText,
                    textAlign = TextAlign.Center,
                    color = ProtonTheme.colors.textWeak,
                    style = ProtonTheme.typography.body2Regular,
                )
            }
        }
    }
}

@ProtonVpnPreview
@Composable
private fun BugReportCompletionPreview() {
    ProtonVpnPreview {
        BugReportCompletion(
            modifier = Modifier.fillMaxSize(),
            imageResId = R.drawable.report_success,
            titleText = stringResource(id = R.string.report_sent),
            descriptionText = stringResource(id = R.string.dynamic_report_completion_success_title),
            actionText = stringResource(id = R.string.action_done),
            onActionClick = {}
        )
    }
}
