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
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.auth.VpnUserCheck
import com.protonvpn.android.auth.usecase.HumanVerificationGuestHoleCheck
import com.protonvpn.android.auth.usecase.Logout
import com.protonvpn.android.auth.usecase.VpnLogin.Companion.GUEST_HOLE_ID
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.userstorage.DontShowAgainStore
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
import javax.inject.Inject

@HiltViewModel
@SuppressWarnings("LongParameterList")
class AccountViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    private val api: ProtonApiRetroFit,
    private val authOrchestrator: AuthOrchestrator,
    private val accountManager: AccountManager,
    private val requiredAccountType: AccountType,
    private val vpnApiClient: VpnApiClient,
    private val vpnUserCheck: VpnUserCheck,
    private val guestHole: dagger.Lazy<GuestHole>,
    private val product: Product,
    private val humanVerificationGuestHoleCheck: HumanVerificationGuestHoleCheck,
    private val logoutUseCase: Logout,
    private val vpnStatus: VpnStatusProviderUI,
    private val appFeaturesPrefs: AppFeaturesPrefs,
    private val dontShowAgainStore: DontShowAgainStore
) : ViewModel() {

    sealed class State {
        object Initial : State()
        object LoginNeeded : State()
        object StepNeeded : State()
        object Ready : State()
        object Processing : State()
    }

    val eventShowOnboarding = combine(
        appFeaturesPrefs.showOnboardingUserIdFlow.distinctUntilChanged(),
        accountManager.getPrimaryUserId().distinctUntilChanged()
    ) { onboardingUserId, primaryUserId ->
        primaryUserId != null && primaryUserId.id == onboardingUserId
    }.filter { it }.map { Unit }

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
                } else if (result.workflow == AddAccountWorkflow.SignUp) {
                    appFeaturesPrefs.showOnboardingUserId = result.userId
                }
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

    suspend fun showDialogOnSignOut() =
        dontShowAgainStore.getChoice(DontShowAgainStore.Type.SignOutWhileConnected) ==
            DontShowAgainStore.Choice.ShowDialog
        && !vpnStatus.isDisabled

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
