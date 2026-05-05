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

package com.protonvpn.android.ui.planupgrade

import android.app.Activity
import com.protonvpn.android.ui.planupgrade.usecase.CycleInfo
import com.protonvpn.android.ui.planupgrade.usecase.GiapOfferInfo
import com.protonvpn.android.utils.Quadruple
import dagger.Reusable
import me.proton.core.domain.entity.AppStore
import me.proton.core.payment.domain.repository.GoogleBillingRepository
import me.proton.core.paymentiap.domain.LogTag
import me.proton.core.paymentiap.domain.entity.unwrap
import me.proton.core.plan.domain.entity.DynamicPlan
import me.proton.core.util.kotlin.CoreLogger
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class LoadGoogleOffers @Inject constructor(
    private val billingRepositoryProvider: Provider<GoogleBillingRepository<Activity>>,
) {
    suspend operator fun invoke(dynamicPlans: List<DynamicPlan>): List<GiapOfferInfo> {
        return billingRepositoryProvider.get().use { repository ->
            if (repository.canQueryProductDetails()) {
                val products = dynamicPlans
                    .flatMap { it.toVendorProducts(AppStore.GooglePlay) }
                val detailsList = if (products.isNotEmpty()) {
                    repository.getProductsDetails(products.map { it.first })?.map { it.unwrap() }
                } else {
                    emptyList()
                }
                if (detailsList.isNullOrEmpty())
                    return emptyList()

                val data = products
                    .mapNotNull { (productId, planCycle, dynamicPlan) ->
                        val details = detailsList.find { it.productId == productId.id }
                        details?.let {
                            Quadruple(productId, planCycle, dynamicPlan, details)
                        }
                    }
                data.flatMap { (productId, planCycle, dynamicPlan, productDetails) ->
                    val offers = productDetails.subscriptionOfferDetails ?: emptyList()
                    offers.mapNotNull { offer ->
                        // Note that this assumes at most 2 pricing phases, this code will need to
                        // be extended if we ever want to support more complex offers.
                        val firstPhase = offer.pricingPhases.pricingPhaseList.getOrNull(0)
                        val secondPhase = offer.pricingPhases.pricingPhaseList.getOrNull(1)

                        firstPhase?.let {
                            val currency = firstPhase.priceCurrencyCode
                            val firstPhasePriceCents = firstPhase.priceAmountMicros.microsToCents()
                            val secondPhasePriceCents = secondPhase?.priceAmountMicros?.microsToCents()
                            val cycleInfo = CycleInfo(
                                cycle = planCycle,
                                productId = productId.id,
                                offerToken = offer.offerToken,
                                currentPriceCents = firstPhasePriceCents,
                                defaultPriceCents = secondPhasePriceCents ?: firstPhasePriceCents
                            )
                            GiapOfferInfo(
                                dynamicPlan = dynamicPlan,
                                currency = currency,
                                cycle = cycleInfo,
                                offerTags = offer.offerTags,
                            )
                        }
                    }
                }
            } else {
                CoreLogger.w(LogTag.GIAP_INFO, "Cannot query for product details.")
                emptyList()
            }
        }
    }
}

private fun Long.microsToCents(): Int = (this / GOOGLE_TO_PROTON_PRICE_DIVIDER).toInt()

// Google Billing library returns price in micros where 1,000,000 micro-units equal one unit of the currency
// but our prices are expressed in cents, this is why we divide by 10000
private const val GOOGLE_TO_PROTON_PRICE_DIVIDER = 10000