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

package com.protonvpn.android.tv.login

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onFirstVisible
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnTvPreview
import com.protonvpn.android.tv.buttons.TvTextButton
import com.protonvpn.android.tv.login.TvQrLoginViewModel.ViewState
import com.protonvpn.android.tv.ui.TvSpinner
import com.protonvpn.android.utils.Constants
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.utils.currentLocale
import me.proton.core.presentation.R as CoreR

@Composable
fun TvQrLoginScreen(
    viewState: ViewState,
    onCreateNewCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
    ) {
        var useFallbackCode by rememberSaveable { mutableStateOf(false) }

        when (viewState) {
            ViewState.Loading,
            is ViewState.Login.Success,
            is ViewState.ForkReady -> {
                if (useFallbackCode) {
                    BackHandler { useFallbackCode = false }
                    LoginUserCodePanel(
                        viewState = viewState,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LoginQrCodePanel(
                        viewState = viewState,
                        onFallbackCode = {
                            useFallbackCode = true
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            ViewState.ForkFailed,
            ViewState.PollingFailed.Timeout,
            is ViewState.PollingFailed.Error,
            is ViewState.Login.Error -> {
                val message = if (viewState == ViewState.PollingFailed.Timeout) {
                    R.string.session_fork_qr_code_error_expired
                } else {
                    R.string.session_fork_qr_code_error_network
                }
                LoginErrorPanel(
                    message = message,
                    onCreateNewClicked = onCreateNewCode,
                    modifier = Modifier
                        .fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun LoginQrCodePanel(
    viewState: ViewState,
    onFallbackCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        VerticalSpacer(height = 50.dp)
        Image(
            painterResource(CoreR.drawable.logo_vpn_with_text),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.height(50.dp),
        )
        VerticalSpacer(modifier = Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                // A hack to allow the "Trouble signing in?" button being unfocused on start.
                // The row with the QR code and title is focused on launch and is above the
                // button (pressing down will focus the button).
                .focusable(true)
                .focusRequester(focusRequester)
        ) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .background(Color.White, ProtonTheme.shapes.large)
            ) {
                if (viewState is ViewState.WithCode) {
                    Image(
                        viewState.bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.session_fork_qr_code_content_description),
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    TvSpinner(
                        color = ProtonTheme.colors.brandNorm,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            val extraHuge = ProtonTheme.typography.hero.copy(
                fontWeight = FontWeight.W700,
                fontSize = 36.sp,
                letterSpacing = 0.45.sp,
                lineHeight = 42.sp,
            )
            Text(
                text = stringResource(R.string.session_fork_qr_code_message),
                style = extraHuge,
                modifier = Modifier.width(360.dp)
            )
        }
        VerticalSpacer(modifier = Modifier.weight(1f))
        TvTextButton(
            text = stringResource(R.string.session_fork_qr_code_trouble_button),
            textStyle = ProtonTheme.typography.body1Bold,
            focusGainSound = true,
            onClick = onFallbackCode,
        )
        VerticalSpacer(height = 24.dp)
    }
}

@Composable
private fun LoginUserCodePanel(
    viewState: ViewState,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        if (viewState is ViewState.ForkReady) {
            VerticalSpacer(height = 96.dp)
            Text(
                stringResource(R.string.session_fork_user_code_title),
                style = ProtonTheme.typography.hero,
            )
            VerticalSpacer(modifier = Modifier.weight(1f))
            UserCodeSteps(viewState.userCode)
        } else {
            VerticalSpacer(modifier = Modifier.weight(1f))
            TvSpinner()
        }
        VerticalSpacer(modifier = Modifier.weight(1f))
        val supportTextHtml =
            stringResource(R.string.session_fork_user_code_support, Constants.URL_SUPPORT_NO_PROTOCOL)
        Text(
            AnnotatedString.fromHtml(supportTextHtml),
            style = ProtonTheme.typography.body1Regular,
            color = ProtonTheme.colors.textWeak,
        )
        VerticalSpacer(height = 42.dp)
    }
}

@Composable
private fun UserCodeSteps(
    userCode: String,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier,
    ) {
        val userCodeDisplay = (userCode.length / 2)
            .takeIf { it > 2 }
            ?.let { userCode.take(it) + " " + userCode.drop(it) }
            ?: userCode
        val step1Html = stringResource(R.string.session_fork_user_code_step1, Constants.TV_LOGIN_URL)
        BulletPointRow(1, AnnotatedString.fromHtml(step1Html))
        BulletPointRow(2, AnnotatedString(stringResource(R.string.session_fork_user_code_step2)))
        val step3Html = stringResource(R.string.session_fork_user_code_step3, userCodeDisplay)
        BulletPointRow(3, AnnotatedString.fromHtml(step3Html))
    }
}

@Composable
private fun BulletPointRow(
    number: Int,
    text: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .semantics(mergeDescendants = true) {},
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(ProtonTheme.colors.iconNorm, shape = CircleShape),
        ) {
            Text(
                "%d".format(locale = LocalConfiguration.current.currentLocale(), number),
                style = ProtonTheme.typography.body1Bold,
                color = ProtonTheme.colors.textInverted,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Text(
            text,
            style = ProtonTheme.typography.body1Regular,
        )
    }
}

@Composable
private fun LoginErrorPanel(
    @StringRes message: Int,
    onCreateNewClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Text(
            stringResource(message),
            style = ProtonTheme.typography.headline,
            textAlign = TextAlign.Center
        )
        VerticalSpacer(height = 48.dp)
        TvTextButton(
            text = stringResource(R.string.session_fork_qr_code_error_create_new_button),
            textStyle = ProtonTheme.typography.body1Bold,
            onClick = onCreateNewClicked,
            modifier = Modifier
                .focusRequester(focusRequester)
        )
        SingleButtonFix()
    }
}

// When there's only a single button on the screen its InteractionSource will not get the initial
// focus event. Add this Composable to work around the issue: it'll add a second focusable
// composable and remove it after it's shown for the first time.
// TODO: report the issue to Google.
@Composable
private fun SingleButtonFix() {
    var showSecondFocusable by remember { mutableStateOf(true) }
    if (showSecondFocusable) {
        Box(
            modifier = Modifier
                .focusable(true)
                .onFirstVisible { showSecondFocusable = false }
        )
    }
}

@ProtonVpnTvPreview
@Composable
private fun PreviewLoginQrCodePanel() {
    ProtonVpnTvPreview {
        LoginQrCodePanel(
            viewState = ViewState.ForkReady(
                "1234ABCD",
                Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
            ),
            onFallbackCode = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@ProtonVpnTvPreview
@Composable
private fun PreviewLoginUserCodePanel() {
    ProtonVpnTvPreview {
        LoginUserCodePanel(
            viewState = ViewState.ForkReady(
                "1234ABCD",
                Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
            ),
            modifier = Modifier.fillMaxSize()
        )
    }
}