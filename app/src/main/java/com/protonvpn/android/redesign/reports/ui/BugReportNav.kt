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

package com.protonvpn.android.redesign.reports.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.navOptions
import com.protonvpn.android.redesign.base.ui.nav.BaseNav
import com.protonvpn.android.redesign.reports.ui.completion.error.BugReportCompletionErrorScreen
import com.protonvpn.android.redesign.reports.ui.completion.error.BugReportCompletionErrorScreen.bugReportCompletionErrorScreen
import com.protonvpn.android.redesign.reports.ui.completion.success.BugReportCompletionSuccessScreen
import com.protonvpn.android.redesign.reports.ui.completion.success.BugReportCompletionSuccessScreen.bugReportCompletionSuccessScreen
import com.protonvpn.android.redesign.reports.ui.steps.BugReportStepsScreen
import com.protonvpn.android.redesign.reports.ui.steps.BugReportStepsScreen.bugReportStepsScreen
import com.protonvpn.android.redesign.reports.ui.steps.rememberBugReportStepsNav
import com.protonvpn.android.update.AppUpdateInfo

class BugReportNav(selfNav: NavHostController) : BaseNav<BugReportNav>(selfNav, "bugReport") {

    @Composable
    fun NavHost(
        bugReportViewModel: BugReportViewModel,
        onClose: () -> Unit,
        onOpenLink: (String) -> Unit,
        onUpdateApp: (AppUpdateInfo) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val bugReportStepsNav = rememberBugReportStepsNav()

        SafeNavHost(
            modifier = modifier,
            startScreen = BugReportStepsScreen,
        ) {
            bugReportStepsScreen(
                bugReportViewModel = bugReportViewModel,
                bugReportStepsNav = bugReportStepsNav,
                onClose = onClose,
                onOpenLink = onOpenLink,
                onUpdateApp = onUpdateApp,
                onReportSubmitError = { networkError ->
                    navigateInternal(
                        screen = BugReportCompletionErrorScreen,
                        arg = networkError,
                    )
                },
                onReportSubmitSuccess = {
                    navigateInternal(
                        screen = BugReportCompletionSuccessScreen,
                        navOptions = navOptions {
                            popUpTo(BugReportStepsScreen.route) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        },
                    )
                },
            )

            bugReportCompletionSuccessScreen(
                onClose = onClose,
            )

            bugReportCompletionErrorScreen(
                onClose = onClose,
                onNavigateBack = ::navigateUp,
            )
        }
    }

}
