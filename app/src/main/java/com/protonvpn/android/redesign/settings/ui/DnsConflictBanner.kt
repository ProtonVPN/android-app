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

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.AnnotatedClickableText
import com.protonvpn.android.base.ui.ProtonSecondaryButton
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun DnsConflictBanner(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    @StringRes buttonRes: Int,
    onLearnMore: () -> Unit,
    onButtonClicked: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = ProtonTheme.colors.backgroundSecondary,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    Surface(
        modifier = modifier,
        color = backgroundColor,
        shape = ProtonTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(contentPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(painterResource(CoreR.drawable.ic_proton_info_circle_filled), contentDescription = null)
                Text(stringResource(titleRes), style = ProtonTheme.typography.body2Medium)
            }
            Spacer(Modifier.height(8.dp))
            AnnotatedClickableText(
                fullText = stringResource(descriptionRes),
                annotatedPart = stringResource(R.string.learn_more),
                onAnnotatedClick = onLearnMore,
                style = ProtonTheme.typography.body2Regular,
                annotatedStyle = ProtonTheme.typography.body2Medium,
                color = ProtonTheme.colors.textWeak
            )
            Spacer(Modifier.height(16.dp))
            ProtonSecondaryButton(onButtonClicked) {
                Text(stringResource(buttonRes))
            }
        }
    }
}
