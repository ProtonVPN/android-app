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

package com.protonvpn.android.auth.ui.sessionfork

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.VpnWeakSolidButton
import com.protonvpn.android.base.ui.largeScreenContentPadding
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun SessionForkSignIn(
    onSignUp: () -> Unit,
    onSignIn: () -> Unit,
    onTermsAndConditions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .largeScreenContentPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.session_fork_no_account_title),
                style = ProtonTheme.typography.hero,
                textAlign = TextAlign.Center,
            )
            VerticalSpacer(height = 8.dp)
            Text(
                stringResource(R.string.session_fork_no_account_message),
                style = ProtonTheme.typography.body1Regular,
                color = ProtonTheme.colors.textWeak,
                textAlign = TextAlign.Center,
            )

            VerticalSpacer(height = 24.dp)
            VpnSolidButton(
                text = stringResource(R.string.session_fork_no_account_signup_button),
                onClick = onSignUp,
            )
            VerticalSpacer(height = 8.dp)
            VpnWeakSolidButton(
                text = stringResource(R.string.session_fork_no_account_signin_button),
                onClick = onSignIn,
            )
            VerticalSpacer(height = 24.dp)

            val footnoteStyle = ProtonTheme.typography.captionRegular
            val footnoteLinkStyle = footnoteStyle.copy(color = ProtonTheme.colors.textAccent)
            val footnote = AnnotatedString.fromHtml(
                stringResource(R.string.session_fork_no_account_footnote),
                TextLinkStyles(footnoteLinkStyle.toSpanStyle()),
                { onTermsAndConditions() }
            )
            Text(
                footnote,
                style = footnoteStyle,
                color = ProtonTheme.colors.textWeak,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewSessionSignIn() {
    ProtonVpnPreview {
        SessionForkSignIn(
            {}, {}, {}, Modifier.fillMaxSize()
        )
    }
}