/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.redesign.app.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.components.VpnUiDelegateProvider
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UiConnect
import com.protonvpn.android.managed.ui.AutoLoginErrorView
import com.protonvpn.android.managed.ui.AutoLoginView
import com.protonvpn.android.redesign.base.ui.LocalVpnUiDelegate
import com.protonvpn.android.redesign.base.ui.ProtonAlert
import com.protonvpn.android.redesign.reports.ui.BugReportActivity
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.tv.main.TvMainActivity
import com.protonvpn.android.ui.deeplinks.DeepLinkHandler
import com.protonvpn.android.ui.drawer.LogActivity
import com.protonvpn.android.ui.drawer.bugreport.DynamicReportActivity
import com.protonvpn.android.ui.main.AccountViewModel
import com.protonvpn.android.ui.main.MainActivityHelper
import com.protonvpn.android.ui.onboarding.OnboardingActivity
import com.protonvpn.android.ui.onboarding.WhatsNewActivity
import com.protonvpn.android.ui.onboarding.WhatsNewDialogController
import com.protonvpn.android.ui.planupgrade.ShowUpgradeSuccess
import com.protonvpn.android.ui.planupgrade.UpgradeOnboardingDialogActivity
import com.protonvpn.android.ui.vpn.VpnUiActivityDelegate
import com.protonvpn.android.ui.vpn.VpnUiActivityDelegateMobile
import com.protonvpn.android.update.UpdatePromptForStaleVersion
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.widget.WidgetActionHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.compose.component.ProtonCenteredProgress
import me.proton.core.notification.presentation.deeplink.HandleDeeplinkIntent
import javax.inject.Inject

private const val GLANCE_ACTION_SCHEME = "glance-action"

@AndroidEntryPoint
class MainActivity : VpnUiDelegateProvider, AppCompatActivity() {

    private val accountViewModel: AccountViewModel by viewModels()
    private val activityViewModel: MainActivityViewModel by viewModels()
    private val settingsChangeViewModel: SettingsChangeViewModel by viewModels()
    private lateinit var currentConfiguration: Configuration

    @Inject
    lateinit var showUpgradeSuccess: ShowUpgradeSuccess

    @Inject
    lateinit var promptUpdate: UpdatePromptForStaleVersion

    @Inject
    lateinit var whatsNewDialogController: WhatsNewDialogController
    @Inject
    lateinit var isTv: IsTvCheck
    @Inject
    lateinit var deepLinkHandler: DeepLinkHandler
    @Inject
    lateinit var handleCoreDeepLink: HandleDeeplinkIntent
    @Inject
    lateinit var widgetActionHandler: WidgetActionHandler

    // public for now until there is need to bridge old code, as LocalVpnUiDelegate is not available in non-compose
    val vpnActivityDelegate = VpnUiActivityDelegateMobile(this) {
        retryConnectionAfterPermissions(it)
    }

    private val helper = object : MainActivityHelper(this) {

        override suspend fun onLoginNeeded() {
            accountViewModel.addAccount()
        }

        override suspend fun onReady() {
            // Handled in onCreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isTv()) {
            startActivity(Intent(this, TvMainActivity::class.java))
            finish()
            return
        }

        currentConfiguration = resources.configuration
        val splashScreen = installSplashScreen()
        helper.onCreate(accountViewModel)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestedOrientation = if (resources.getBoolean(R.bool.isTablet) || isTv())
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        accountViewModel.eventShowOnboarding
            .flowWithLifecycle(lifecycle)
            .onEach {
                accountViewModel.onOnboardingShown()
                when (it) {
                    is AccountViewModel.OnboardingEvent.None -> Unit
                    is AccountViewModel.OnboardingEvent.ShowOnboarding ->
                        startActivity(Intent(this, OnboardingActivity::class.java))

                    is AccountViewModel.OnboardingEvent.ShowUpgradeOnboarding ->
                        UpgradeOnboardingDialogActivity.launch(this)

                    is AccountViewModel.OnboardingEvent.ShowUpgradeSuccess ->
                        showUpgradeSuccess.showPlanUpgradeSuccess(
                            this,
                            it.planName,
                            refreshVpnInfo = false
                        )
                }
            }
            .launchIn(lifecycleScope)

        // Keep this state outside the composable to use it in splashScreen.setKeepOnScreenCondition
        var accountState by mutableStateOf<AccountViewModel.State>(AccountViewModel.State.Processing)
        accountViewModel.state
            .flowWithLifecycle(lifecycle)
            .onEach { accountState = it }
            .launchIn(lifecycleScope)

        splashScreen.setKeepOnScreenCondition {
            accountState == AccountViewModel.State.Processing ||
                accountState == AccountViewModel.State.StepNeeded ||
                accountState == AccountViewModel.State.Ready && !activityViewModel.isMinimalStateReady
        }
        setContent {
            val coroutineScope = rememberCoroutineScope()
            VpnTheme {
                val isMinimalStateReady by activityViewModel.isMinimalStateReadyFlow.collectAsStateWithLifecycle()
                when (val state = accountState) {
                    AccountViewModel.State.Initial,
                    AccountViewModel.State.LoginNeeded -> {}
                    AccountViewModel.State.Processing,
                    AccountViewModel.State.StepNeeded ->
                        ProtonCenteredProgress(Modifier.fillMaxSize())

                    AccountViewModel.State.AutoLoginInProgress ->
                        AutoLoginView()
                    is AccountViewModel.State.AutoLoginError -> {
                        val context = LocalContext.current
                        AutoLoginErrorView(
                            state.e.message ?: stringResource(R.string.something_went_wrong),
                            onRetry = activityViewModel::retryAutoLogin,
                            onReportIssue = {
                                if (state.showRedesignedBugReport) {
                                    context.startActivity(Intent(context, BugReportActivity::class.java))
                                } else {
                                    context.startActivity(Intent(context, DynamicReportActivity::class.java))
                                }
                            },
                            onShowLog = {
                                context.startActivity(Intent(context, LogActivity::class.java))
                            }
                        )
                    }

                    AccountViewModel.State.Ready -> {
                        val showSignOutDialog = rememberSaveable { mutableStateOf(false) }
                        val coreNavigation = CoreNavigation(
                            onSignUp = { accountViewModel.signUp() },
                            onSignIn = { accountViewModel.signIn() },
                            onSignOut = {
                                coroutineScope.launch {
                                    if (accountViewModel.showDialogOnSignOut()) {
                                        showSignOutDialog.value = true
                                    } else {
                                        accountViewModel.signOut()
                                    }
                                }
                            }
                        )
                        if (isMinimalStateReady) {
                            // Show the app UI only when it's ready, otherwise accessibility services will focus
                            // incorrectly on partially set up UI (even when the splash screen is covering it).
                            CompositionLocalProvider(
                                LocalVpnUiDelegate provides this@MainActivity.vpnActivityDelegate
                            ) {
                                VpnApp(coreNavigation, settingsChangeViewModel)
                            }
                        }

                        if (showSignOutDialog.value) {
                            SignOutDialog(
                                hide = { showSignOutDialog.value = false },
                                signOut = accountViewModel::signOut
                            )
                        }
                        val showReconnectDialogType =
                            settingsChangeViewModel.showReconnectDialogFlow.collectAsStateWithLifecycle().value
                        if (showReconnectDialogType != null) {
                            ReconnectDialog(
                                onOk = { dontShowAgain ->
                                    settingsChangeViewModel.dismissReconnectDialog(dontShowAgain, showReconnectDialogType)
                                },
                                onReconnect = { dontShowAgain ->
                                    settingsChangeViewModel.onReconnectClicked(
                                        this@MainActivity.vpnActivityDelegate,
                                        dontShowAgain,
                                        showReconnectDialogType
                                    )
                                })
                        }
                    }
                }
            }
        }
        whatsNewDialogController.shouldShowDialog()
            .flowWithLifecycle(lifecycle)
            .onEach { show ->
                if (show) {
                    WhatsNewActivity.launch(this)
                    whatsNewDialogController.onDialogShown()
                }
            }
            .launchIn(lifecycleScope)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val appUpdate = promptUpdate.getUpdatePrompt()
                if (appUpdate != null) {
                    promptUpdate.launchUpdateFlow(this@MainActivity, appUpdate)
                }
            }
        }
        processIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        helper.onNewIntent(accountViewModel)
        processIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val diff = newConfig.diff(currentConfiguration)
        if (diff and Configuration.UI_MODE_NIGHT_MASK.inv() == 0) {
            // Night mode handled by Compose UI.
            currentConfiguration = newConfig
        } else {
            recreate()
        }
    }

    fun processIntent(intent: Intent) {
        processDeepLink(intent)
        handleCoreDeepLink(intent)
        if (intent.data?.scheme == GLANCE_ACTION_SCHEME) {
            lifecycleScope.launch {
                widgetActionHandler.onIntent(vpnActivityDelegate, intent)
            }
        }
    }

    private fun retryConnectionAfterPermissions(connectIntent: AnyConnectIntent) {
        ProtonLogger.log(UiConnect, "VPN permission retry")
        // ConnectionCard is the most likely trigger, although not always correct.
        activityViewModel.connect(vpnActivityDelegate, connectIntent, ConnectTrigger.ConnectionCard)
    }

    override fun getVpnUiDelegate(): VpnUiActivityDelegate = vpnActivityDelegate

    private fun processDeepLink(intent: Intent) {
        val intentUri = intent.data
        if (intent.action == Intent.ACTION_VIEW && intentUri != null) {
            deepLinkHandler.processDeepLink(intentUri)
        }
    }
}

class CoreNavigation(
    val onSignUp: () -> Unit,
    val onSignIn: () -> Unit,
    val onSignOut: () -> Unit
)

@Composable
private fun SignOutDialog(hide: () -> Unit, signOut: (notShowAgain: Boolean) -> Unit) {
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

@Composable
private fun ReconnectDialog(
    onOk: (notShowAgain: Boolean) -> Unit,
    onReconnect: (notShowAgain: Boolean) -> Unit
) {
    ProtonAlert(
        title = null,
        text = stringResource(id = R.string.settings_dialog_reconnect),
        checkBox = stringResource(id = R.string.dialogDontShowAgain),
        confirmLabel = stringResource(id = R.string.reconnect_now),
        onConfirm = { notShowAgain ->
            onReconnect(notShowAgain)
        },
        dismissLabel = stringResource(id = R.string.ok),
        onDismissButton = { notShowAgain ->
            onOk(notShowAgain)
        },
        checkBoxInitialValue = false,
        onDismissRequest = { onOk(false) }
    )
}

@ProtonVpnPreview
@Composable
fun PreviewSignOutDialog() {
    ProtonVpnPreview {
        SignOutDialog({}, {})
    }
}
