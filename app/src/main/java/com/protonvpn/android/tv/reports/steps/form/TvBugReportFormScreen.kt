package com.protonvpn.android.tv.reports.steps.form

import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.ScreenNoArg
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.reports.ui.BugReportViewModel
import com.protonvpn.android.tv.reports.steps.TvBugReportStepsNav

object TvBugReportFormScreen : ScreenNoArg<TvBugReportStepsNav>("tvBugReportStepForm") {

    fun SafeNavGraphBuilder<TvBugReportStepsNav>.tvBugReportFormScreen(
        viewState: BugReportViewModel.ViewState,
        onSetCurrentStep: (BugReportViewModel.BugReportSteps) -> Unit,
    ) = addToGraph(this) {
        TvBugReportForm(
            viewState = viewState,
            onSetCurrentStep = onSetCurrentStep,
        )
    }

}
