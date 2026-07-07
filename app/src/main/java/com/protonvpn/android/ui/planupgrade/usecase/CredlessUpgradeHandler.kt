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

package com.protonvpn.android.ui.planupgrade.usecase

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.UserPlanManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.android.payment.purchase.model.SessionState
import me.proton.android.payment.purchase.usecase.ObserveSessionState
import me.proton.android.payment.purchase.usecase.ResolveUnredeemedPurchases
import me.proton.core.accountmanager.domain.AccountWorkflowHandler
import me.proton.core.user.domain.extension.isCredentialLess
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredlessUpgradeHandler @Inject constructor(
    private val mainScope: CoroutineScope,
    private val observePaymentSessionState: ObserveSessionState,
    private val currentUser: CurrentUser,
    private val userPlanManager: UserPlanManager,
    private val accountWorkflowHandler: dagger.Lazy<AccountWorkflowHandler>,
    private val resolveUnredeemedPurchases: dagger.Lazy<ResolveUnredeemedPurchases>,
) {

    fun start() {
        currentUser.eventVpnLogin
            .onEach {
                val isCredentialless = currentUser.user()?.isCredentialLess() == true
                if (!isCredentialless) {
                    ProtonLogger.logCustom(
                        LogCategory.IN_APP_PURCHASE,
                        "Checking for unredeemed purchase."
                    )
                    resolveUnredeemedPurchases.get().invoke()
                }
            }
            .launchIn(mainScope)

        observePaymentSessionState()
            .onEach { sessionState ->
                when (sessionState) {
                    SessionState.Reconciling.Terminal.PendingAccountCreation -> {
                        val user = currentUser.user()
                        if (user?.isCredentialLess() == true) {
                            ProtonLogger.logCustom(
                                LogCategory.IN_APP_PURCHASE,
                                "Purchase ready to reconcile, credentialless user needs to sign-in"
                            )
                            accountWorkflowHandler.get().handleCreateAccountNeeded(user.userId)
                        }
                    }

                    is SessionState.Reconciling.Terminal.Success ->
                        userPlanManager.refreshVpnInfo()

                    else -> Unit
                }
            }
            .launchIn(mainScope)
    }
}
