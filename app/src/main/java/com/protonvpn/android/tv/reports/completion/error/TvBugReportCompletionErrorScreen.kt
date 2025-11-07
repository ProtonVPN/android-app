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

package com.protonvpn.android.tv.reports.completion.error

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.Screen
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.reports.ui.BugReportNav
import com.protonvpn.android.redesign.reports.ui.BugReportViewModel
import com.protonvpn.android.tv.reports.completion.TvBugReportCompletion

object TvBugReportCompletionErrorScreen : Screen<BugReportViewModel.BugReportNetworkError, BugReportNav>("tvBugReportCompletionError") {

    fun SafeNavGraphBuilder<BugReportNav>.tvBugReportCompletionErrorScreen(
        onClose: () -> Unit,
        onNavigateBack: () -> Unit,
        modifier: Modifier = Modifier,
    ) = addToGraph(this) { navBackStackEntry ->
        BackHandler(enabled = true) {
            onClose()
        }

        TvBugReportCompletionErrorRoute(
            modifier = modifier,
            networkError = getArgs(entry = navBackStackEntry),
            onTryAgainClick = onNavigateBack,
        )
    }

}

@Composable
private fun TvBugReportCompletionErrorRoute(
    networkError: BugReportViewModel.BugReportNetworkError,
    onTryAgainClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvBugReportCompletion(
        modifier = modifier,
        imageResId = R.drawable.report_error,
        titleText = stringResource(id = R.string.something_went_wrong),
        descriptionText = stringResource(id = networkError.resId),
        actionText = stringResource(id = R.string.try_again),
        onActionClick = onTryAgainClick,
    )
}
