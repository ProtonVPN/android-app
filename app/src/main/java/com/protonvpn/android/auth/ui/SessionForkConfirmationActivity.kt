/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.auth.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.auth.ui.SessionForkConfirmationViewModel.ViewState
import com.protonvpn.android.base.ui.largeScreenContentPadding
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.base.ui.theme.enableEdgeToEdgeVpn
import com.protonvpn.android.bugreport.ui.BugReportActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SessionForkConfirmationActivity : ComponentActivity() {

    private val viewModel by viewModels<SessionForkConfirmationViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val splashScreen = installSplashScreen()
        enableEdgeToEdgeVpn()
        splashScreen.setKeepOnScreenCondition { viewModel.viewState.value == ViewState.Initializing }

        viewModel.start(intent.data)

        setContent {
            VpnTheme {
                val viewState by viewModel.viewState.collectAsStateWithLifecycle()
                if (viewState is ViewState.Finished) {
                    LaunchedEffect(Unit) { finish() }
                }
                SessionForkConfirmation(
                    viewState = viewState,
                    onConfirm = viewModel::confirmFork,
                    onClose = ::finish,
                    onReportBug = ::openBugReport,
                    modifier = Modifier
                        .fillMaxSize()
                        .largeScreenContentPadding()
                )
            }
        }
    }

    private fun openBugReport() {
        startActivity(Intent(this, BugReportActivity::class.java))
    }
}