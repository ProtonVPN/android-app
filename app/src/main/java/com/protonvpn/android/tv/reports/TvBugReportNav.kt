package com.protonvpn.android.tv.reports

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.navOptions
import com.protonvpn.android.redesign.base.ui.nav.BaseNav
import com.protonvpn.android.redesign.reports.ui.BugReportNav
import com.protonvpn.android.redesign.reports.ui.BugReportViewModel
import com.protonvpn.android.tv.reports.completion.error.TvBugReportCompletionErrorScreen
import com.protonvpn.android.tv.reports.completion.error.TvBugReportCompletionErrorScreen.tvBugReportCompletionErrorScreen
import com.protonvpn.android.tv.reports.completion.success.TvBugReportCompletionSuccessScreen
import com.protonvpn.android.tv.reports.completion.success.TvBugReportCompletionSuccessScreen.tvBugReportCompletionSuccessScreen
import com.protonvpn.android.tv.reports.steps.TvBugReportStepsScreen
import com.protonvpn.android.tv.reports.steps.TvBugReportStepsScreen.tvBugReportStepsScreen
import com.protonvpn.android.tv.reports.steps.rememberTvBugReportStepsNav

class TvBugReportNav(selfNav: NavHostController) : BaseNav<BugReportNav>(selfNav, "tvBugReport") {

    @Composable
    fun NavHost(
        bugReportViewModel: BugReportViewModel,
        onClose: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val tvBugReportStepsNav = rememberTvBugReportStepsNav()

        SafeNavHost(
            modifier = modifier,
            startScreen = TvBugReportStepsScreen,
        ) {
            tvBugReportStepsScreen(
                modifier = Modifier.fillMaxSize(),
                bugReportViewModel = bugReportViewModel,
                tvBugReportStepsNav = tvBugReportStepsNav,
                onClose = onClose,
                onReportSubmitError = { networkError ->
                    navigateInternal(
                        screen = TvBugReportCompletionErrorScreen,
                        arg = networkError,
                    )
                },
                onReportSubmitSuccess = {
                    navigateInternal(
                        screen = TvBugReportCompletionSuccessScreen,
                        navOptions = navOptions {
                            popUpTo(TvBugReportStepsScreen.route) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        },
                    )
                },
            )

            tvBugReportCompletionSuccessScreen(
                modifier = Modifier.fillMaxSize(),
                onClose = onClose,
            )

            tvBugReportCompletionErrorScreen(
                modifier = Modifier.fillMaxSize(),
                onClose = onClose,
                onNavigateBack = ::navigateUp,
            )
        }
    }

}
