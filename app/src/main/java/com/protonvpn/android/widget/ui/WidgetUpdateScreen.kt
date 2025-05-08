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
package com.protonvpn.android.widget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.TextWithStyle
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.VpnTextButton
import com.protonvpn.android.base.ui.textMultiStyle
import com.protonvpn.android.redesign.settings.ui.SubSetting
import me.proton.core.compose.theme.ProtonTheme


@Composable
fun WidgetAddScreen(
    onClose: () -> Unit,
) {
    SubSetting(
        title = stringResource(id = R.string.settings_widget_title),
        onClose = onClose
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            WidgetLottieAnimation()
            StyledAddWidgetDescription()
        }
    }
}

@Composable
private fun StyledAddWidgetDescription(
    modifier: Modifier = Modifier
) {
    Text(
        modifier = modifier,
        color = ProtonTheme.colors.textNorm,
        style = ProtonTheme.typography.body2Regular,
        text = textMultiStyle(
            originalText = stringResource(
                R.string.setting_widget_selection_additional_description,
                stringResource(R.string.app_name)
            ),
            customStyleTexts = listOf(
                TextWithStyle(
                    stringResource(R.string.app_name),
                    ProtonTheme.typography.body2Medium
                )
            )
        )
    )
}

@Composable
fun OnboardingWidgetBottomSheetContent(
    onClose: () -> Unit,
    onAddWidget: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            color = ProtonTheme.colors.textNorm,
            style = ProtonTheme.typography.subheadline,
            text = stringResource(R.string.setting_widget_try_title),
            modifier = Modifier.padding(vertical = 16.dp)
        )

        WidgetLottieAnimation()
        Spacer(Modifier.height(16.dp))

        if (onAddWidget != null) {
            VpnSolidButton(
                text = stringResource(R.string.settings_widget_selection_button_title),
                onClick = onAddWidget,
            )
            Spacer(Modifier.height(8.dp))
            VpnTextButton(
                text = stringResource(R.string.no_thanks),
                onClick = onClose,
            )
        } else {
            StyledAddWidgetDescription()
            Spacer(Modifier.height(32.dp))
            VpnSolidButton(
                text = stringResource(R.string.got_it),
                onClick = onClose,
            )
        }
    }
}

@Composable
private fun WidgetLottieAnimation(
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
    ) {
        val compositionResult = rememberLottieComposition(
            LottieCompositionSpec.RawRes(R.raw.add_widget_animation)
        )
        val composition = compositionResult.value

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .aspectRatio(16f / 9f)
                .background(ProtonTheme.colors.backgroundSecondary),
            contentAlignment = Alignment.Center
        ) {
            LottieAnimation(
                composition,
                iterations = 1
            )
        }

        Text(
            color = ProtonTheme.colors.textNorm,
            style = ProtonTheme.typography.body2Regular,
            text = stringResource(R.string.settings_widget_selection_description),
            modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
        )
    }
}

@ProtonVpnPreview
@Composable
fun WidgetSettingsScreenPreview() {
    ProtonVpnPreview {
        WidgetAddScreen(onClose = {})
    }
}

@ProtonVpnPreview
@Composable
fun WidgetOnboardingBottomSheetPreview() {
    ProtonVpnPreview {
        OnboardingWidgetBottomSheetContent(
            onClose = {},
            onAddWidget = {}
        )
    }
}

@ProtonVpnPreview
@Composable
fun WidgetOnboardingBottomSheetNonNativePreview() {
    ProtonVpnPreview {
        OnboardingWidgetBottomSheetContent(
            onClose = {},
            onAddWidget = null
        )
    }
}
