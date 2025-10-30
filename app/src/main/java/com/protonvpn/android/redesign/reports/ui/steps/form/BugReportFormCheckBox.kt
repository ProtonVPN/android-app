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

package com.protonvpn.android.redesign.reports.ui.steps.form

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.tooltips.VpnIconTooltip
import com.protonvpn.android.redesign.base.ui.previews.PreviewBooleanProvider
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun BugReportFormCheckBox(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            Row(
                modifier = Modifier.weight(weight = 1f, fill = true),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(id = R.string.dynamic_report_logs_title),
                    color = ProtonTheme.colors.textNorm,
                    style = ProtonTheme.typography.body2Regular,
                )

                VpnIconTooltip(
                    tooltipText = stringResource(id = R.string.dynamic_report_logs_tooltip),
                    iconResId = CoreR.drawable.ic_info_circle,
                    iconTint = ProtonTheme.colors.iconWeak,
                    isPersistent = true,
                )
            }

            Checkbox(
                modifier = Modifier
                    .focusable()
                    .clearAndSetSemantics {},
                checked = isChecked,
                colors = CheckboxDefaults.colors(
                    uncheckedColor = ProtonTheme.colors.shade60,
                ),
                onCheckedChange = onCheckedChange,
            )
        }

        if (!isChecked) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
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

@Preview
@Composable
private fun BugReportFormCheckBoxCheckedPreview(
    @PreviewParameter(PreviewBooleanProvider::class) isDark: Boolean,
) {
    ProtonVpnPreview(isDark = isDark) {
        BugReportFormCheckBox(
            isChecked = true,
            onCheckedChange = {},
        )
    }
}

@Preview
@Composable
private fun BugReportFormCheckBoxUncheckedPreview(
    @PreviewParameter(PreviewBooleanProvider::class) isDark: Boolean,
) {
    ProtonVpnPreview(isDark = isDark) {
        BugReportFormCheckBox(
            isChecked = false,
            onCheckedChange = {},
        )
    }
}
