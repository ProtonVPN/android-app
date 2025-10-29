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

import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.ScreenNoArg
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.reports.ui.BugReportViewModel
import com.protonvpn.android.redesign.reports.ui.steps.BugReportStepsNav

object BugReportSuggestionsScreen : ScreenNoArg<BugReportStepsNav>("bugReportStepSuggestions") {

    fun SafeNavGraphBuilder<BugReportStepsNav>.bugReportSuggestionsScreen(
        viewState: BugReportViewModel.ViewState,
        onSetCurrentStep: (BugReportViewModel.BugReportSteps) -> Unit,
        onOpenLink: (String) -> Unit,
    ) = addToGraph(this) {
        BugReportSuggestions(
            viewState = viewState,
            onSetCurrentStep = onSetCurrentStep,
            onSuggestionLinkClick = onOpenLink,
        )
    }

}
