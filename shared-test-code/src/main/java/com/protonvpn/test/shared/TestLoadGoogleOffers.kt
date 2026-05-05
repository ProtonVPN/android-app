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

package com.protonvpn.test.shared

import com.protonvpn.android.ui.planupgrade.LoadGoogleOffers
import com.protonvpn.android.ui.planupgrade.toVendorProducts
import com.protonvpn.android.ui.planupgrade.usecase.CycleInfo
import com.protonvpn.android.ui.planupgrade.usecase.GiapOfferInfo
import me.proton.core.domain.entity.AppStore
import me.proton.core.plan.domain.entity.DynamicPlan
import me.proton.core.plan.presentation.entity.PlanCycle

/**
 * Describes one billing row returned by [LoadGoogleOffers]-style APIs.
 *
 * @param pricingPhasesCents Phase 1 = current price (cents); phase 2 = renew/default (cents).
 *        Use a single element when current and renew are the same (no intro).
 */
data class TestGiapOffer(
    val cycle: PlanCycle,
    val productId: String,
    val token: String,
    val tags: List<String>,
    val pricingPhasesCents: List<Int>,
    val currency: String,
) {
    init {
        require(pricingPhasesCents.isNotEmpty()) { "pricingPhasesCents must not be empty" }
    }
}

class TestLoadGoogleOffers(
    var offers: List<TestGiapOffer> = emptyList(),
) {
    var wasCalled: Boolean = false
        private set

    fun resetWasCalled() {
        wasCalled = false
    }

    suspend operator fun invoke(dynamicPlans: List<DynamicPlan>): List<GiapOfferInfo> {
        wasCalled = true

        return dynamicPlans.flatMap { it.toVendorProducts(AppStore.GooglePlay) }
            .flatMap { (productId, cycle, dynamicPlan) ->
                offers
                    .filter { offer -> offer.productId == productId.id && offer.cycle == cycle }
                    .map { offer ->
                        val currentPrice = offer.pricingPhasesCents.first()
                        val defaultPrice = offer.pricingPhasesCents.getOrNull(1) ?: currentPrice

                        GiapOfferInfo(
                            dynamicPlan = dynamicPlan,
                            currency = offer.currency,
                            cycle = CycleInfo(
                                cycle = offer.cycle,
                                productId = offer.productId,
                                offerToken = offer.token,
                                currentPriceCents = currentPrice,
                                defaultPriceCents = defaultPrice,
                            ),
                            offerTags = offer.tags,
                        )
                    }
            }
    }
}
