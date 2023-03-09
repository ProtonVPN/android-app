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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.VpnApiClient
import com.protonvpn.android.auth.VpnUserCheck
import com.protonvpn.android.auth.usecase.HumanVerificationGuestHoleCheck
import com.protonvpn.android.auth.usecase.OnSessionClosed
import com.protonvpn.android.auth.usecase.VpnLogin.Companion.GUEST_HOLE_ID
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.ui.onboarding.OnboardingPreferences
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.CertificateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.account.domain.entity.AccountType
import me.proton.core.account.domain.entity.isDisabled
import me.proton.core.account.domain.entity.isReady
import me.proton.core.account.domain.entity.isStepNeeded
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.presentation.observe
import me.proton.core.accountmanager.presentation.onAccountCreateAddressFailed
import me.proton.core.accountmanager.presentation.onAccountCreateAddressNeeded
import me.proton.core.accountmanager.presentation.onSessionSecondFactorNeeded
import me.proton.core.accountmanager.presentation.onUserAddressKeyCheckFailed
import me.proton.core.accountmanager.presentation.onUserKeyCheckFailed
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.auth.presentation.entity.AddAccountWorkflow
import me.proton.core.auth.presentation.onAddAccountResult
import me.proton.core.domain.entity.Product
import me.proton.core.network.domain.NetworkManager
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    val api: ProtonApiRetroFit,
    val authOrchestrator: AuthOrchestrator,
    val accountManager: AccountManager,
    val requiredAccountType: AccountType,
    val vpnApiClient: VpnApiClient,
    val onSessionClosed: OnSessionClosed,
    val certificateRepository: CertificateRepository,
    val vpnUserCheck: VpnUserCheck,
    val guestHole: dagger.Lazy<GuestHole>,
    val product: Product,
    val humanVerificationGuestHoleCheck: HumanVerificationGuestHoleCheck,
    val networkManager: NetworkManager
) : ViewModel() {

    sealed class State {
        object Initial : State()
        object LoginNeeded : State()
        object StepNeeded : State()
        object Ready : State()
        object Processing : State()
    }

    val eventForceUpdate get() = vpnApiClient.eventForceUpdate
    var onAddAccountClosed: (() -> Unit)? = null
    var onAssignConnectionHandler: (() -> Unit)? = null

    val state = accountManager.getAccounts()
        .map { accounts ->
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
        .stateIn(
            scope = viewModelScope,
            // Lazily is needed to observe state change, even in the background, to ensure that the
            // subscriber will always get the latest value, even if they follow different lifecycle.
            started = SharingStarted.Lazily,
            initialValue = State.Processing
        )

    fun init(activity: FragmentActivity) {
        authOrchestrator.register(activity)

        vpnUserCheck.assignConnectionNeeded.onEach {
            onAssignConnectionHandler?.invoke()
        }.launchIn(activity.lifecycleScope)

        with(authOrchestrator) {
            onAddAccountResult { result ->
                if (result == null) {
                    viewModelScope.launch {
                        guestHole.get().releaseNeedGuestHole(GUEST_HOLE_ID)
                        onAddAccountClosed?.invoke()
                    }
                } else if (result.workflow == AddAccountWorkflow.SignUp)
                    Storage.saveString(OnboardingPreferences.ONBOARDING_USER_ID, result.userId)
            }
            accountManager.observe(activity.lifecycle, minActiveState = Lifecycle.State.CREATED)
                .onSessionSecondFactorNeeded { startSecondFactorWorkflow(it) }
                .onAccountCreateAddressNeeded { startChooseAddressWorkflow(it) }
                .onAccountCreateAddressFailed { accountManager.disableAccount(it.userId) }
                .onUserKeyCheckFailed { ProtonLogger.logCustom(LogCategory.USER, "UserKeyCheckFailed") }
                .onUserAddressKeyCheckFailed { ProtonLogger.logCustom(LogCategory.USER,"UserAddressKeyCheckFailed") }
        }
    }

    private fun setupGuestHoleForLoginAndSignup() = with(guestHole.get()) {
        acquireNeedGuestHole(GUEST_HOLE_ID)
        humanVerificationGuestHoleCheck(viewModelScope)
    }

    override fun onCleared() {
        viewModelScope.launch(NonCancellable) {
            guestHole.get().releaseNeedGuestHole(GUEST_HOLE_ID)
        }
    }

    suspend fun startLogin() {
        viewModelScope.launch { api.getAvailableDomains() }
        authOrchestrator.startAddAccountWorkflow(
            requiredAccountType = requiredAccountType,
            creatableAccountType = requiredAccountType,
            product = product,
            loginUsername = Storage.getString(LAST_USER, null)
        )
    }

    companion object {
        const val LAST_USER = "LAST_USER"
    }
}
