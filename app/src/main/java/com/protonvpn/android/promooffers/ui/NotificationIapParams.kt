/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.promooffers.ui

import com.protonvpn.android.promooffers.data.ApiNotificationIapAction
import com.protonvpn.android.promooffers.data.ApiNotificationProductDetailsGoogle
import com.protonvpn.android.ui.planupgrade.PlanCycle
import com.protonvpn.android.ui.planupgrade.usecase.LoadPlansConfig

data class NotificationIapParams(
    val loadPlansConfig: LoadPlansConfig,
    val preselectedCycle: PlanCycle?,
    val currency: String? = null,
    val priceCents: Int? = null,
    val showDiscountBadge: Boolean = false,
)

fun ApiNotificationIapAction.toIapParams() = NotificationIapParams(
    loadPlansConfig = LoadPlansConfig.WithOfferTagAndFilter(
        offerTag = offerTag,
        planNames = listOf(planName),
        planCycles = listOf(cycle),
    ),
    preselectedCycle = cycle,
    currency = currency,
    priceCents = priceCents,
    showDiscountBadge = false,
)

fun ApiNotificationProductDetailsGoogle.toIapParams() = NotificationIapParams(
    // Empty offer tag should not match anything and thus eligibility checks will fail.
    loadPlansConfig = LoadPlansConfig.WithOfferTag(offerTag.orEmpty()),
    preselectedCycle = preselectedCycle,
    showDiscountBadge = false,
)

