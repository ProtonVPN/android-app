/*
 * Copyright (c) 2021 Proton AG
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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.protonvpn.android.ui.main

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.VpnApiClient
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.auth.AuthFlowStartHelper
import com.protonvpn.android.auth.LOGIN_GUEST_HOLE_ID
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.managed.AutoLoginManager
import com.protonvpn.android.managed.AutoLoginState
import com.protonvpn.android.auth.usecase.HumanVerificationGuestHoleCheck
import com.protonvpn.android.auth.usecase.Logout
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.redesign.reports.IsRedesignedBugReportFeatureFlagEnabled
import com.protonvpn.android.ui.planupgrade.IsInAppUpgradeAllowedUseCase
import com.protonvpn.android.userstorage.DontShowAgainStore
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.proton.core.account.domain.entity.isDisabled
import me.proton.core.account.domain.entity.isReady
import me.proton.core.account.domain.entity.isStepNeeded
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.presentation.observe
import me.proton.core.accountmanager.presentation.onAccountCreateAccountNeeded
import me.proton.core.accountmanager.presentation.onAccountCreateAddressFailed
import me.proton.core.accountmanager.presentation.onAccountCreateAddressNeeded
import me.proton.core.accountmanager.presentation.onAccountDeviceSecretNeeded
import me.proton.core.accountmanager.presentation.onSessionSecondFactorNeeded
import me.proton.core.accountmanager.presentation.onUserAddressKeyCheckFailed
import me.proton.core.accountmanager.presentation.onUserKeyCheckFailed
import me.proton.core.auth.domain.feature.IsCredentialLessEnabled
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.auth.presentation.entity.AddAccountWorkflow
import me.proton.core.auth.presentation.onAddAccountResult
import me.proton.core.plan.domain.usecase.GetDynamicSubscription
import javax.inject.Inject

@HiltViewModel
@SuppressWarnings("LongParameterList")
class AccountViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    private val api: ProtonApiRetroFit,
    private val authOrchestrator: AuthOrchestrator,
    private val accountManager: AccountManager,
    private val currentUser: CurrentUser,
    private val vpnApiClient: VpnApiClient,
    private val guestHole: dagger.Lazy<GuestHole>,
    private val humanVerificationGuestHoleCheck: HumanVerificationGuestHoleCheck,
    private val logoutUseCase: Logout,
    private val vpnStatus: VpnStatusProviderUI,
    private val appFeaturesPrefs: AppFeaturesPrefs,
    private val dontShowAgainStore: DontShowAgainStore,
    private val isCredentialLessEnabled: IsCredentialLessEnabled,
    private val isInAppUpgradeAllowedUseCase: IsInAppUpgradeAllowedUseCase,
    private val getDynamicSubscription: GetDynamicSubscription,
    private val authFlowTriggerHelper: AuthFlowStartHelper,
    private val showRedesignedBugReportFeatureFlagEnabled: IsRedesignedBugReportFeatureFlagEnabled,
    autoLoginManager: AutoLoginManager,
) : ViewModel() {

    sealed class State {
        data object Initial : State()
        data object LoginNeeded : State()
        data object StepNeeded : State()
        data object Ready : State()
        data object Processing : State()

        data object AutoLoginInProgress : State()
        data class AutoLoginError(val e: Throwable, val showRedesignedBugReport: Boolean) : State()
    }

    sealed class OnboardingEvent {
        data object None: OnboardingEvent()
        data object ShowOnboarding: OnboardingEvent()
        data object ShowUpgradeOnboarding: OnboardingEvent()
        data class ShowUpgradeSuccess(val planName: String): OnboardingEvent()
    }

    val eventShowOnboarding = combine(
        appFeaturesPrefs.showOnboardingUserIdFlow.distinctUntilChanged(),
        currentUser.vpnUserFlow.map { it?.userId }.distinctUntilChanged()
    ) { onboardingUserId, primaryUserId ->
        if (primaryUserId != null && primaryUserId.id == onboardingUserId) {
            primaryUserId
        } else {
            null
        }
    }.filterNotNull().map { userId ->
        val paidPlanName = getDynamicSubscription(userId)?.name
        if (paidPlanName != null) {
            OnboardingEvent.ShowUpgradeSuccess(paidPlanName)
        } else if (!isCredentialLessEnabled()) {
            OnboardingEvent.ShowOnboarding
        } else if (isInAppUpgradeAllowedUseCase()) {
            OnboardingEvent.ShowUpgradeOnboarding
        } else {
            OnboardingEvent.None
        }
    }.catch {
        emit(OnboardingEvent.None)
    }

    val eventForceUpdate get() = vpnApiClient.eventForceUpdate
    var onAddAccountClosed: (() -> Unit)? = null

    val state =
        autoLoginManager.state.flatMapLatest { autoLoginState ->
            when (autoLoginState) {
                AutoLoginState.Ongoing -> flowOf(State.AutoLoginInProgress)
                AutoLoginState.PartiallyLoggedIn ->
                    // Display regular UI, it will handle any errors related to /vpn/v2
                    flowOf(State.Ready)
                AutoLoginState.Success -> flowOf(State.Ready)
                is AutoLoginState.Error -> flowOf(
                    State.AutoLoginError(
                        autoLoginState.e,
                        showRedesignedBugReport = showRedesignedBugReportFeatureFlagEnabled()
                    )
                )
                AutoLoginState.Disabled -> {
                    accountManager.getAccounts().map { accounts ->
                        when {
                            accounts.isEmpty() || accounts.all { it.isDisabled() } -> {
                                setupGuestHoleForLoginAndSignup()
                                State.LoginNeeded
                            }

                            accounts.any { it.isReady() } -> State.Ready
                            accounts.any { it.isStepNeeded() } -> State.StepNeeded
                            else -> State.Processing
                        }
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            // Lazily is needed to observe state change, even in the background, to ensure that the
            // subscriber will always get the latest value, even if they follow different lifecycle.
            started = SharingStarted.Lazily,
            initialValue = State.Processing
        )

    fun init(activity: FragmentActivity) {
        authOrchestrator.register(activity)

        with(authOrchestrator) {
            onAddAccountResult { result ->
                if (result == null) {
                    viewModelScope.launch {
                        guestHole.get().releaseNeedGuestHole(LOGIN_GUEST_HOLE_ID)
                        onAddAccountClosed?.invoke()
                    }
                } else if (result.workflow == AddAccountWorkflow.SignUp ||
                    result.workflow == AddAccountWorkflow.CredentialLess
                ) {
                    appFeaturesPrefs.showOnboardingUserId = result.userId
                }
            }
            setOnSignUpResult { result ->
                if (result != null) {
                    appFeaturesPrefs.showOnboardingUserId = result.userId
                }
            }
            accountManager.observe(activity.lifecycle, minActiveState = Lifecycle.State.CREATED)
                .onSessionSecondFactorNeeded { startSecondFactorWorkflow(it) }
                .onAccountCreateAddressNeeded { startChooseAddressWorkflow(it) }
                .onAccountCreateAddressFailed { accountManager.disableAccount(it.userId) }
                .onAccountCreateAccountNeeded { startSignupWorkflow(cancellable = false) }
                .onAccountDeviceSecretNeeded { startDeviceSecretWorkflow(it) }
                .onUserKeyCheckFailed { ProtonLogger.logCustom(LogCategory.USER, "UserKeyCheckFailed") }
                .onUserAddressKeyCheckFailed { ProtonLogger.logCustom(LogCategory.USER,"UserAddressKeyCheckFailed") }

            authFlowTriggerHelper.startAuthEvent
                .onEach { type ->
                    when (type) {
                        AuthFlowStartHelper.Type.SignIn -> startLoginWorkflow()
                        AuthFlowStartHelper.Type.CreateAccount -> startSignupWorkflow()
                    }
                }
                .launchIn(activity.lifecycleScope)
        }
    }

    private fun setupGuestHoleForLoginAndSignup() = with(guestHole.get()) {
        acquireNeedGuestHole(LOGIN_GUEST_HOLE_ID) // Released in VpnAppViewModel.
        humanVerificationGuestHoleCheck(viewModelScope)
    }

    override fun onCleared() {
        viewModelScope.launch(NonCancellable) {
            guestHole.get().releaseNeedGuestHole(LOGIN_GUEST_HOLE_ID)
        }
    }

    suspend fun showDialogOnSignOut() =
        dontShowAgainStore.getChoice(DontShowAgainStore.Type.SignOutWhileConnected) ==
            DontShowAgainStore.Choice.ShowDialog
        && !vpnStatus.isDisabled


    suspend fun addAccount() {
        viewModelScope.launch { api.getAvailableDomains() }
        authOrchestrator.startAddAccountWorkflow(Storage.getString(LAST_USER, null))
    }

    fun signUp() {
        authOrchestrator.startSignupWorkflow()
    }

    fun signIn() {
        authOrchestrator.startLoginWorkflow(Storage.getString(LAST_USER, null))
    }

    fun signOut(notAskAgain: Boolean? = null) = mainScope.launch {
        if (notAskAgain == true) {
            dontShowAgainStore.setChoice(
                DontShowAgainStore.Type.SignOutWhileConnected, DontShowAgainStore.Choice.Positive)
        }
        logoutUseCase()
    }

    fun onOnboardingShown() {
        appFeaturesPrefs.showOnboardingUserId = null
    }

    companion object {
        const val LAST_USER = "LAST_USER"
    }
}
