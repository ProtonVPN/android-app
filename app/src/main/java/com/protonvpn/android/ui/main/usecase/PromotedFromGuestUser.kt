package com.protonvpn.android.ui.main.usecase

import kotlinx.coroutines.flow.firstOrNull
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.extension.isCredentialLess
import javax.inject.Inject

/**
 * Detects a case, when a guest (credential-less) user successfully creates a regular account.
 */
class PromotedFromGuestUser @Inject constructor(
    private val accountManager: AccountManager,
    private val userManager: UserManager
) {
    suspend operator fun invoke(): Boolean {
        val previousUserId = accountManager.getPreviousPrimaryUserId() ?: return false
        val previousAccount = accountManager.getAccount(previousUserId).firstOrNull()
        val previousUser = userManager.getUser(previousUserId)
        return previousUser.isCredentialLess() && previousAccount?.state == AccountState.CreateAccountSuccess
    }
}
