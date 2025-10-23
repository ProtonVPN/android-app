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

package com.protonvpn.android.redesign.reports.ui.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonTextButton
import com.protonvpn.android.base.ui.SimpleTopAppBar
import com.protonvpn.android.base.ui.TopAppBarBackIcon
import com.protonvpn.android.base.ui.TopAppBarTitle
import com.protonvpn.android.base.ui.indicators.StepsProgressIndicator
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.ScreenNoArg
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.reports.ui.BugReportNav
import com.protonvpn.android.redesign.reports.ui.BugReportViewModel
import me.proton.core.compose.theme.ProtonTheme

object BugReportStepsScreen : ScreenNoArg<BugReportNav>("bugReportSteps") {

    fun SafeNavGraphBuilder<BugReportNav>.bugReportStepsScreen(
        bugReportViewModel: BugReportViewModel,
        bugReportStepsNav: BugReportStepsNav,
        onClose: () -> Unit,
        onUpdateApp: () -> Unit,
        modifier: Modifier = Modifier,
    ) = addToGraph(this) {
        val viewState = bugReportViewModel.viewStateFlow.collectAsStateWithLifecycle().value

        viewState?.let { state ->
            BugReportStepsRoute(
                modifier = modifier,
                viewState = state,
                onClose = onClose,
                onNavigateBack = bugReportStepsNav::navigateUp,
            ) {
                bugReportStepsNav.NavHost(
                    modifier = Modifier.fillMaxSize(),
                    viewState = state,
                    onOpenStore = onUpdateApp,
                    onSelectCategory = bugReportViewModel::onSelectCategory,
                    onSetCurrentStep = bugReportViewModel::onUpdateCurrentStep,
                )
            }
        }
    }
}

@Composable
private fun BugReportStepsRoute(
    viewState: BugReportViewModel.ViewState,
    onClose: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            SimpleTopAppBar(
                modifier = Modifier.fillMaxWidth(),
                navigationIcon = {
                    TopAppBarBackIcon(
                        onClick = {
                            if (viewState.canMoveToPreviousStep) {
                                onNavigateBack()
                            } else {
                                onClose()
                            }
                        },
                    )
                },
                title = {
                    TopAppBarTitle(
                        title = stringResource(id = R.string.settings_report_issue_title),
                        subtitle = viewState.subtitle,
                    )
                },
                actions = {
                    if (viewState.canCancelBugReport) {
                        ProtonTextButton(
                            onClick = onClose,
                        ) {
                            Text(
                                text = stringResource(id = R.string.action_done),
                                style = ProtonTheme.typography.body1Medium,
                            )
                        }
                    }
                }
            )
        }
    ) { innerPaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues = innerPaddingValues)
                .background(color = ProtonTheme.colors.backgroundNorm)
                .padding(horizontal = largeScreenContentPadding()),
        ) {
            StepsProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                currentStep = viewState.currentStep.stepIndex,
                totalSteps = viewState.stepsCount,
            )

            content()
        }
    }
}
