/*
 * Copyright (c) 2023 Proton AG
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

package com.protonvpn.android.redesign.main_screen.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.LightAndDarkPreview
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.components.VpnUiDelegateProvider
import com.protonvpn.android.redesign.base.ui.LocalVpnUiDelegate
import com.protonvpn.android.redesign.base.ui.ProtonAlert
import com.protonvpn.android.redesign.countries.ui.collectAsEffect
import com.protonvpn.android.redesign.home_screen.ui.HomeViewModel
import com.protonvpn.android.redesign.main_screen.ui.nav.VpnApp
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.ui.login.AssignVpnConnectionActivity
import com.protonvpn.android.ui.main.AccountViewModel
import com.protonvpn.android.ui.main.MainActivityHelper
import com.protonvpn.android.ui.onboarding.OnboardingActivity
import com.protonvpn.android.ui.onboarding.WhatsNewActivity
import com.protonvpn.android.ui.onboarding.WhatsNewDialogController
import com.protonvpn.android.ui.vpn.VpnUiActivityDelegate
import com.protonvpn.android.ui.vpn.VpnUiActivityDelegateMobile
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.core.compose.component.ProtonCenteredProgress
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : VpnUiDelegateProvider, AppCompatActivity() {

    private val accountViewModel: AccountViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()

    @Inject
    lateinit var whatsNewDialogController: WhatsNewDialogController

    // public for now until there is need to bridge old code, as LocalVpnUiDelegate is not available in non-compose
    val vpnActivityDelegate = VpnUiActivityDelegateMobile(this) {
        retryConnectionAfterPermissions(it)
    }

    private val helper = object : MainActivityHelper(this) {

        override suspend fun onLoginNeeded() {
            accountViewModel.startLogin()
        }

        override suspend fun onReady() {
            // Handled in onCreate()
        }

        override fun onAssignConnectionNeeded() {
            startActivity(Intent(this@MainActivity, AssignVpnConnectionActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val splashScreen = installSplashScreen()
        helper.onCreate(accountViewModel)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            accountViewModel.showOnboarding.collectAsEffect {
                accountViewModel.onOnboardingShown()
                startActivity(Intent(this, OnboardingActivity::class.java))
            }
            VpnTheme {
                val state by accountViewModel.state.collectAsStateWithLifecycle()
                splashScreen.setKeepOnScreenCondition {
                    state == AccountViewModel.State.Processing ||
                        state == AccountViewModel.State.StepNeeded
                }
                when (state) {
                    AccountViewModel.State.Initial,
                    AccountViewModel.State.LoginNeeded -> {}

                    AccountViewModel.State.Processing,
                    AccountViewModel.State.StepNeeded ->
                        ProtonCenteredProgress(Modifier.fillMaxSize())

                    AccountViewModel.State.Ready -> {
                        val showSignOutDialog = rememberSaveable { mutableStateOf(false) }
                        val coreNavigation = CoreNavigation(
                            signOut = {
                                if (accountViewModel.showDialogOnSignOut) {
                                    showSignOutDialog.value = true
                                } else {
                                    accountViewModel.signOut()
                                }
                            }
                        )
                        CompositionLocalProvider(LocalVpnUiDelegate provides this@MainActivity.vpnActivityDelegate) {
                            VpnApp(coreNavigation = coreNavigation)
                        }

                        if (showSignOutDialog.value) {
                            SignOutDialog(
                                hide = { showSignOutDialog.value = false },
                                signOut = accountViewModel::signOut
                            )
                        }
                    }
                }
            }
        }
        whatsNewDialogController.shouldShowDialog()
            .flowWithLifecycle(lifecycle)
            .filterNotNull()
            .onEach { dialogType ->
                WhatsNewActivity.launch(this, dialogType)
                whatsNewDialogController.onDialogShown()
            }
            .launchIn(lifecycleScope)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        helper.onNewIntent(accountViewModel)
    }

    private fun retryConnectionAfterPermissions(connectIntent: AnyConnectIntent) {
        homeViewModel.connect(vpnActivityDelegate, connectIntent)
    }

    override fun getVpnUiDelegate(): VpnUiActivityDelegate = vpnActivityDelegate
}

class CoreNavigation(
    val signOut: () -> Unit
)

@Composable
fun SignOutDialog(hide: () -> Unit, signOut: (notShowAgain: Boolean) -> Unit) {
    ProtonAlert(
        title = stringResource(id = R.string.dialog_sign_out_title),
        text = stringResource(id = R.string.dialog_sign_out_message),
        checkBox = stringResource(id = R.string.dialog_dont_ask_again),
        confirmLabel = stringResource(id = R.string.dialog_sign_out_action),
        onConfirm = { notShowAgain ->
            hide()
            signOut(notShowAgain)
        },
        dismissLabel = stringResource(id = R.string.dialog_action_cancel),
        onDismissButton = { hide() },
        checkBoxInitialValue = false,
        onDismissRequest = hide
    )
}

@Preview
@Composable
fun PreviewSignOutDialog() {
    LightAndDarkPreview {
        SignOutDialog({}, {})
    }
}
