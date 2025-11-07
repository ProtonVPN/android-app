package com.protonvpn.android.tv.reports.completion.success

import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.ScreenNoArg
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.reports.ui.BugReportNav
import com.protonvpn.android.tv.reports.completion.TvBugReportCompletion

object TvBugReportCompletionSuccessScreen : ScreenNoArg<BugReportNav>("tvBugReportCompletionSuccess") {

    fun SafeNavGraphBuilder<BugReportNav>.tvBugReportCompletionSuccessScreen(
        onClose: () -> Unit,
        modifier: Modifier = Modifier.Companion,
    ) = addToGraph(this) {
        TvBugReportCompletion(
            modifier = modifier,
            imageResId = R.drawable.report_success,
            titleText = stringResource(id = R.string.report_sent),
            descriptionText = stringResource(id = R.string.dynamic_report_completion_success_title),
            actionText = stringResource(id = R.string.action_done),
            onActionClick = onClose,
        )
    }

}