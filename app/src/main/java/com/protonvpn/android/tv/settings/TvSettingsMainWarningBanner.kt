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

package com.protonvpn.android.tv.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.protonvpn.android.tv.buttons.TvSolidButton
import com.protonvpn.android.tv.ui.TvUiConstants
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun TvSettingsMainWarningBanner(
    @DrawableRes headerImageRes: Int,
    headerTitle: String,
    headerDescription: String,
    bannerTitle: String,
    bannerDescription: String,
    actionText: String,
    onActionClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(key1 = Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(space = 16.dp)
    ) {
        TvSettingsHeader(
            modifier = Modifier.padding(top = TvUiConstants.ScreenPaddingVertical),
            title = headerTitle,
            imageRes = headerImageRes,
        )

        Text(
            modifier = Modifier
                .padding(horizontal = TvUiConstants.SelectionPaddingHorizontal)
                .padding(top = 12.dp),
            text = headerDescription,
            style = ProtonTheme.typography.body2Regular,
            color = ProtonTheme.colors.textWeak,
        )

        Column(
            modifier = Modifier
                .clip(shape = ProtonTheme.shapes.medium)
                .background(color = ProtonTheme.colors.backgroundSecondary)
                .padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            ) {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_proton_info_circle_filled),
                    contentDescription = null,
                    tint = ProtonTheme.colors.textNorm,
                )

                Text(
                    text = bannerTitle,
                    color = ProtonTheme.colors.textNorm,
                    style = ProtonTheme.typography.body2Medium,
                )
            }

            Text(
                text = bannerDescription,
                color = ProtonTheme.colors.textWeak,
                style = ProtonTheme.typography.body2Regular,
            )

            TvSolidButton(
                modifier = Modifier.focusRequester(focusRequester = focusRequester),
                text = actionText,
                onClick = onActionClicked,
            )
        }
    }
}
