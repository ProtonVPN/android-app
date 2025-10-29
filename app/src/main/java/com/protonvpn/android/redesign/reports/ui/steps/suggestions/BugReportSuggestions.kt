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

package com.protonvpn.android.redesign.reports.ui.steps.suggestions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.VpnDivider
import com.protonvpn.android.redesign.base.ui.optional
import com.protonvpn.android.redesign.reports.ui.BugReportViewModel
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun BugReportSuggestions(
    viewState: BugReportViewModel.ViewState,
    onSetCurrentStep: (BugReportViewModel.BugReportSteps) -> Unit,
    onSuggestionLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(key1 = Unit) {
        onSetCurrentStep(BugReportViewModel.BugReportSteps.Suggestions)
    }

    LazyColumn(
        modifier = modifier,
    ) {
        item {
            Column(
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(space = 4.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.dynamic_report_tips_title),
                    color = ProtonTheme.colors.textNorm,
                    style = ProtonTheme.typography.headline,
                )

                Text(
                    text = stringResource(id = R.string.dynamic_report_tips_description),
                    color = ProtonTheme.colors.textWeak,
                    style = ProtonTheme.typography.body2Regular,
                )
            }
        }

        itemsIndexed(viewState.suggestions) { index, suggestion ->
            val isSuggestionLinkAvailable = suggestion.link != null

            Row(
                modifier = Modifier
                    .optional(
                        predicate = { isSuggestionLinkAvailable },
                        modifier = Modifier.clickable {
                            onSuggestionLinkClick(suggestion.link.orEmpty())
                        },
                    )
                    .padding(all = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            ) {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_proton_lightbulb),
                    contentDescription = null,
                    tint = ProtonTheme.colors.notificationWarning,
                )

                Text(
                    modifier = Modifier.weight(weight = 1f),
                    text = suggestion.text,
                    color = ProtonTheme.colors.textNorm,
                    style = ProtonTheme.typography.body1Regular,
                )

                if (isSuggestionLinkAvailable) {
                    Icon(
                        modifier = Modifier.size(size = 16.dp),
                        painter = painterResource(id = CoreR.drawable.ic_proton_arrow_out_square),
                        contentDescription = null,
                        tint = ProtonTheme.colors.textWeak,
                    )
                }
            }

            if (index != viewState.suggestions.lastIndex) {
                VpnDivider(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
