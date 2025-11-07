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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonTextButton
import com.protonvpn.android.base.ui.SimpleTopAppBar
import com.protonvpn.android.base.ui.TopAppBarBackIcon
import com.protonvpn.android.base.ui.TopAppBarTitle
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.indicators.StepsProgressIndicator
import com.protonvpn.android.redesign.base.ui.collectAsEffect
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.ScreenNoArg
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.reports.ui.BugReportNav
import com.protonvpn.android.redesign.reports.ui.BugReportViewModel
import com.protonvpn.android.redesign.reports.ui.steps.form.BugReportFormScreen
import com.protonvpn.android.update.AppUpdateInfo
import kotlinx.coroutines.flow.receiveAsFlow
import me.proton.core.compose.theme.ProtonTheme

object BugReportStepsScreen : ScreenNoArg<BugReportNav>("bugReportSteps") {

    fun SafeNavGraphBuilder<BugReportNav>.bugReportStepsScreen(
        bugReportViewModel: BugReportViewModel,
        bugReportStepsNav: BugReportStepsNav,
        onClose: () -> Unit,
        onOpenLink: (String) -> Unit,
        onUpdateApp: (AppUpdateInfo) -> Unit,
        onReportSubmitError: (BugReportViewModel.BugReportNetworkError) -> Unit,
        onReportSubmitSuccess: () -> Unit,
        modifier: Modifier = Modifier,
    ) = addToGraph(this) {
        val viewState = bugReportViewModel.viewStateFlow.collectAsStateWithLifecycle().value

        bugReportViewModel.eventChannelReceiver.receiveAsFlow().collectAsEffect { event ->
            when (event) {
                is BugReportViewModel.Event.OnBugReportSubmitError -> {
                    onReportSubmitError(event.networkError)
                }

                BugReportViewModel.Event.OnBugReportSubmitSuccess -> {
                    onReportSubmitSuccess()
                }
            }
        }

        viewState?.let { state ->
            BugReportStepsRoute(
                modifier = modifier.padding(horizontal = largeScreenContentPadding()),
                viewState = state,
                onClose = onClose,
                onNavigateBack = bugReportStepsNav::navigateUp,
                onContactUsClick = {
                    bugReportStepsNav.navigateInternal(screen = BugReportFormScreen)
                },
                onSubmitReportClick = bugReportViewModel::onSubmitReport,
            ) {
                bugReportStepsNav.NavHost(
                    modifier = Modifier.fillMaxSize(),
                    viewState = state,
                    onUpdateApp = onUpdateApp,
                    onOpenLink = onOpenLink,
                    onSelectCategory = bugReportViewModel::onSelectCategory,
                    onSetCurrentStep = bugReportViewModel::onUpdateCurrentStep,
                    onFormEmailChanged = bugReportViewModel::onFormEmailChanged,
                    onFormFieldChanged = bugReportViewModel::onFormFieldChanged,
                    onFormSendLogsChanged = bugReportViewModel::onFormSendLogsChanged,
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
    onContactUsClick: () -> Unit,
    onSubmitReportClick: () -> Unit,
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
        },
        bottomBar = {
            BugReportStepBottomBar(
                modifier = Modifier.fillMaxWidth(),
                step = viewState.currentStep,
                isLoading = viewState.isLoading,
                onContactUsClick = onContactUsClick,
                onSubmitReportClick = onSubmitReportClick,
            )
        },
    ) { innerPaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues = innerPaddingValues)
                .background(color = ProtonTheme.colors.backgroundNorm),
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

@Composable
private fun BugReportStepBottomBar(
    step: BugReportViewModel.BugReportSteps,
    isLoading: Boolean,
    onContactUsClick: () -> Unit,
    onSubmitReportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (step) {
        BugReportViewModel.BugReportSteps.Menu -> Unit
        BugReportViewModel.BugReportSteps.Suggestions -> {
            BottomAppBar(
                modifier = modifier.padding(all = 16.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(space = 16.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.dynamic_report_suggestion_didnt_work),
                        color = ProtonTheme.colors.textWeak,
                        style = ProtonTheme.typography.captionRegular,
                    )

                    VpnSolidButton(
                        text = stringResource(id = R.string.dynamic_report_contact_us),
                        onClick = onContactUsClick,
                    )
                }
            }
        }

        BugReportViewModel.BugReportSteps.Form -> {
            val keyboardController = LocalSoftwareKeyboardController.current

            BottomAppBar(
                modifier = modifier
                    .padding(horizontal = 16.dp)
                    .imePadding(),
            ) {
                VpnSolidButton(
                    text = stringResource(id = R.string.send_report),
                    isLoading = isLoading,
                    onClick = {
                        keyboardController?.hide()

                        onSubmitReportClick()
                    },
                )
            }
        }
    }
}
