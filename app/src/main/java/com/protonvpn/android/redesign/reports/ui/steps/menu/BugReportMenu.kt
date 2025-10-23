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

package com.protonvpn.android.redesign.reports.ui.steps.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.banners.VpnUpdateBanner
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.android.redesign.reports.ui.BugReportViewModel
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun BugReportMenu(
    viewState: BugReportViewModel.ViewState,
    onUpdateVersionClick: () -> Unit,
    onCategoryClick: (Category) -> Unit,
    onSetCurrentStep: (BugReportViewModel.BugReportSteps) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(key1 = Unit) {
        onSetCurrentStep(BugReportViewModel.BugReportSteps.Menu)
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (viewState.isUpdateAvailable) {
            item {
                VpnUpdateBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            top = 24.dp,
                            end = 16.dp,
                        ),
                    onClick = onUpdateVersionClick,
                )
            }
        }

        item {
            Text(
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 24.dp,
                ),
                text = stringResource(id = R.string.dynamic_report_your_issue_headline),
                color = ProtonTheme.colors.textNorm,
                style = ProtonTheme.typography.headline,
            )
        }

        items(viewState.categories) { category ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategoryClick(category) }
                    .padding(all = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
            ) {
                Text(
                    modifier = Modifier.weight(weight = 1f),
                    text = category.label,
                    color = ProtonTheme.colors.textNorm,
                    style = ProtonTheme.typography.body1Regular,
                )

                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_proton_chevron_right),
                    contentDescription = null,
                    tint = ProtonTheme.colors.iconWeak,
                )
            }
        }
    }
}
