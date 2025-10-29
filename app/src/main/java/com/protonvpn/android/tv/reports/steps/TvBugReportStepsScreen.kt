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

package com.protonvpn.android.tv.reports.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.indicators.StepsProgressIndicator
import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.ScreenNoArg
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.reports.ui.BugReportNav
import com.protonvpn.android.redesign.reports.ui.BugReportViewModel
import com.protonvpn.android.tv.reports.steps.form.TvBugReportFormScreen
import com.protonvpn.android.tv.ui.TvUiConstants
import me.proton.core.compose.theme.ProtonTheme

object TvBugReportStepsScreen : ScreenNoArg<BugReportNav>("tvBugReportSteps") {

    fun SafeNavGraphBuilder<BugReportNav>.tvBugReportStepsScreen(
        bugReportViewModel: BugReportViewModel,
        tvBugReportStepsNav: TvBugReportStepsNav,
        onClose: () -> Unit,
        modifier: Modifier = Modifier,
    ) = addToGraph(this) {
        val viewState = bugReportViewModel.viewStateFlow.collectAsStateWithLifecycle().value

        viewState?.let { state ->
            TvBugReportStepsRoute(
                modifier = modifier,
                viewState = state,
            ) {
                tvBugReportStepsNav.NavHost(
                    modifier = Modifier.fillMaxSize(),
                    viewState = viewState,
                    onClose = onClose,
                    onContactUs = {
                        tvBugReportStepsNav.navigateInternal(screen = TvBugReportFormScreen)
                    },
                    onSelectCategory = bugReportViewModel::onSelectCategory,
                    onSetCurrentStep = bugReportViewModel::onUpdateCurrentStep,
                )
            }
        }
    }
}

@Composable
private fun TvBugReportStepsRoute(
    viewState: BugReportViewModel.ViewState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = TvUiConstants.SingleColumnWidth)
                .padding(vertical = TvUiConstants.ScreenPaddingVerticalSmall),
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(space = 4.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.settings_report_issue_title),
                    style = ProtonTheme.typography.hero,
                )

                Text(
                    text = viewState.selectedCategory?.label.orEmpty(),
                    color = ProtonTheme.colors.textWeak,
                    style = ProtonTheme.typography.body2Regular,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                StepsProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    currentStep = viewState.currentStep.stepIndex,
                    totalSteps = viewState.stepsCount,
                )

                content()
            }
        }
    }
}
