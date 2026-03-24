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

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.VpnApiClient
import com.protonvpn.android.auth.AuthFlowStartHelper
import com.protonvpn.android.auth.LOGIN_GUEST_HOLE_ID
import com.protonvpn.android.auth.usecase.HumanVerificationGuestHoleCheck
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.managed.AutoLoginManager
import com.protonvpn.android.managed.AutoLoginState
import com.protonvpn.android.utils.Storage
import dagger.Lazy
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
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
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.auth.presentation.entity.AddAccountWorkflow
import me.proton.core.auth.presentation.onAddAccountResult

@SuppressWarnings("LongParameterList")
abstract class AccountViewModel(
    private val api: ProtonApiRetroFit,
    private val authOrchestrator: AuthOrchestrator,
    private val accountManager: AccountManager,
    private val vpnApiClient: VpnApiClient,
    private val guestHole: Lazy<GuestHole>,
    private val humanVerificationGuestHoleCheck: HumanVerificationGuestHoleCheck,
    private val authFlowTriggerHelper: AuthFlowStartHelper,
    autoLoginManager: AutoLoginManager,
) : ViewModel() {

    sealed class State {
        data object Initial : State()
        data object LoginNeeded : State()
        data object StepNeeded : State()
        data object Ready : State()
        data object Processing : State()

        data object AutoLoginInProgress : State()
        data class AutoLoginError(val e: Throwable) : State()
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
            started = SharingStarted.Companion.Lazily,
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
                    onAccountAddSuccess(result.userId)
                }
            }
            setOnSignUpResult { result ->
                if (result != null) {
                    onAccountAddSuccess(result.userId) }
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

    protected open fun onAccountAddSuccess(userId: String) {
    }

    companion object {
        const val LAST_USER = "LAST_USER"
    }
}