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
package com.protonvpn.android.redesign.main_screen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.VpnSolidButton
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultSmallWeak
import me.proton.core.compose.theme.defaultStrongNorm

@Composable
fun FullScreenLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val composition =
                rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.vpn_pulsing_logo))
            LottieAnimation(
                composition.value,
                modifier = Modifier.size(128.dp),
                iterations = LottieConstants.IterateForever,
            )
        }
    }
}

@Composable
fun FullScreenError(
    modifier: Modifier = Modifier,
    errorTitle: String,
    errorDescription: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = errorTitle,
                style = ProtonTheme.typography.defaultStrongNorm,
                modifier = Modifier.padding(8.dp)
            )
            Text(
                text = errorDescription,
                style = ProtonTheme.typography.defaultSmallWeak,
                modifier = Modifier.padding(8.dp)
            )
            VpnSolidButton(
                text = stringResource(R.string.retry),
                onClick = onRetry,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview
@Composable
fun PreviewLoadingScreen() {
    FullScreenLoading()
}

@Preview
@Composable
fun PreviewFullScreenError() {
    FullScreenError(
        errorTitle = "Something went wrong",
        errorDescription = "Error description explaining what went wrong",
        onRetry = { }
    )
}
