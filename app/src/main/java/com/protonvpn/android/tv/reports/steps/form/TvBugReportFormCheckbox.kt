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

package com.protonvpn.android.tv.reports.steps.form

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.base.ui.previews.PreviewBooleanProvider
import com.protonvpn.android.tv.settings.ProtonTvFocusableSurface
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm

@Composable
fun TvBugReportFormCheckbox(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        ProtonTvFocusableSurface(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onCheckedChange(!isChecked) },
            shape = ProtonTheme.shapes.large,
            focusedColor = { ProtonTheme.colors.backgroundNorm },
            clickSound = true,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape = ProtonTheme.shapes.large)
                    .background(color = ProtonTheme.colors.backgroundSecondary)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(weight = 1f, fill = true),
                    text = stringResource(id = R.string.dynamic_report_logs_title),
                    style = ProtonTheme.typography.defaultNorm,
                )

                Checkbox(
                    modifier = Modifier
                        .focusable(enabled = false)
                        .offset(x = 12.dp)
                        .clearAndSetSemantics {},
                    checked = isChecked,
                    colors = CheckboxDefaults.colors(
                        uncheckedColor = ProtonTheme.colors.shade60,
                    ),
                    onCheckedChange = {},
                )
            }
        }

        AnimatedVisibility(
            visible = !isChecked,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ProtonTvFocusableSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                ) {
                    Icon(
                        modifier = Modifier.size(size = 20.dp),
                        painter = painterResource(id = R.drawable.ic_exclamation_triangle_filled),
                        contentDescription = null,
                        tint = ProtonTheme.colors.iconWeak,
                    )

                    Text(
                        text = stringResource(id = R.string.dynamic_report_logs_missing),
                        color = ProtonTheme.colors.textWeak,
                        style = ProtonTheme.typography.captionRegular,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun TvBugReportFormCheckboxPreview(
    @PreviewParameter(PreviewBooleanProvider::class) isChecked: Boolean,
) {
    ProtonVpnPreview(isDark = true) {
        TvBugReportFormCheckbox(
            isChecked = isChecked,
            onCheckedChange = {},
        )
    }
}
