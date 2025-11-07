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

package com.protonvpn.android.tv.reports.completion

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.protonvpn.android.tv.buttons.TvSolidButton
import com.protonvpn.android.tv.ui.TvUiConstants
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun TvBugReportCompletion(
    @DrawableRes imageResId: Int,
    titleText: String,
    descriptionText: String,
    actionText: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(key1 = Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = TvUiConstants.SingleColumnWidth),
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

            Spacer(modifier = Modifier.height(height = 16.dp))

            TvSolidButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester = focusRequester),
                text = actionText,
                onClick = onActionClick,
            )
        }
    }
}
