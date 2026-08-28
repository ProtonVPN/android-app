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

import com.protonvpn.android.promooffers.data.ApiNotification
import com.protonvpn.android.promooffers.data.ApiNotificationManager
import com.protonvpn.android.promooffers.ui.NotificationIapParams
import com.protonvpn.android.ui.planupgrade.IapConstants
import com.protonvpn.android.ui.planupgrade.IsInAppUpgradeAllowedUseCase
import com.protonvpn.android.ui.planupgrade.PaymentCycle
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

data class UpgradeDialogLoadPlansConfig(
    val planSelection: LoadPlansConfig,
    val preselectedCycle: PaymentCycle?,
    val buttonLabelOverride: String?,
    val showDiscountBadge: Boolean,
    val notificationReference: String?,
)

@Reusable
class GetUpgradeDialogPlansConfig(
    private val isInAppUpgradeAllowed: suspend () -> Boolean,
    private val activeNotificationsFlow: Flow<List<ApiNotification>>,
    private val awaitNotificationsUpdate: suspend () -> Unit,
) {

    @Inject constructor(
        isInAppUpgradeAllowedUseCase: IsInAppUpgradeAllowedUseCase,
        notificationsManager: ApiNotificationManager
    ) : this(
        isInAppUpgradeAllowed = isInAppUpgradeAllowedUseCase::invoke,
        activeNotificationsFlow = notificationsManager.activeListFlow,
        awaitNotificationsUpdate = notificationsManager::awaitUpdateFinish,
    )

    suspend fun forBuiltinUpsell(
        notificationType: Int,
        supportedPlanNames: List<String>
    ): UpgradeDialogLoadPlansConfig? {
        if (!isInAppUpgradeAllowed()) return null

        val notification = withTimeoutOrNull(5.seconds) {
            // Note: this relies on ApiNotificationManager starting the first update
            // before onboarding view model calls this function.
            // ApiNotificationManager starts update on login event which is before the onboarding
            // screen can load. But it's fragile.
            awaitNotificationsUpdate()
            activeNotificationsFlow
                .first()
                .firstOrNull { it.type == notificationType }
        }
        val panel = notification?.offer?.panel
        val iapParams = panel?.iapProductDetails?.google
        return if (iapParams != null && iapParams.offerTag != null) {
            UpgradeDialogLoadPlansConfig(
                planSelection = LoadPlansConfig.WithOptionalDiscount(
                    planNames = supportedPlanNames,
                    paymentCycles = IapConstants.DEFAULT_PAYMENT_CYCLES,
                    discountOfferTags = listOf(iapParams.offerTag, IapConstants.INTRO_PRICE_TAG),
                ),
                preselectedCycle = iapParams.preselectedCycle,
                showDiscountBadge = false,
                buttonLabelOverride = panel.button?.text,
                notificationReference = notification.reference,
            )
        } else {
            UpgradeDialogLoadPlansConfig(
                planSelection = LoadPlansConfig.WithOptionalDiscount(
                    planNames = supportedPlanNames,
                    paymentCycles = IapConstants.DEFAULT_PAYMENT_CYCLES,
                    discountOfferTags = listOf(IapConstants.INTRO_PRICE_TAG),
                ),
                preselectedCycle = PaymentCycle.Year(1),
                showDiscountBadge = true,
                buttonLabelOverride = null,
                notificationReference = null,
            )
        }
    }

    suspend fun forNotification(
        iapParams: NotificationIapParams,
        buttonLabelOverride: String?,
        notificationReference: String?,
    ): UpgradeDialogLoadPlansConfig? {
        if (!isInAppUpgradeAllowed()) return null

        return UpgradeDialogLoadPlansConfig(
            planSelection = iapParams.loadPlansConfig,
            preselectedCycle = iapParams.preselectedCycle,
            buttonLabelOverride = buttonLabelOverride,
            showDiscountBadge = false,
            notificationReference = notificationReference,
        )
    }
}
