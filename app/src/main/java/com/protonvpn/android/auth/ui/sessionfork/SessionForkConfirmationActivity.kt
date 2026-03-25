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

package com.protonvpn.android.auth.ui.sessionfork

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.auth.ui.AccountViewModel
import com.protonvpn.android.auth.ui.TermsAndConditionsActivity
import com.protonvpn.android.auth.ui.sessionfork.SessionForkConfirmationViewModel.ViewState
import com.protonvpn.android.auth.usecase.IsQrCodeTvLoginFeatureFlagEnabled
import com.protonvpn.android.auth.usecase.Logout
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.base.ui.theme.enableEdgeToEdgeVpn
import com.protonvpn.android.bugreport.ui.BugReportActivity
import com.protonvpn.android.redesign.app.ui.VpnApp
import com.protonvpn.android.ui.main.MainActivityHelper
import com.protonvpn.android.ui.planupgrade.UpgradeOnboardingDialogActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.proton.core.compose.component.ProtonCenteredProgress
import javax.inject.Inject

@AndroidEntryPoint
class SessionForkConfirmationActivity : FragmentActivity() {

    @Inject lateinit var logoutUseCase: Logout
    @Inject lateinit var mainScope: CoroutineScope
    @Inject lateinit var isQrCodeTvLoginFeatureFlagEnabled: IsQrCodeTvLoginFeatureFlagEnabled

    private val viewModel by viewModels<SessionForkConfirmationViewModel>()
    private val accountViewModel by viewModels<SessionForkConfirmationAccountViewModel>()

    private val activityHelper = object : MainActivityHelper(this) {
        override suspend fun onLoginNeeded() {
            viewModel.onSignInRequired()
        }

        override suspend fun onReady() {
            // UI will display the appropriate state.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val splashScreen = installSplashScreen()
        enableEdgeToEdgeVpn()

        // Keep this state outside the composable to use it in splashScreen.setKeepOnScreenCondition
        var accountState by mutableStateOf<AccountViewModel.State>(AccountViewModel.State.Processing)
        accountViewModel.state
            .flowWithLifecycle(lifecycle)
            .onEach { accountState = it }
            .launchIn(lifecycleScope)

        splashScreen.setKeepOnScreenCondition {
            viewModel.viewState.value == SessionForkConfirmationViewModel.ViewState.Initial &&
                accountState in arrayOf(AccountViewModel.State.Processing, AccountViewModel.State.Initial)
        }

        viewModel.eventLaunchUpgrade
            .receiveAsFlow()
            .flowWithLifecycle(lifecycle)
            .onEach {
                UpgradeOnboardingDialogActivity.launch(this) }
            .launchIn(lifecycleScope)
        viewModel.initialize(intent.data)

        activityHelper.onCreate(accountViewModel)

        lifecycleScope.launch {
            // No regular user should be scanning QR codes when the FF is disabled.
            if (!isQrCodeTvLoginFeatureFlagEnabled()) finish()
        }

        setContent {
            VpnTheme {
                val viewState by viewModel.viewState.collectAsStateWithLifecycle()
                when (accountState) {
                    AccountViewModel.State.Ready -> {
                        VpnApp(
                            onSignOut = ::logout,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            SessionForkConfirmation(
                                viewState = viewState,
                                onConfirm = viewModel::confirmFork,
                                onClose = ::finish,
                                onStartActivity = { startActivity(it) },
                                onReportBug = ::openBugReport,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    AccountViewModel.State.Initial -> {}
                    AccountViewModel.State.LoginNeeded -> {
                        val termsAndConditionsIntent =
                            Intent(this, TermsAndConditionsActivity::class.java)
                        SessionForkSignIn(
                            onSignUp = { accountViewModel.signUp() },
                            onSignIn = { accountViewModel.signIn() },
                            onTermsAndConditions = { startActivity(termsAndConditionsIntent) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    AccountViewModel.State.Processing,
                    AccountViewModel.State.StepNeeded -> {
                        ProtonCenteredProgress(Modifier.fillMaxSize())
                    }

                    is AccountViewModel.State.AutoLoginError,
                    AccountViewModel.State.AutoLoginInProgress -> {
                        SessionForkConfirmation(
                            viewState = ViewState.Fork.Error.Fatal,
                            onConfirm = viewModel::confirmFork,
                            onClose = ::finish,
                            onStartActivity = { startActivity(it) },
                            onReportBug = ::openBugReport,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        activityHelper.onNewIntent(accountViewModel)
    }

    private fun openBugReport() {
        startActivity(Intent(this, BugReportActivity::class.java))
    }

    private fun logout() {
        mainScope.launch {
            logoutUseCase()
        }
    }
}