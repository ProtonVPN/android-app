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

package com.protonvpn.android.tv.reports.steps.suggestions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.redesign.reports.ui.BugReportViewModel
import com.protonvpn.android.tv.buttons.TvSolidButton
import com.protonvpn.android.tv.buttons.TvProtonTextButton
import com.protonvpn.android.tv.reports.steps.TvBugReportStepHeader
import com.protonvpn.android.tv.settings.ProtonTvFocusableSurface
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun TvBugReportSuggestions(
    viewState: BugReportViewModel.ViewState,
    onCancelClick: () -> Unit,
    onContactUsClick: () -> Unit,
    onSetCurrentStep: (BugReportViewModel.BugReportSteps) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(key1 = Unit) {
        onSetCurrentStep(BugReportViewModel.BugReportSteps.Suggestions)

        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier,
    ) {
        TvLazyColumn(
            modifier = Modifier
                .focusRequester(focusRequester)
                .weight(weight = 1f, fill = true),
        ) {
            item {
                TvBugReportStepHeader(
                    modifier = Modifier.padding(all = 16.dp),
                    title = stringResource(id = R.string.dynamic_report_tips_title),
                    subtitle = stringResource(id = R.string.dynamic_report_tips_description),
                )
            }

            items(viewState.suggestions) { suggestion ->
                ProtonTvFocusableSurface(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(all = 16.dp),
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
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            TvSolidButton(
                text = stringResource(id = R.string.dynamic_report_contact_us),
                onClick = onContactUsClick,
            )

            TvProtonTextButton(
                text = stringResource(id = R.string.cancel),
                onClick = onCancelClick,
            )
        }
    }
}
