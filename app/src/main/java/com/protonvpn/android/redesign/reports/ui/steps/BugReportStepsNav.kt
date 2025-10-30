package com.protonvpn.android.redesign.reports.ui.steps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.android.redesign.base.ui.nav.BaseNav
import com.protonvpn.android.redesign.reports.ui.BugReportViewModel
import com.protonvpn.android.redesign.reports.ui.steps.form.BugReportFormScreen
import com.protonvpn.android.redesign.reports.ui.steps.form.BugReportFormScreen.bugReportFormScreen
import com.protonvpn.android.redesign.reports.ui.steps.menu.BugReportMenuScreen
import com.protonvpn.android.redesign.reports.ui.steps.menu.BugReportMenuScreen.bugReportMenuScreen
import com.protonvpn.android.redesign.reports.ui.steps.suggestions.BugReportSuggestionsScreen
import com.protonvpn.android.redesign.reports.ui.steps.suggestions.BugReportSuggestionsScreen.bugReportSuggestionsScreen
import com.protonvpn.android.update.AppUpdateInfo

class BugReportStepsNav(
    selfNav: NavHostController,
) : BaseNav<BugReportStepsNav>(selfNav, "bugReportSteps") {

    @Composable
    fun NavHost(
        viewState: BugReportViewModel.ViewState,
        onUpdateApp: (AppUpdateInfo) -> Unit,
        onOpenLink: (String) -> Unit,
        onSelectCategory: (Category) -> Unit,
        onSetCurrentStep: (BugReportViewModel.BugReportSteps) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        SafeNavHost(
            modifier = modifier,
            startScreen = BugReportMenuScreen,
        ) {
            bugReportMenuScreen(
                viewState = viewState,
                onUpdateApp = onUpdateApp,
                onSetCurrentStep = onSetCurrentStep,
                onCategorySelected = { selectedCategory ->
                    onSelectCategory(selectedCategory)

                    if (selectedCategory.suggestions.isEmpty()) {
                        navigateInternal(screen = BugReportFormScreen)
                    } else {
                        navigateInternal(screen = BugReportSuggestionsScreen)
                    }
                },
            )

            bugReportSuggestionsScreen(
                viewState = viewState,
                onSetCurrentStep = onSetCurrentStep,
                onOpenLink = onOpenLink,
            )

            bugReportFormScreen(
                viewState = viewState,
                onSetCurrentStep = onSetCurrentStep,
            )
        }
    }

}

@Composable
fun rememberBugReportStepsNav(
    selfNavController: NavHostController = rememberNavController(),
) = remember(key1 = selfNavController) {
    BugReportStepsNav(
        selfNav = selfNavController,
    )
}