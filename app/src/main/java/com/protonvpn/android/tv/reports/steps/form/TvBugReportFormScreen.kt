package com.protonvpn.android.tv.reports.steps.form

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.protonvpn.android.models.config.bugreport.InputField
import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.ScreenNoArg
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.reports.ui.BugReportViewModel
import com.protonvpn.android.tv.reports.steps.TvBugReportStepsNav

object TvBugReportFormScreen : ScreenNoArg<TvBugReportStepsNav>("tvBugReportStepForm") {

    fun SafeNavGraphBuilder<TvBugReportStepsNav>.tvBugReportFormScreen(
        viewState: BugReportViewModel.ViewState,
        onSetCurrentStep: (BugReportViewModel.BugReportSteps) -> Unit,
        onFormEmailChanged: (String) -> Unit,
        onFormFieldChanged: (InputField, String) -> Unit,
        onFormSendLogsChanged: (Boolean) -> Unit,
        onSubmitReport: () -> Unit,
    ) = addToGraph(this) {
        TvBugReportForm(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            viewState = viewState,
            onSetCurrentStep = onSetCurrentStep,

            onFormEmailChanged = onFormEmailChanged,
            onFormFieldChanged = onFormFieldChanged,
            onFormSendLogsChanged = onFormSendLogsChanged,
            onSubmitReportClick = onSubmitReport,
        )
    }

}
