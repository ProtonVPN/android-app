/*
 * Copyright (c) 2021 Proton Technologies AG
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

package com.protonvpn.android.ui.main

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.VpnApiClient
import com.protonvpn.android.auth.VpnUserCheck
import com.protonvpn.android.auth.usecase.OnSessionClosed
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.ui.onboarding.OnboardingPreferences
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.CertificateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.account.domain.entity.AccountType
import me.proton.core.account.domain.entity.isDisabled
import me.proton.core.account.domain.entity.isReady
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.presentation.observe
import me.proton.core.accountmanager.presentation.onAccountCreateAddressFailed
import me.proton.core.accountmanager.presentation.onAccountCreateAddressNeeded
import me.proton.core.accountmanager.presentation.onAccountReady
import me.proton.core.accountmanager.presentation.onSessionForceLogout
import me.proton.core.accountmanager.presentation.onSessionSecondFactorNeeded
import me.proton.core.accountmanager.presentation.onUserAddressKeyCheckFailed
import me.proton.core.accountmanager.presentation.onUserKeyCheckFailed
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.auth.presentation.entity.AddAccountWorkflow
import me.proton.core.auth.presentation.onAddAccountResult
import me.proton.core.auth.presentation.onSecondFactorResult
import me.proton.core.domain.entity.Product
import me.proton.core.humanverification.domain.HumanVerificationManager
import me.proton.core.humanverification.presentation.HumanVerificationOrchestrator
import me.proton.core.humanverification.presentation.observe
import me.proton.core.humanverification.presentation.onHumanVerificationNeeded
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    val api: ProtonApiRetroFit,
    val authOrchestrator: AuthOrchestrator,
    val humanVerificationOrchestrator: HumanVerificationOrchestrator,
    val humanVerificationManager: HumanVerificationManager,
    val accountManager: AccountManager,
    val accountType: AccountType,
    val vpnApiClient: VpnApiClient,
    val onSessionClosed: OnSessionClosed,
    val certificateRepository: CertificateRepository,
    val vpnUserCheck: VpnUserCheck,
    val guestHole: dagger.Lazy<GuestHole>,
    val product: Product
) : ViewModel() {

    sealed class State {
        object Initial : State()
        object LoginNeeded : State()
        object Ready : State()
        object Processing : State()
    }

    val eventForceUpdate get() = vpnApiClient.eventForceUpdate
    var onAddAccountClosed: (() -> Unit)? = null
    var onAssignConnectionHandler: (() -> Unit)? = null

    private val _state = MutableStateFlow<State>(State.Initial)
    val state = _state.asStateFlow()

    fun init(activity: FragmentActivity) {
        authOrchestrator.register(activity)
        humanVerificationOrchestrator.register(activity)

        accountManager.getAccounts()
            .flowWithLifecycle(activity.lifecycle)
            .onEach { accounts ->
                when {
                    accounts.isEmpty() || accounts.all { it.isDisabled() || it.state == AccountState.Removed } ->
                        _state.emit(State.LoginNeeded)
                    accounts.any { it.isReady() } ->
                        _state.emit(State.Ready)
                    else ->
                        _state.emit(State.Processing)
                }
            }.launchIn(activity.lifecycleScope)

        vpnUserCheck.assignConnectionNeeded.onEach {
            onAssignConnectionHandler?.invoke()
        }.launchIn(activity.lifecycleScope)

        with(authOrchestrator) {
            onAddAccountResult { result ->
                if (result == null)
                    onAddAccountClosed?.invoke()
                else if (result.workflow == AddAccountWorkflow.SignUp)
                    Storage.saveString(OnboardingPreferences.ONBOARDING_USER_ID, result.userId)
            }
            accountManager.observe(activity.lifecycle, minActiveState = Lifecycle.State.CREATED)
                .onSessionSecondFactorNeeded { startSecondFactorWorkflow(it) }
                .onAccountCreateAddressNeeded { startChooseAddressWorkflow(it) }
                .onAccountCreateAddressFailed { accountManager.disableAccount(it.userId) }
                .onSessionForceLogout { onSessionClosed(it) }
                .onUserKeyCheckFailed { ProtonLogger.logCustom(LogCategory.USER, "UserKeyCheckFailed") }
                .onUserAddressKeyCheckFailed { ProtonLogger.logCustom(LogCategory.USER,"UserAddressKeyCheckFailed") }
        }

        with(humanVerificationOrchestrator) {
            humanVerificationManager.observe(activity.lifecycle, minActiveState = Lifecycle.State.RESUMED)
                .onHumanVerificationNeeded { startHumanVerificationWorkflow(it) }
        }
    }

    fun onSecondFactorClosed(block: () -> Unit) {
        authOrchestrator.onSecondFactorResult { if (it == null) block() }
    }

    suspend fun startLogin() {
        viewModelScope.launch { api.getAvailableDomains() }
        authOrchestrator.startAddAccountWorkflow(
            accountType, product, loginUsername = Storage.getString(LAST_USER, null)
        )
    }

    companion object {
        const val LAST_USER = "LAST_USER"
    }
}
