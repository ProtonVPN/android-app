/*
 * Copyright (c) 2023 Proton AG
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
package com.protonvpn.android.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.upsellGradientEnd
import com.protonvpn.android.base.ui.upsellGradientStart
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionWeak
import me.proton.core.compose.theme.defaultSmallNorm
import me.proton.core.compose.theme.defaultWeak
import me.proton.core.presentation.R as CoreR

@Composable
fun OnboardingScreen(
    bannerAction: () -> Unit,
    action: () -> Unit,
) {
    Box(
        Modifier
            .background(ProtonTheme.colors.backgroundNorm)
            .background(Brush.verticalGradient(
                0.0f to ProtonTheme.colors.upsellGradientStart,
                0.3f to ProtonTheme.colors.upsellGradientEnd,
            ))
            .safeDrawingPadding()
    ) {
        Image(
            modifier = Modifier
                .fillMaxWidth()
                .padding(26.dp, 6.dp, 26.dp, 0.dp),
            painter = painterResource(id = R.drawable.onboarding_globe),
            contentDescription = null
        )
        Column(Modifier.fillMaxHeight()) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 16.dp),
                text = stringResource(id = R.string.onboarding_welcome_title),
                style = ProtonTheme.typography.hero,
            )
            Text(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                text = stringResource(id = R.string.onboarding_welcome_description),
                style = ProtonTheme.typography.defaultWeak,
                textAlign = TextAlign.Center
            )
            UpsellBanner(
                Modifier
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    .clickable(onClick = bannerAction)
            )

            Spacer(modifier = Modifier.weight(.5f))

            VpnSolidButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                text = stringResource(id = R.string.onboarding_welcome_action),
                onClick = action
            )
        }
    }
}

@Composable
private fun UpsellBanner(modifier: Modifier) {
    Row(
        modifier = modifier
            .background(
                color = ProtonTheme.colors.backgroundSecondary,
                shape = ProtonTheme.shapes.medium
            )
            .padding(12.dp),
    ) {
        Image(
            modifier = Modifier.padding(end = 12.dp),
            painter = painterResource(id = R.drawable.ic_no_logs_banner),
            contentDescription = null
        )
        Column {
            Row {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(id = R.string.no_logs_banner_title),
                    style = ProtonTheme.typography.defaultSmallNorm
                )
                Image(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(16.dp),
                    painter = painterResource(id = CoreR.drawable.ic_proton_arrow_out_square),
                    colorFilter = ColorFilter.tint(ProtonTheme.colors.iconWeak),
                    contentDescription = null
                )
            }
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = stringResource(id = R.string.no_logs_banner_description),
                style = ProtonTheme.typography.captionWeak
            )
        }
    }
}

@ProtonVpnPreview
@Composable
private fun OnboardingScreenPreview() {
    ProtonVpnPreview {
        OnboardingScreen({}) {}
    }
}
