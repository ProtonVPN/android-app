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

package com.protonvpn.android.redesign.reports.ui.completion.success

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.ScreenNoArg
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.reports.ui.BugReportNav
import com.protonvpn.android.redesign.reports.ui.completion.BugReportCompletion

object BugReportCompletionSuccessScreen : ScreenNoArg<BugReportNav>("bugReportCompletionSuccess") {

    fun SafeNavGraphBuilder<BugReportNav>.bugReportCompletionSuccessScreen(
        onClose: () -> Unit,
    ) = addToGraph(this) {
        BugReportCompletion(
            modifier = Modifier.fillMaxSize(),
            imageResId = R.drawable.report_success,
            titleText = stringResource(id = R.string.report_sent),
            descriptionText = stringResource(id = R.string.dynamic_report_completion_success_title),
            actionText = stringResource(id = R.string.action_done),
            onActionClick = onClose,
        )
    }
}
