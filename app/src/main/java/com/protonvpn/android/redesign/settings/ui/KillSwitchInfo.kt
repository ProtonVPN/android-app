/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.redesign.settings.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonOutlinedButton
import com.protonvpn.android.base.ui.ProtonSolidButton
import com.protonvpn.android.base.ui.TextBulletRow
import com.protonvpn.android.base.ui.replaceWithInlineContent
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun KillSwitchInfo(
    onOpenVpnSettings: () -> Unit,
    onLearnMore: () -> Unit,
    onClose: () -> Unit,
) {
    BasicSubSetting(
        title = stringResource(R.string.settings_kill_switch_title),
        onClose = onClose
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
                .largeScreenContentPadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Image(
                    painterResource(R.drawable.killswitch_settings_promo),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                VerticalSpacer(height = 32.dp)
                Text(
                    text = stringResource(R.string.settingsKillSwitchEnableTitle),
                    style = ProtonTheme.typography.headline,
                )
                VerticalSpacer(height = 16.dp)
                val blockLineHeightStyle = LineHeightStyle(LineHeightStyle.Default.alignment, LineHeightStyle.Trim.None)
                val bulletPointStyle = LocalTextStyle.current.copy(lineHeightStyle = blockLineHeightStyle)
                val cogwheelId = "cogwheel"
                val cogwheelInlineIcon = inlineIcon(
                    20.sp,
                    20.sp,
                    CoreR.drawable.ic_proton_cog_wheel,
                    stringResource(R.string.settingsKillSwitchEnableStep2_gearIconContentDescription)
                )
                val step2InlineContentMap = mapOf(cogwheelId to cogwheelInlineIcon)
                val step2TextWithInlineId =
                    stringResource(R.string.settingsKillSwitchEnableStep2).replaceWithInlineContent("%1\$s", cogwheelId)

                Text(stringResource(R.string.settingsKillSwitchEnableStep1), style = bulletPointStyle)
                Text(step2TextWithInlineId, inlineContent = step2InlineContentMap, style = bulletPointStyle)
                Text(
                    AnnotatedString.fromHtml(stringResource(R.string.settingsKillSwitchEnableStep3)),
                    style = bulletPointStyle
                )

                VerticalSpacer(height = 16.dp)
                CompositionLocalProvider(
                    LocalContentColor provides ProtonTheme.colors.textWeak,
                    LocalTextStyle provides ProtonTheme.typography.body2Regular.copy(lineHeightStyle = blockLineHeightStyle)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_proton_info_circle),
                            contentDescription = null,
                        )
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.settingsKillSwitchWarningMain))
                            VerticalSpacer(height = 16.dp)
                            TextBulletRow(R.string.settingsKillSwitchWarningPoint1)
                            TextBulletRow(R.string.settingsKillSwitchWarningPoint2)
                        }
                    }
                }
            }
            VerticalSpacer(height = 16.dp)
            ProtonSolidButton(
                onOpenVpnSettings,
                contained = false,
            ) {
                ButtonTextWithExternalIcon(stringResource(R.string.settingsKillSwitchAndroidSettingsButton))
            }
            VerticalSpacer(height = 16.dp)
            ProtonOutlinedButton(
                onLearnMore,
                contained = false,
            ) {
                ButtonTextWithExternalIcon(stringResource(R.string.settingsKillSwitchLearnMoreButton))
            }
            VerticalSpacer(height = 16.dp)
        }
    }
}

// Note: if it's needed in more places consider adding a generic implementation in base.ui.ProtonButtons.
@Composable
private fun ButtonTextWithExternalIcon(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text,
            modifier = Modifier.align(Alignment.Center)
        )
        Icon(
            painterResource(CoreR.drawable.ic_proton_arrow_out_square),
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

private fun inlineIcon(width: TextUnit, height: TextUnit, @DrawableRes iconRes: Int, contentDescription: String) =
    InlineTextContent(
        Placeholder(width, height, PlaceholderVerticalAlign.Center)
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize()
        )
    }
