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

package com.protonvpn.android.bugreport.ui.steps.form

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.base.ui.nav.ScreenNoArg
import com.protonvpn.android.base.ui.nav.addToGraph
import com.protonvpn.android.bugreport.ui.BugReportViewModel
import com.protonvpn.android.bugreport.ui.steps.BugReportStepsNav
import com.protonvpn.android.models.config.bugreport.InputField

object BugReportFormScreen : ScreenNoArg<BugReportStepsNav>("bugReportStepForm") {

    fun SafeNavGraphBuilder<BugReportStepsNav>.bugReportFormScreen(
        viewState: BugReportViewModel.ViewState,
        onSetCurrentStep: (BugReportViewModel.BugReportSteps) -> Unit,
        onFormEmailChanged: (String) -> Unit,
        onFormFieldChanged: (InputField, String) -> Unit,
        onFormSendLogsChanged: (Boolean) -> Unit,
    ) = addToGraph(this) {
        BugReportForm(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            viewState = viewState,
            onSetCurrentStep = onSetCurrentStep,
            onFormEmailChanged = onFormEmailChanged,
            onFormFieldChanged = onFormFieldChanged,
            onFormSendLogsChanged = onFormSendLogsChanged,
        )
    }

}
