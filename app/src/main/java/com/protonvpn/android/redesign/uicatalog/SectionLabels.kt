/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.redesign.uicatalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionStrongNorm

@Composable
fun SamplesSectionLabel(
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = ProtonTheme.typography.captionStrongNorm,
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
        Divider(color = ProtonTheme.colors.shade50, modifier = Modifier.padding(bottom = 16.dp))
    }
}

@Composable
fun ColumnScope.SingleLabeledComponent(
    label: String,
    contentMargins: Boolean = false,
    content: @Composable () -> Unit
) {
    val marginsModifier = if (contentMargins) Modifier.padding(horizontal = 16.dp) else Modifier
    SamplesSectionLabel(label, marginsModifier)
    Box(marginsModifier) {
        content()
    }
    VerticalSpacer(height = 24.dp)
}