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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.android.redesign.base.ui.nav.BaseNav
import com.protonvpn.android.redesign.reports.ui.BugReportViewModel
import com.protonvpn.android.tv.reports.steps.form.TvBugReportFormScreen
import com.protonvpn.android.tv.reports.steps.form.TvBugReportFormScreen.tvBugReportFormScreen
import com.protonvpn.android.tv.reports.steps.menu.TvBugReportMenuScreen
import com.protonvpn.android.tv.reports.steps.menu.TvBugReportMenuScreen.tvBugReportMenuScreen
import com.protonvpn.android.tv.reports.steps.suggestions.TvBugReportSuggestionsScreen
import com.protonvpn.android.tv.reports.steps.suggestions.TvBugReportSuggestionsScreen.tvBugReportSuggestionsScreen

class TvBugReportStepsNav(
    selfNav: NavHostController,
) : BaseNav<TvBugReportStepsNav>(selfNav, "tvBugReportSteps") {

    @Composable
    fun NavHost(
        viewState: BugReportViewModel.ViewState,
        onClose: () -> Unit,
        onContactUs: () -> Unit,
        onSelectCategory: (Category) -> Unit,
        onSetCurrentStep: (BugReportViewModel.BugReportSteps) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        SafeNavHost(
            modifier = modifier,
            startScreen = TvBugReportMenuScreen,
        ) {
            tvBugReportMenuScreen(
                viewState = viewState,
                onCategorySelected = { selectedCategory ->
                    onSelectCategory(selectedCategory)

                    if (selectedCategory.suggestions.isEmpty()) {
                        navigateInternal(screen = TvBugReportFormScreen)
                    } else {
                        navigateInternal(screen = TvBugReportSuggestionsScreen)
                    }
                },
                onSetCurrentStep = onSetCurrentStep,
            )

            tvBugReportSuggestionsScreen(
                viewState = viewState,
                onCancel = onClose,
                onContactUs = onContactUs,
                onSetCurrentStep = onSetCurrentStep,
            )

            tvBugReportFormScreen(
                viewState = viewState,
                onSetCurrentStep = onSetCurrentStep,
            )
        }
    }

}

@Composable
fun rememberTvBugReportStepsNav(
    selfNavController: NavHostController = rememberNavController(),
) = remember(key1 = selfNavController) {
    TvBugReportStepsNav(
        selfNav = selfNavController,
    )
}
