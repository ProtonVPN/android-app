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

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.base.ui.theme.enableEdgeToEdgeVpn
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.utils.openBrowserLink

@AndroidEntryPoint
class BugReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdgeVpn()

        super.onCreate(savedInstanceState)

        setContent {
            VpnTheme {
                val bugReportViewModel = hiltViewModel<BugReportViewModel>()

                val navController = rememberNavController()

                BugReportNav(selfNav = navController).NavHost(
                    bugReportViewModel = bugReportViewModel,
                    onClose = ::finish,
                    onOpenLink = ::openBrowserLink,
                    onUpdateApp = { update -> bugReportViewModel.onAppUpdateStart(this, update) },
                )
            }
        }
    }

}
