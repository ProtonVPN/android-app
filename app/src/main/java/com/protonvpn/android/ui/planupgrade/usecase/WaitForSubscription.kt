package com.protonvpn.android.ui.planupgrade.usecase

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import me.proton.core.domain.entity.UserId
import me.proton.core.payment.domain.PurchaseManager
import me.proton.core.payment.domain.entity.Purchase
import me.proton.core.payment.domain.entity.PurchaseState
import me.proton.core.payment.domain.onPurchaseState
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.extension.isCredentialLess
import javax.inject.Inject

private const val MAX_AWAIT_DURATION_MS = 1000L * 60

class WaitForSubscription @Inject constructor(
    private val purchaseManager: PurchaseManager,
    private val userManager: UserManager
) {
    /**
     * Wait until a [purchase state][Purchase.purchaseState] for the [planName] is at least [PurchaseState.Subscribed].
     * In other words, wait until the purchase state has changed from
     * [PurchaseState.Pending] or [PurchaseState.Purchased] into any other state.
     * If [userId] is null or a credential-less user, this function returns immediately
     * (it's not possible to subscribe without a user or with a credential-less user).
     */
    suspend operator fun invoke(planName: String, userId: UserId?): Purchase? {
        val user = userId?.let { userManager.getUser(it) }
        if (user == null || user.isCredentialLess()) return null
        return withTimeoutOrNull(MAX_AWAIT_DURATION_MS) {
            purchaseManager.onPurchaseState(
                PurchaseState.Subscribed,
                PurchaseState.Acknowledged,
                PurchaseState.Cancelled,
                PurchaseState.Failed,
                PurchaseState.Deleted,
                planName = planName
            ).firstOrNull()
        }
    }
}
