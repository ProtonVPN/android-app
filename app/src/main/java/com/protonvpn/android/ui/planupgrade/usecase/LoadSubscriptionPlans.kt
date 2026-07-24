/*
 * Copyright (c) 2023. Proton AG
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

import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.ui.planupgrade.PlanCycle
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.getValue
import com.protonvpn.android.utils.ifOrNull
import dagger.Reusable
import kotlinx.serialization.Serializable
import me.proton.android.payment.common.model.ProductId
import me.proton.android.payment.product.model.BillingCycle
import me.proton.android.payment.product.model.Offer
import me.proton.android.payment.product.model.OfferTag
import me.proton.android.payment.product.model.Product
import me.proton.android.payment.product.usecase.GetProducts
import javax.inject.Inject

data class CycleInfo(
    val cycle: PlanCycle,
    val productId: String,
    val offerToken: String,
    val offerTags: List<OfferTag>,
    val currentPriceCents: Int,
    val defaultPriceCents: Int,
)

data class SubscriptionPlanInfo(
    val name: String,
    val displayName: String,
    val currency: String,
    val cycles: List<CycleInfo>,
)

@Serializable
sealed interface LoadPlansConfig {

    @Serializable
    data class WithOptionalDiscount(
        val planNames: List<String>,
        val planCycles: List<PlanCycle>,
        val discountOfferTag: String,
    ) : LoadPlansConfig


    // Needed for client generated intro offer promos - hopefully to be removed soonish.
    @Serializable
    data class WithOfferTagAndFilter(
        val planNames: List<String>,
        val planCycles: List<PlanCycle>,
        val offerTag: String,
    ) : LoadPlansConfig

    @Serializable
    data class WithOfferTag(
        val offerTag: String,
    ) : LoadPlansConfig
}

@Reusable
class LoadSubscriptionPlans @Inject constructor(
    getProductsLazy: dagger.Lazy<GetProducts>,
) {
    private val getProducts by getProductsLazy

    private data class OfferInfo(
        val offer: Offer,
        val baseOffer: Offer.NonDiscounted, // May be the same as offer.
    )

    suspend operator fun invoke(
        selection: LoadPlansConfig,
    ): List<SubscriptionPlanInfo> {
        val offerSelector = when(selection) {
            is LoadPlansConfig.WithOfferTag -> { product: Product ->
                product.offers
                    .find { it.tags.contains(selection.offerTag) }
                    ?.let { offerWithTag ->
                        val baseOffer = offerWithTag as? Offer.NonDiscounted
                            ?: product.offers.filterIsInstance<Offer.NonDiscounted>().firstOrNull()
                            ?: return@let null
                        OfferInfo(offerWithTag, baseOffer)
                    }
            }

            is LoadPlansConfig.WithOfferTagAndFilter -> { product: Product ->
                ifOrNull(product.planId in selection.planNames) {
                    product.offers
                        .find { it.tags.contains(selection.offerTag) }
                        ?.let { offerWithTag ->
                            val baseOffer = offerWithTag as? Offer.NonDiscounted
                                ?: product.offers.filterIsInstance<Offer.NonDiscounted>().firstOrNull()
                            val planCycle = offerWithTag.pricingPeriods.firstOrNull()?.cycle?.toPlanCycle()
                            if (planCycle == null || baseOffer == null) // Should not happen.
                                return@ifOrNull null
                            ifOrNull(planCycle in selection.planCycles) {
                                OfferInfo(offerWithTag, baseOffer)
                            }
                        }
                }
            }

            is LoadPlansConfig.WithOptionalDiscount -> { product: Product ->
                ifOrNull(product.planId in selection.planNames) {
                    val baseOffer = product.offers.filterIsInstance<Offer.NonDiscounted>().firstOrNull()
                    val discountedOffer = product.offers
                        .filterIsInstance<Offer.Discounted>()
                        .find { it.tags.contains(selection.discountOfferTag) }
                    val currentOffer = discountedOffer ?: baseOffer
                    val planCycle = currentOffer?.pricingPeriods?.firstOrNull()?.cycle?.toPlanCycle()
                    if (planCycle == null || baseOffer == null) // Should not happen.
                        return@ifOrNull null
                    ifOrNull(planCycle in selection.planCycles) {
                        OfferInfo(discountedOffer ?: baseOffer, baseOffer)
                    }
                }
            }
        }

        return loadPlans(offerSelector)
    }

    private suspend fun loadPlans(
        offerSelector: (Product) -> OfferInfo?
    ): List<SubscriptionPlanInfo> {
        val subscriptionPlans = getProducts().fold(
            onSuccess = { products ->
                products
                    .groupBy { it.planId }
                    .mapNotNull { (planName, products) ->
                        var currency: String? = null
                        val cycles = products.mapNotNull { product ->
                            DebugUtils.debugAssert { product.offers.count { it is Offer.NonDiscounted } == 1 }
                            val offer = offerSelector(product)
                            offer?.let {
                                DebugUtils.debugAssert { offer.baseOffer.pricingPeriods.isNotEmpty() }
                                currency = offer.baseOffer.pricingPeriods.first().price.currency
                                createCycleInfo(product.id, offer)
                            }
                        }

                        if (cycles.isNotEmpty() && currency != null) {
                            SubscriptionPlanInfo(
                                name = requireNotNull(planName),
                                displayName = products.first().title,
                                currency = currency,
                                cycles = cycles,
                            )
                        } else {
                            null
                        }
                    }
            },
            onFailure = { e ->
                throw e
            }
        )
        val firstPlanCurrency = subscriptionPlans.firstOrNull()?.currency
        val planWithDifferentCurrency = subscriptionPlans.find { it.currency != firstPlanCurrency }
        if (planWithDifferentCurrency != null) {
            throw IllegalArgumentException(
                "Conflicting currencies: $firstPlanCurrency vs ${planWithDifferentCurrency}."
            )
        }
        return subscriptionPlans
    }

    private fun createCycleInfo(productId: ProductId, offerInfo: OfferInfo): CycleInfo {
        val offer = offerInfo.offer
        val purchasePeriod = offer.pricingPeriods.first()
        val renewPeriod = offerInfo.baseOffer.pricingPeriods.first()
        val planCycle = purchasePeriod.cycle.toPlanCycle()
        logDebug("Product: $productId $planCycle, purchase offer: ${offer.tags} ${purchasePeriod}, renew offer: $renewPeriod")
        logDebug("Purchase offer phases: ${offer.pricingPeriods}")
        return CycleInfo(
            planCycle,
            productId,
            offer.token,
            offer.tags,
            (purchasePeriod.price.amount / SDK_AMOUNT_TO_CENTS_PRICE_DIVIDER).toInt(),
            (renewPeriod.price.amount / SDK_AMOUNT_TO_CENTS_PRICE_DIVIDER).toInt()
        )
    }

    private fun BillingCycle.toPlanCycle(): PlanCycle = when(this) {
        is BillingCycle.Month if count == 1 -> PlanCycle.MONTHLY
        is BillingCycle.Year if count == 1 -> PlanCycle.YEARLY
        is BillingCycle.Year if count == 2 -> PlanCycle.TWO_YEARS
        else -> PlanCycle.OTHER
    }

    private fun logDebug(message: String) {
        ProtonLogger.logCustom(LogLevel.DEBUG, LogCategory.IN_APP_PURCHASE, message)
    }

    private fun logWarning(message: String) {
        ProtonLogger.logCustom(LogLevel.WARN, LogCategory.IN_APP_PURCHASE, message)
    }

    companion object {
        private const val SDK_AMOUNT_TO_CENTS_PRICE_DIVIDER = 10_000
    }
}
